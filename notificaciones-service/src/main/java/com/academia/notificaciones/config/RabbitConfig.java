package com.academia.notificaciones.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** notificaciones.q escucha todos los "momentos importantes" del estudiante. */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "cursos.eventos";
    public static final String DLX = "cursos.eventos.dlx";
    public static final String COLA = "notificaciones.q";
    public static final String COLA_DLQ = COLA + ".dlq";

    public static final String[] EVENTOS = {
            "inscripcion.creada", "pago.aprobado", "pago.rechazado",
            "curso.recordatorio", "comprobante.enviado", "comprobante.fallido"
    };

    @Bean TopicExchange exchangeEventos() { return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build(); }
    @Bean DirectExchange exchangeDlx() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean Queue colaNotificaciones() {
        return QueueBuilder.durable(COLA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", COLA_DLQ)
                .build();
    }
    @Bean Queue colaNotificacionesDlq() { return QueueBuilder.durable(COLA_DLQ).build(); }

    @Bean
    Declarables bindings() {
        List<Declarable> lista = new ArrayList<>();
        for (String ev : EVENTOS) {
            lista.add(BindingBuilder.bind(colaNotificaciones()).to(exchangeEventos()).with(ev));
        }
        lista.add(BindingBuilder.bind(colaNotificacionesDlq()).to(exchangeDlx()).with(COLA_DLQ));
        return new Declarables(lista);
    }
}
