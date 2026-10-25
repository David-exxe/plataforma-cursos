package com.academia.pagos.outbox;

import com.academia.pagos.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Publica los eventos pendientes cada 2 s. Si RabbitMQ esta caido, el evento queda en la BD
 * y se reintenta: no se pierde nada. Usa publisher confirms (espera el ACK del broker).
 * Puede publicar un evento 2 veces (si cae justo despues del ACK) -> por eso los consumidores
 * son idempotentes (usan eventoId / inscripcionId).
 */
@Component
public class OutboxPublicador {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublicador.class);
    private final OutboxRepository repo;
    private final RabbitTemplate rabbit;

    public OutboxPublicador(OutboxRepository repo, RabbitTemplate rabbit) {
        this.repo = repo;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    @Transactional
    public void publicar() {
        for (OutboxEvento e : repo.bloquearPendientes()) {
            try {
                Message msg = MessageBuilder.withBody(e.getPayload().getBytes(StandardCharsets.UTF_8))
                        .setContentType("application/json")
                        .setContentEncoding("UTF-8")
                        .setMessageId(e.getEventoId().toString())
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                        .setHeader("tipo", e.getTipo())
                        .build();
                rabbit.invoke(ops -> {
                    ops.send(RabbitConfig.EXCHANGE, e.getTipo(), msg);
                    ops.waitForConfirmsOrDie(5000);
                    return true;
                });
                e.setEstado("PUBLICADO");
                e.setPublicadoEn(Instant.now());
                log.info("Evento publicado {} {}", e.getTipo(), e.getEventoId());
            } catch (Exception ex) {
                e.setEstado("ERROR");
                e.setIntentos(e.getIntentos() + 1);
                e.setUltimoError(truncar(ex.getMessage()));
                log.warn("No se pudo publicar {} (intento {}): {}", e.getEventoId(), e.getIntentos(), ex.getMessage());
                break; // si el broker esta caido no tiene sentido seguir con los demas
            }
        }
    }

    private static String truncar(String s) {
        return s == null ? null : s.substring(0, Math.min(500, s.length()));
    }
}
