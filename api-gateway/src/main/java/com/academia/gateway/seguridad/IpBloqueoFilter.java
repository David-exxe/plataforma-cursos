package com.academia.gateway.seguridad;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Se ejecuta ANTES que Spring Security (HIGHEST_PRECEDENCE): una IP bloqueada
 * ni siquiera llega a validar su JWT ni a los microservicios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IpBloqueoFilter implements WebFilter {

    private final IpBloqueoService servicio;
    private final IpBloqueoProperties props;

    public IpBloqueoFilter(IpBloqueoService servicio, IpBloqueoProperties props) {
        this.servicio = servicio;
        this.props = props;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = obtenerIp(exchange);

        Instant hasta = servicio.bloqueadaHasta(ip);
        if (hasta != null) {
            return responder(exchange.getResponse(), HttpStatus.FORBIDDEN,
                    "{\"error\":\"IP bloqueada\",\"hasta\":\"" + (hasta == Instant.MAX ? "permanente" : hasta) + "\"}");
        }
        if (!servicio.registrarPeticionYVerificarLimite(ip)) {
            return responder(exchange.getResponse(), HttpStatus.TOO_MANY_REQUESTS,
                    "{\"error\":\"Demasiadas peticiones. IP bloqueada temporalmente\"}");
        }

        String path = exchange.getRequest().getPath().value();
        boolean esLogin = path.equals("/api/auth/login") || path.equals("/api/auth/2fa/verificar");

        return chain.filter(exchange).doFinally(senal -> {
            if (esLogin) {
                HttpStatusCode status = exchange.getResponse().getStatusCode();
                if (status != null && status.value() == 401) {
                    servicio.registrarLoginFallido(ip);
                }
            }
        });
    }

    private String obtenerIp(ServerWebExchange exchange) {
        if (props.confiarXForwardedFor()) {
            String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        InetSocketAddress remoto = exchange.getRequest().getRemoteAddress();
        return (remoto != null && remoto.getAddress() != null) ? remoto.getAddress().getHostAddress() : "desconocida";
    }

    private Mono<Void> responder(ServerHttpResponse resp, HttpStatus status, String json) {
        resp.setStatusCode(status);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = resp.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return resp.writeWith(Mono.just(buffer));
    }
}
