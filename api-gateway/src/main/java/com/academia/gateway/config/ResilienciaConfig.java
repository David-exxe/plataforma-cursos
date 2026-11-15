package com.academia.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuracion del circuit breaker (Resilience4j), igual para los 4 servicios pero con estado independiente:
 *  - CERRADO: todo normal.
 *  - Si de las ultimas 10 llamadas al menos 5 fallan y el 50 % o mas son errores -> ABIERTO:
 *    durante 20 s no se llama al servicio, se responde el fallback al instante.
 *  - SEMI-ABIERTO: deja pasar 3 llamadas de prueba; si funcionan, vuelve a CERRADO.
 * El TimeLimiter es largo (90 s) porque en Render free un servicio dormido tarda ~1 min en despertar.
 */
@Configuration
public class ResilienciaConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> circuitBreakerPorDefecto(
            @Value("${servicios.timeout-segundos}") int timeoutSeg) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(timeoutSeg))
                        .build())
                .build());
    }
}
