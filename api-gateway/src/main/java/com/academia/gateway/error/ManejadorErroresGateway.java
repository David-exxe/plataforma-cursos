package com.academia.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Manejo global de errores del API Gateway (WebFlux no usa @RestControllerAdvice para lo que
 * ocurre en el enrutamiento, por eso se implementa ErrorWebExceptionHandler).
 *
 * Reemplaza la respuesta por defecto de Spring Boot ({"error":"Not Found","path":...} en ingles)
 * por el mismo ErrorRespuesta que usan los microservicios:
 *   404 -> la ruta no existe en el gateway (ej. /api/cursoz)
 *   405 / 415 / 400 -> errores de la peticion
 *   503 -> no se pudo conectar con el microservicio (si no lo atrapo antes el circuit breaker)
 *   504 -> el microservicio tardo demasiado
 *   500 -> error inesperado, con codigo de referencia para buscarlo en los logs de Render
 *
 * @Order(-2): se ejecuta antes que el manejador por defecto de Boot (que tiene -1).
 */
@Component
@Order(-2)
public class ManejadorErroresGateway implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErroresGateway.class);
    private final ObjectMapper mapper;

    public ManejadorErroresGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse res = exchange.getResponse();
        if (res.isCommitted()) return Mono.error(ex);
        String ruta = exchange.getRequest().getPath().value();
        return escribir(res, mapper, convertir(ex, ruta, exchange.getRequest().getMethod().name()));
    }

    private ErrorRespuesta convertir(Throwable ex, String ruta, String metodo) {
        if (ex instanceof AuthenticationException) {
            return ErrorRespuesta.de(HttpStatus.UNAUTHORIZED, null, ruta);
        }
        if (ex instanceof AccessDeniedException) {
            return ErrorRespuesta.de(HttpStatus.FORBIDDEN, "Acceso denegado", ruta);
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus s = HttpStatus.resolve(rse.getStatusCode().value());
            if (s == null) s = HttpStatus.INTERNAL_SERVER_ERROR;
            String msg = switch (s) {
                case NOT_FOUND -> "La ruta solicitada no existe";
                case METHOD_NOT_ALLOWED -> "El metodo " + metodo + " no esta permitido en esta ruta";
                case SERVICE_UNAVAILABLE -> "Servicio no disponible temporalmente";
                case GATEWAY_TIMEOUT -> "El servicio tardo demasiado en responder. Intenta de nuevo";
                default -> null; // mensaje por defecto en espanol segun el status
            };
            if (s.is5xxServerError()) log.warn("{} {} -> {} ({})", metodo, ruta, s.value(), rse.getMessage());
            return ErrorRespuesta.de(s, msg, ruta);
        }
        if (tieneCausa(ex, TimeoutException.class)) {
            log.warn("{} {} -> 504 timeout", metodo, ruta);
            return ErrorRespuesta.de(HttpStatus.GATEWAY_TIMEOUT, "El servicio tardo demasiado en responder. Intenta de nuevo", ruta);
        }
        if (tieneCausa(ex, ConnectException.class)) {
            log.warn("{} {} -> 503 sin conexion con el microservicio", metodo, ruta);
            return ErrorRespuesta.de(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible temporalmente", ruta);
        }
        String referencia = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Error no controlado en el gateway [ref {}] {} {}", referencia, metodo, ruta, ex);
        return ErrorRespuesta.de(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ruta).conReferencia(referencia);
    }

    private static boolean tieneCausa(Throwable ex, Class<? extends Throwable> tipo) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (tipo.isInstance(t)) return true;
            if (t.getCause() == t) break;
        }
        return false;
    }

    /**
     * Escribe cualquier cuerpo JSON con su status. No borra las cabeceras ya puestas
     * (asi se conservan las de CORS y el navegador puede leer el error).
     */
    public static Mono<Void> escribir(ServerHttpResponse res, ObjectMapper mapper, ErrorRespuesta cuerpo) {
        return escribir(res, mapper, HttpStatus.valueOf(cuerpo.status()), cuerpo);
    }

    public static Mono<Void> escribir(ServerHttpResponse res, ObjectMapper mapper, HttpStatus status, Object cuerpo) {
        if (res.isCommitted()) return Mono.empty();
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(cuerpo);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"error\":\"Error interno\"}").getBytes(StandardCharsets.UTF_8);
        }
        res.setStatusCode(status);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        res.getHeaders().setContentLength(bytes.length);
        DataBuffer buffer = res.bufferFactory().wrap(bytes);
        return res.writeWith(Mono.just(buffer));
    }
}
