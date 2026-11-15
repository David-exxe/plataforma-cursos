package com.academia.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.GatewayFilterSpec;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enrutamiento: el frontend Angular solo conoce el gateway.
 * Cada ruta tiene su PROPIO circuit breaker (mismo nombre que el servicio):
 * si "pagos" se cae, solo se abre el circuito de pagos y catalogo, login, comprobantes y bot
 * siguen funcionando con normalidad. Mientras el circuito esta abierto, el gateway responde
 * al instante con /fallback/{servicio} (503 + mensaje claro) en vez de colgarse esperando.
 * El tiempo maximo de espera por servicio lo controla el TimeLimiter (ResilienciaConfig).
 *
 * Las rutas /internal/** NO se publican y se elimina cualquier X-Internal-Key que venga de afuera.
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
                .route("matricula", r -> r.path("/api/auth/**", "/api/cursos", "/api/cursos/**", "/api/inscripciones",
                                "/api/inscripciones/**", "/api/admin/cursos", "/api/admin/cursos/**", "/api/admin/inscripciones",
                                "/api/admin/inscripciones/**", "/api/admin/recordatorios/**")
                        .filters(f -> comunes(f, "matricula"))
                        .uri(matricula))
                .route("pagos", r -> r.path("/api/pagos/**", "/api/admin/pagos", "/api/admin/pagos/**", "/webhooks/mercadopago")
                        .filters(f -> comunes(f, "pagos"))
                        .uri(pagos))
                .route("notificaciones", r -> r.path("/api/bot/**", "/api/admin/notificaciones", "/api/admin/notificaciones/**",
                                "/webhooks/whatsapp")
                        .filters(f -> comunes(f, "notificaciones"))
                        .uri(notificaciones))
                .route("comprobantes", r -> r.path("/api/comprobantes/**", "/api/admin/comprobantes", "/api/admin/comprobantes/**")
                        .filters(f -> comunes(f, "comprobantes"))
                        .uri(comprobantes))
                .build();
    }

    private static GatewayFilterSpec comunes(GatewayFilterSpec f, String servicio) {
        return f.removeRequestHeader("X-Internal-Key")
                .circuitBreaker(c -> c.setName(servicio)
                        .setFallbackUri("forward:/fallback/" + servicio)
                        // 502/504 = el proxy (Render) no pudo llegar al servicio -> cuenta como caida
                        .addStatusCode("502")
                        .addStatusCode("504"));
    }
}
