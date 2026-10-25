package com.academia.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enrutamiento: el frontend Angular solo conoce el gateway (puerto 8080).
 * Las rutas /internal/** NO se publican: son solo para comunicacion entre servicios.
 * Se quita cualquier cabecera X-Internal-Key que venga de afuera para que nadie la falsifique.
 */
@Configuration
public class RutasConfig {

    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder,
                              @Value("${servicios.matricula}") String matricula,
                              @Value("${servicios.pagos}") String pagos,
                              @Value("${servicios.notificaciones}") String notificaciones,
                              @Value("${servicios.comprobantes}") String comprobantes) {
        return builder.routes()
                .route("matricula", r -> r.path("/api/auth/**", "/api/cursos/**", "/api/inscripciones/**",
                                "/api/admin/cursos/**", "/api/admin/inscripciones/**", "/api/admin/recordatorios/**")
                        .filters(f -> f.removeRequestHeader("X-Internal-Key"))
                        .uri(matricula))
                .route("pagos", r -> r.path("/api/pagos/**", "/api/admin/pagos/**", "/webhooks/mercadopago")
                        .filters(f -> f.removeRequestHeader("X-Internal-Key"))
                        .uri(pagos))
                .route("notificaciones", r -> r.path("/api/admin/notificaciones/**", "/webhooks/whatsapp")
                        .filters(f -> f.removeRequestHeader("X-Internal-Key"))
                        .uri(notificaciones))
                .route("comprobantes", r -> r.path("/api/comprobantes/**", "/api/admin/comprobantes/**")
                        .filters(f -> f.removeRequestHeader("X-Internal-Key"))
                        .uri(comprobantes))
                .build();
    }
}
