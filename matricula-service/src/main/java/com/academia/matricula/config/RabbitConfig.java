package com.academia.matricula.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ (cada servicio declara lo que usa; las declaraciones son idempotentes).
 *   Exchange topic  "cursos.eventos"      -> todos los eventos de negocio
 *   Exchange direct "cursos.eventos.dlx"  -> mensajes que fallaron todos sus reintentos
 * Matricula escucha pago.aprobado / pago.rechazado para marcar la inscripcion.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "cursos.eventos";
    public static final String DLX = "cursos.eventos.dlx";
    public static final String COLA = "matricula.pagos.q";
    public static final String COLA_DLQ = COLA + ".dlq";

    @Bean TopicExchange exchangeEventos() { return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build(); }
    @Bean DirectExchange exchangeDlx() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean Queue colaPagos() {
        return QueueBuilder.durable(COLA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", COLA_DLQ)
                .build();
    }
    @Bean Queue colaPagosDlq() { return QueueBuilder.durable(COLA_DLQ).build(); }

    @Bean Binding bindAprobado() { return BindingBuilder.bind(colaPagos()).to(exchangeEventos()).with("pago.aprobado"); }
    @Bean Binding bindRechazado() { return BindingBuilder.bind(colaPagos()).to(exchangeEventos()).with("pago.rechazado"); }
    @Bean Binding bindDlq() { return BindingBuilder.bind(colaPagosDlq()).to(exchangeDlx()).with(COLA_DLQ); }
}
