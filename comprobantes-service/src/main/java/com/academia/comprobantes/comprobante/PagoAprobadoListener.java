package com.academia.comprobantes.comprobante;

import com.academia.comprobantes.config.RabbitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PagoAprobadoListener {

    private final ObjectMapper mapper;
    private final ComprobanteService servicio;

    public PagoAprobadoListener(ObjectMapper mapper, ComprobanteService servicio) {
        this.mapper = mapper;
        this.servicio = servicio;
    }

    /**
     * Si emitir() falla (BD caida, PDF) -> excepcion -> RabbitMQ reintenta y luego DLQ.
     * El correo NO hace fallar el mensaje: su reintento lo maneja ComprobanteService.
     */
    @RabbitListener(queues = RabbitConfig.COLA)
    public void alRecibir(Message msg) throws Exception {
        JsonNode evento = mapper.readTree(msg.getBody());
        Long id = servicio.emitir(UUID.fromString(evento.path("eventoId").asText()), evento.path("datos"));
        servicio.intentarEnvio(id);
    }
}
