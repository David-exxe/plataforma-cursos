package com.academia.notificaciones.notificacion;

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

@Component
public class EventosListener {

    private static final Logger log = LoggerFactory.getLogger(EventosListener.class);

    private final ObjectMapper mapper;
    private final NotificacionService servicio;
    private final String telefonoAdmin;
    private final String frontend;

    public EventosListener(ObjectMapper mapper, NotificacionService servicio,
                           @Value("${whatsapp.telefono-admin}") String telefonoAdmin,
                           @Value("${app.frontend-url}") String frontend) {
        this.mapper = mapper;
        this.servicio = servicio;
        this.telefonoAdmin = telefonoAdmin;
        this.frontend = frontend;
    }

    @RabbitListener(queues = RabbitConfig.COLA)
    public void alRecibir(Message msg) throws Exception {
        String tipo = msg.getMessageProperties().getReceivedRoutingKey();
        JsonNode evento = mapper.readTree(msg.getBody());
        UUID eventoId = UUID.fromString(evento.path("eventoId").asText());
        Mensajes.Mensaje m = Mensajes.desdeEvento(tipo, evento.path("datos"), telefonoAdmin, frontend);
        if (m == null) return;

        // Se registra (si falla la BD -> excepcion -> reintento de RabbitMQ -> DLQ).
        // El ENVIO no lanza excepcion: si WhatsApp falla, el reintento lo maneja el scheduler.
        Long id = servicio.registrar(eventoId, m);
        if (id == null) {
            log.info("Evento {} ({}) ya notificado o sin telefono", eventoId, tipo);
            return;
        }
        servicio.intentarEnvio(id);
    }
}
