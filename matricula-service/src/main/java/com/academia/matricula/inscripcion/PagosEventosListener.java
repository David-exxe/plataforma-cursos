package com.academia.matricula.inscripcion;

import com.academia.matricula.config.RabbitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Consume pago.aprobado / pago.rechazado (publicados por pagos-service).
 * IDEMPOTENTE: si el mismo evento llega dos veces, la segunda no cambia nada.
 * Si lanza excepcion -> RabbitMQ reintenta 4 veces (2s, 6s, 18s) y luego va a matricula.pagos.q.dlq
 */
@Component
public class PagosEventosListener {

    private static final Logger log = LoggerFactory.getLogger(PagosEventosListener.class);
    private final InscripcionRepository repo;
    private final ObjectMapper mapper;
    private final CorreoInscripcionService correo;
    private final TransactionTemplate tx;

    public PagosEventosListener(InscripcionRepository repo, ObjectMapper mapper, CorreoInscripcionService correo,
                                PlatformTransactionManager tm) {
        this.repo = repo;
        this.mapper = mapper;
        this.correo = correo;
        this.tx = new TransactionTemplate(tm);
    }

    @RabbitListener(queues = RabbitConfig.COLA)
    public void alRecibir(Message msg) throws Exception {
        JsonNode evento = mapper.readTree(msg.getBody());
        String tipo = msg.getMessageProperties().getReceivedRoutingKey();
        JsonNode d = evento.path("datos");
        long inscripcionId = d.path("inscripcionId").asLong();
        log.info("Evento {} recibido para inscripcion {}", tipo, inscripcionId);

        Boolean enviarInvitacion = tx.execute(s -> procesar(tipo, inscripcionId, d));
        if (Boolean.TRUE.equals(enviarInvitacion)) correo.enviar(inscripcionId);
    }

    private boolean procesar(String tipo, long inscripcionId, JsonNode d) {
        Inscripcion i = repo.findById(inscripcionId)
                .orElseThrow(() -> new IllegalStateException("Inscripcion " + inscripcionId + " no existe"));

        if ("pago.aprobado".equals(tipo)) {
            if (i.getEstado() == EstadoInscripcion.PAGADA) return false; // duplicado: ya procesado
            if (i.getEstado() != EstadoInscripcion.PENDIENTE_PAGO) {
                // Pago llego despues de vencer/cancelar: el dinero entro, se respeta la matricula y se avisa
                log.warn("Pago aprobado para inscripcion {} en estado {}. Se marca PAGADA, revisar cupos.", inscripcionId, i.getEstado());
            }
            i.setEstado(EstadoInscripcion.PAGADA);
            i.setMpPaymentId(d.path("mpPaymentId").asText(null));
            i.setUltimoEstadoPago("approved");
            String fecha = d.path("fechaAprobacion").asText(null);
            i.setFechaPago(fecha != null ? Instant.parse(fecha) : Instant.now());
            repo.save(i);
            return true;
        }
        if ("pago.rechazado".equals(tipo) && i.getEstado() == EstadoInscripcion.PENDIENTE_PAGO) {
            i.setUltimoEstadoPago(d.path("motivo").asText("rejected"));
            repo.save(i);
        }
        return false;
    }
}
