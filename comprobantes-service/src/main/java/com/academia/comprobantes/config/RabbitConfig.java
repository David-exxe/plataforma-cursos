package com.academia.comprobantes.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "cursos.eventos";
    public static final String DLX = "cursos.eventos.dlx";
    public static final String COLA = "comprobantes.pagos.q";
    public static final String COLA_DLQ = COLA + ".dlq";

    @Bean TopicExchange exchangeEventos() { return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build(); }
    @Bean DirectExchange exchangeDlx() { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean Queue colaComprobantes() {
        return QueueBuilder.durable(COLA)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", COLA_DLQ)
                .build();
    }
    @Bean Queue colaComprobantesDlq() { return QueueBuilder.durable(COLA_DLQ).build(); }

    @Bean Binding bindAprobado() { return BindingBuilder.bind(colaComprobantes()).to(exchangeEventos()).with("pago.aprobado"); }
    @Bean Binding bindDlq() { return BindingBuilder.bind(colaComprobantesDlq()).to(exchangeDlx()).with(COLA_DLQ); }
}
