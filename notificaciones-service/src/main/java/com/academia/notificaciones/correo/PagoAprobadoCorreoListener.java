package com.academia.notificaciones.correo;

import com.academia.notificaciones.config.RabbitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Escucha pago.aprobado (cola notificaciones.correo.q) y manda el correo de confirmacion.
 * Si el envio falla -> excepcion -> reintentos de Spring AMQP -> DLQ (notificaciones.correo.q.dlq).
 */
@Component
public class PagoAprobadoCorreoListener {

    private static final Logger log = LoggerFactory.getLogger(PagoAprobadoCorreoListener.class);

    private final ObjectMapper mapper;
    private final CorreoService correo;
    private final boolean habilitado;

    public PagoAprobadoCorreoListener(ObjectMapper mapper, CorreoService correo,
                                      @Value("${correo.confirmacion-pago.habilitado}") boolean habilitado) {
        this.mapper = mapper;
        this.correo = correo;
        this.habilitado = habilitado;
    }

    @RabbitListener(queues = RabbitConfig.COLA_CORREO)
    public void alRecibir(Message msg) throws Exception {
        JsonNode evento = mapper.readTree(msg.getBody());
        UUID eventoId = UUID.fromString(evento.path("eventoId").asText());
        if (!habilitado) {
            log.info("Correo de confirmacion deshabilitado (CORREO_CONFIRMACION_PAGO=false); evento {} descartado", eventoId);
            return;
        }
        correo.enviarConfirmacionPago(eventoId, ConfirmacionPago.desde(evento.path("datos")));
    }
}
