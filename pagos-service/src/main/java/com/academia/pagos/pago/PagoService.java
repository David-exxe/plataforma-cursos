package com.academia.pagos.pago;

import com.academia.pagos.comun.ApiException;
import com.academia.pagos.outbox.OutboxService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository repo;
    private final MercadoPagoClient mp;
    private final OutboxService outbox;
    private final TransactionTemplate tx;

    public PagoService(PagoRepository repo, MercadoPagoClient mp, OutboxService outbox, PlatformTransactionManager tm) {
        this.repo = repo;
        this.mp = mp;
        this.outbox = outbox;
        this.tx = new TransactionTemplate(tm);
    }

    public record CrearPreferenciaRequest(Long inscripcionId, Long usuarioId, String estudianteNombre,
                                          String estudianteEmail, String estudianteTelefono, String estudianteDocumento,
                                          Long cursoId, String cursoNombre, BigDecimal monto, String moneda) {}

    public record PreferenciaResponse(String pagoId, String preferenceId, String linkPago, String estado) {}

    /** IDEMPOTENTE: si matricula reintenta, se devuelve el mismo pago/link. */
    public PreferenciaResponse crearPreferencia(CrearPreferenciaRequest r) {
        if (r.inscripcionId() == null || r.monto() == null || r.monto().signum() <= 0) {
            throw ApiException.invalido("Datos de pago invalidos");
        }
        Pago p = tx.execute(s -> repo.findByInscripcionId(r.inscripcionId()).orElse(null));
        if (p == null) {
            try {
                p = tx.execute(s -> repo.save(nuevo(r)));
            } catch (DataIntegrityViolationException carrera) {
                p = tx.execute(s -> repo.findByInscripcionId(r.inscripcionId()).orElseThrow());
            }
        }
        if (p.getEstado() == EstadoPago.APROBADO || p.getLinkPago() != null) {
            return respuesta(p);
        }
        MercadoPagoClient.Preferencia pref = mp.crearPreferencia(p);   // llamada HTTP fuera de la TX
        UUID id = p.getId();
        Pago guardado = tx.execute(s -> {
            Pago x = repo.findById(id).orElseThrow();
            x.setPreferenceId(pref.id());
            x.setLinkPago(pref.linkPago());
            return x;
        });
        log.info("Preferencia {} creada para inscripcion {}", pref.id(), r.inscripcionId());
        return respuesta(guardado);
    }

    private Pago nuevo(CrearPreferenciaRequest r) {
        Pago p = new Pago();
        p.setId(UUID.randomUUID());
        p.setInscripcionId(r.inscripcionId());
        p.setUsuarioId(r.usuarioId());
        p.setEstudianteNombre(r.estudianteNombre());
        p.setEstudianteEmail(r.estudianteEmail());
        p.setEstudianteTelefono(r.estudianteTelefono());
        p.setEstudianteDocumento(r.estudianteDocumento());
        p.setCursoId(r.cursoId());
        p.setCursoNombre(r.cursoNombre());
        p.setMonto(r.monto());
        p.setMoneda(r.moneda());
        return p;
    }

    private PreferenciaResponse respuesta(Pago p) {
        return new PreferenciaResponse(p.getId().toString(), p.getPreferenceId(), p.getLinkPago(), p.getEstado().name());
    }

    /** Webhook: se consulta el pago REAL a la API de Mercado Pago y se aplica. */
    public void procesarNotificacionDePago(String paymentId) {
        JsonNode pagoMp = mp.consultarPago(paymentId);
        aplicar(pagoMp);
    }

    /**
     * Aplica el estado de un pago de Mercado Pago a nuestro registro.
     * Reglas:
     *  - Se valida monto y moneda contra lo que cobramos (evita pagar S/1 por un curso de S/250).
     *  - Un pago APROBADO no retrocede a RECHAZADO (pueden llegar webhooks desordenados
     *    de intentos anteriores fallidos); solo puede pasar a REEMBOLSADO.
     *  - El evento se publica solo cuando el estado CAMBIA (idempotencia ante webhooks repetidos).
     */
    public void aplicar(JsonNode pagoMp) {
        String ref = pagoMp.path("external_reference").asText(null);
        if (ref == null || !ref.matches("\\d+")) {
            log.warn("Pago {} sin external_reference valido, se ignora", pagoMp.path("id").asText());
            return;
        }
        Long inscripcionId = Long.valueOf(ref);
        tx.executeWithoutResult(s -> {
            Pago p = repo.bloquearPorInscripcion(inscripcionId).orElse(null);
            if (p == null) {
                log.warn("Llego un pago para inscripcion {} que no tenemos registrada", inscripcionId);
                return;
            }
            EstadoPago nuevo = EstadoPago.desdeMercadoPago(pagoMp.path("status").asText());
            String paymentId = pagoMp.path("id").asText();

            if (nuevo == EstadoPago.APROBADO) {
                BigDecimal pagado = pagoMp.path("transaction_amount").decimalValue();
                String moneda = pagoMp.path("currency_id").asText();
                if (pagado.compareTo(p.getMonto()) != 0 || !moneda.equalsIgnoreCase(p.getMoneda())) {
                    log.error("ALERTA: pago {} por {} {} no coincide con {} {}", paymentId, pagado, moneda, p.getMonto(), p.getMoneda());
                    p.setEstadoDetalle("monto_no_coincide");
                    return;
                }
            }
            EstadoPago anterior = p.getEstado();
            if (anterior == nuevo) return;                                           // webhook repetido
            if (anterior == EstadoPago.APROBADO && nuevo != EstadoPago.REEMBOLSADO) return;
            if (anterior == EstadoPago.REEMBOLSADO) return;

            p.setEstado(nuevo);
            p.setMpPaymentId(paymentId);
            p.setEstadoDetalle(pagoMp.path("status_detail").asText(null));
            p.setMetodoPago(pagoMp.path("payment_method_id").asText(null));
            if (nuevo == EstadoPago.APROBADO) {
                String f = pagoMp.path("date_approved").asText(null);
                p.setFechaAprobacion(f != null && !f.isBlank() ? OffsetDateTime.parse(f).toInstant() : Instant.now());
                outbox.registrar("pago.aprobado", datosEvento(p));
            } else if (nuevo == EstadoPago.RECHAZADO) {
                outbox.registrar("pago.rechazado", datosEvento(p));
            } else if (nuevo == EstadoPago.REEMBOLSADO) {
                outbox.registrar("pago.reembolsado", datosEvento(p));
            }
            log.info("Pago de inscripcion {}: {} -> {}", inscripcionId, anterior, nuevo);
        });
    }

    /** Lo que viaja en pago.aprobado / pago.rechazado: todo lo que comprobantes y notificaciones necesitan. */
    private Map<String, Object> datosEvento(Pago p) {
        Map<String, Object> est = new LinkedHashMap<>();
        est.put("usuarioId", p.getUsuarioId());
        est.put("nombre", p.getEstudianteNombre());
        est.put("email", p.getEstudianteEmail());
        est.put("telefono", p.getEstudianteTelefono());
        est.put("documento", p.getEstudianteDocumento());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("pagoId", p.getId().toString());
        d.put("mpPaymentId", p.getMpPaymentId());
        d.put("inscripcionId", p.getInscripcionId());
        d.put("monto", p.getMonto());
        d.put("moneda", p.getMoneda());
        d.put("metodoPago", p.getMetodoPago());
        d.put("motivo", p.getEstadoDetalle());
        d.put("fechaAprobacion", p.getFechaAprobacion() == null ? null : p.getFechaAprobacion().toString());
        d.put("estudiante", est);
        d.put("curso", Map.of("id", p.getCursoId(), "nombre", p.getCursoNombre()));
        d.put("linkPago", p.getLinkPago());
        return d;
    }

    /** Solo modo mock: simula la respuesta de GET /v1/payments/{id} y la procesa igual que un webhook real. */
    public PagoDto simular(Long inscripcionId, String status, com.fasterxml.jackson.databind.ObjectMapper om) {
        if (!mp.esMock()) throw ApiException.prohibido("La simulacion solo esta disponible con MP_MODO=mock");
        Pago p = tx.execute(s -> repo.findByInscripcionId(inscripcionId).orElse(null));
        if (p == null) throw ApiException.noEncontrado("No hay pago para esa inscripcion");
        var n = om.createObjectNode();
        n.put("id", String.valueOf(System.currentTimeMillis()));
        n.put("status", status);
        n.put("status_detail", "approved".equals(status) ? "accredited" : "cc_rejected_insufficient_amount");
        n.put("external_reference", String.valueOf(inscripcionId));
        n.put("transaction_amount", p.getMonto());
        n.put("currency_id", p.getMoneda());
        n.put("payment_method_id", "visa");
        n.put("date_approved", "approved".equals(status) ? OffsetDateTime.now().toString() : null);
        aplicar(n);
        return tx.execute(s -> PagoDto.de(repo.findByInscripcionId(inscripcionId).orElseThrow()));
    }
}
