package com.academia.gateway.config;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Respuesta cuando un microservicio esta caido o su circuito esta abierto. */
@RestController
public class FallbackController {

    private static final Map<String, String> NOMBRES = Map.of(
            "matricula", "matricula (cuentas, cursos e inscripciones)",
            "pagos", "pagos (Mercado Pago)",
            "notificaciones", "notificaciones y chatbot",
            "comprobantes", "comprobantes");

    @RequestMapping("/fallback/{servicio}")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(@PathVariable String servicio, ServerWebExchange exchange) {
        Throwable causa = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 503);
        body.put("codigo", "SERVICIO_NO_DISPONIBLE");
        body.put("servicio", servicio);
        body.put("error", "El servicio de " + NOMBRES.getOrDefault(servicio, servicio)
                + " no esta disponible en este momento. El resto de la plataforma sigue funcionando.");
        body.put("ruta", exchange.getRequest().getPath().value());
        body.put("motivo", causa == null ? "circuito abierto" : causa.getClass().getSimpleName());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body));
    }
}
