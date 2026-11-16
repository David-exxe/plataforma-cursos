package com.academia.gateway.seguridad;

import com.academia.gateway.error.ErrorRespuesta;
import com.academia.gateway.error.ManejadorErroresGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsUtils;
import org.springframework.web.cors.reactive.DefaultCorsProcessor;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Se ejecuta ANTES que Spring Security (HIGHEST_PRECEDENCE): una IP bloqueada
 * ni siquiera llega a validar su JWT ni a los microservicios.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IpBloqueoFilter implements WebFilter {

    private final IpBloqueoService servicio;
    private final ClienteIpResolver ipResolver;
    private final CorsConfigurationSource cors;
    private final ObjectMapper mapper;
    private final DefaultCorsProcessor procesadorCors = new DefaultCorsProcessor();

    public IpBloqueoFilter(IpBloqueoService servicio, ClienteIpResolver ipResolver,
                           @Qualifier("corsConfigurationSource") CorsConfigurationSource cors, ObjectMapper mapper) {
        this.servicio = servicio;
        this.ipResolver = ipResolver;
        this.cors = cors;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // El preflight CORS (OPTIONS) lo responde el filtro CORS sin llegar a los microservicios.
        // Si se bloqueara aqui, el navegador no enviaria la peticion real y el usuario veria
        // un "error de red" en vez del aviso de IP bloqueada.
        if (CorsUtils.isPreFlightRequest(exchange.getRequest())) return chain.filter(exchange);

        String ip = ipResolver.resolver(exchange);

        Instant hasta = servicio.bloqueadaHasta(ip);
        if (hasta != null) {
            // "IP bloqueada" y "hasta" los usa el frontend para mostrar el aviso de bloqueo
            return responder(exchange, HttpStatus.FORBIDDEN, "IP_BLOQUEADA", "IP bloqueada",
                    hasta == Instant.MAX ? "permanente" : hasta.toString());
        }
        if (!servicio.registrarPeticionYVerificarLimite(ip)) {
            return responder(exchange, HttpStatus.TOO_MANY_REQUESTS, "DEMASIADAS_PETICIONES",
                    "Demasiadas peticiones. IP bloqueada temporalmente", null);
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

    /**
     * Este filtro corre ANTES que el filtro CORS de Spring Security. Sin las cabeceras CORS el
     * navegador oculta la respuesta (el frontend veria un error de red en vez de "IP bloqueada"),
     * por eso se aplican aqui con la misma configuracion del gateway.
     */
    private Mono<Void> responder(ServerWebExchange exchange, HttpStatus status, String codigo, String mensaje, String hasta) {
        CorsConfiguration cfg = cors.getCorsConfiguration(exchange);
        if (cfg != null) procesadorCors.process(cfg, exchange);
        ErrorRespuesta base = ErrorRespuesta.de(status, mensaje, exchange.getRequest().getPath().value()).conCodigo(codigo);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", base.timestamp());
        cuerpo.put("status", base.status());
        cuerpo.put("codigo", base.codigo());
        cuerpo.put("error", base.error());
        cuerpo.put("ruta", base.ruta());
        if (hasta != null) cuerpo.put("hasta", hasta);
        return ManejadorErroresGateway.escribir(exchange.getResponse(), mapper, status, cuerpo);
    }
}
