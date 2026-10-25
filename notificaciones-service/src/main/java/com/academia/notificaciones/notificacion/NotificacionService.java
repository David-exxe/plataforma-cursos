package com.academia.notificaciones.notificacion;

import com.academia.notificaciones.whatsapp.WhatsAppClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 1) Se GUARDA la notificacion (PENDIENTE) -> si WhatsApp falla, no se pierde.
 * 2) Se intenta enviar. Si falla: reintento con espera creciente 1, 2, 4, 8 min... hasta max-intentos -> FALLIDO.
 */
@Service
public class NotificacionService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionService.class);

    private final NotificacionRepository repo;
    private final WhatsAppClient whatsapp;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final boolean usarPlantillas;
    private final int maxIntentos;

    public NotificacionService(NotificacionRepository repo, WhatsAppClient whatsapp, ObjectMapper mapper,
                               PlatformTransactionManager tm,
                               @Value("${whatsapp.usar-plantillas}") boolean usarPlantillas,
                               @Value("${whatsapp.max-intentos}") int maxIntentos) {
        this.repo = repo;
        this.whatsapp = whatsapp;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(tm);
        this.usarPlantillas = usarPlantillas;
        this.maxIntentos = maxIntentos;
    }

    /** Devuelve el id creado, o null si el evento ya se habia procesado (duplicado). */
    public Long registrar(UUID eventoId, Mensajes.Mensaje m) {
        if (m.telefono() == null || m.telefono().isBlank()) {
            log.warn("Evento {} sin telefono, no se envia WhatsApp", eventoId);
            return null;
        }
        try {
            return tx.execute(s -> {
                if (eventoId != null && repo.existsByEventoIdAndTipo(eventoId, m.tipo())) return null;
                Notificacion n = new Notificacion();
                n.setEventoId(eventoId);
                n.setTipo(m.tipo());
                n.setTelefono(m.telefono());
                n.setMensaje(m.texto());
                n.setPlantilla(m.plantilla());
                n.setParametros(json(m.parametros()));
                return repo.save(n).getId();
            });
        } catch (DataIntegrityViolationException duplicado) {
            return null; // otro hilo ya la registro (unique evento_id + tipo)
        }
    }

    public void intentarEnvio(Long id) {
        Notificacion n = tx.execute(s -> repo.findById(id).orElse(null));
        if (n == null || !"PENDIENTE".equals(n.getEstado())) return;
        try {
            String waId = (usarPlantillas && n.getPlantilla() != null)
                    ? whatsapp.enviarPlantilla(n.getTelefono(), n.getPlantilla(), lista(n.getParametros()), n.getMensaje())
                    : whatsapp.enviarTexto(n.getTelefono(), n.getMensaje());
            tx.executeWithoutResult(s -> repo.findById(id).ifPresent(x -> {
                x.setEstado("ENVIADO");
                x.setWaMessageId(waId);
                x.setEnviadoEn(Instant.now());
                x.setIntentos(x.getIntentos() + 1);
                x.setError(null);
            }));
        } catch (Exception e) {
            tx.executeWithoutResult(s -> repo.findById(id).ifPresent(x -> {
                int intentos = x.getIntentos() + 1;
                x.setIntentos(intentos);
                x.setError(corto(e.getMessage()));
                if (intentos >= maxIntentos) {
                    x.setEstado("FALLIDO");
                    log.error("WhatsApp {} FALLIDO tras {} intentos: {}", id, intentos, e.getMessage());
                } else {
                    x.setProximoIntento(Instant.now().plus((long) Math.pow(2, intentos - 1), ChronoUnit.MINUTES));
                    log.warn("WhatsApp {} fallo (intento {}), se reintentara: {}", id, intentos, e.getMessage());
                }
            }));
        }
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void reintentarPendientes() {
        repo.pendientes(Instant.now()).forEach(this::intentarEnvio);
    }

    /** Estados que reporta Meta por webhook: sent, delivered, read, failed. */
    public void actualizarEstado(String waMessageId, String status, String error) {
        tx.executeWithoutResult(s -> repo.findFirstByWaMessageId(waMessageId).ifPresent(n -> {
            switch (status) {
                case "delivered" -> { if (!"LEIDO".equals(n.getEstado())) n.setEstado("ENTREGADO"); }
                case "read" -> n.setEstado("LEIDO");
                case "failed" -> {
                    // Ej: 131047 "fuera de la ventana de 24 h" -> requiere plantilla aprobada
                    n.setEstado("FALLIDO");
                    n.setError(corto(error));
                }
                default -> { }
            }
        }));
    }

    /** Reintento manual desde el panel admin. */
    public void reprogramar(Long id) {
        tx.executeWithoutResult(s -> repo.findById(id).ifPresent(n -> {
            n.setEstado("PENDIENTE");
            n.setIntentos(0);
            n.setProximoIntento(Instant.now());
        }));
        intentarEnvio(id);
    }

    private String json(List<String> l) {
        try { return l == null ? null : mapper.writeValueAsString(l); } catch (Exception e) { return null; }
    }

    private List<String> lista(String json) {
        try { return json == null ? List.of() : mapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private static String corto(String s) {
        return s == null ? null : s.substring(0, Math.min(490, s.length()));
    }
}
