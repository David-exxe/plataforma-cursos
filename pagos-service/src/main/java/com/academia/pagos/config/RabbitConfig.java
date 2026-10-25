package com.academia.pagos.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** pagos-service solo PUBLICA (pago.aprobado, pago.rechazado, pago.reembolsado). */
@Configuration
public class RabbitConfig {
    public static final String EXCHANGE = "cursos.eventos";

    @Bean TopicExchange exchangeEventos() { return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build(); }
}
