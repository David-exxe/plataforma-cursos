package com.academia.gateway.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 401 / 403 del gateway con cuerpo JSON en espanol (por defecto Spring Security responde
 * con el cuerpo vacio y el frontend no sabria que decirle al usuario).
 *
 *  401 NO_AUTENTICADO  -> ruta protegida sin token
 *  401 SESION_INVALIDA -> token vencido, mal firmado o de otra audiencia
 *  403 ACCESO_DENEGADO -> token valido pero sin el rol requerido
 */
@Component
public class ErroresSeguridadGateway implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper mapper;

    public ErroresSeguridadGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String ruta = exchange.getRequest().getPath().value();
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        boolean traiaToken = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7);
        ErrorRespuesta cuerpo = traiaToken
                ? ErrorRespuesta.de(HttpStatus.UNAUTHORIZED, "Tu sesion expiro o no es valida. Inicia sesion de nuevo", ruta)
                        .conCodigo("SESION_INVALIDA")
                : ErrorRespuesta.de(HttpStatus.UNAUTHORIZED, null, ruta);
        return ManejadorErroresGateway.escribir(exchange.getResponse(), mapper, cuerpo);
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return ManejadorErroresGateway.escribir(exchange.getResponse(), mapper,
                ErrorRespuesta.de(HttpStatus.FORBIDDEN, "Acceso denegado", exchange.getRequest().getPath().value()));
    }
}
