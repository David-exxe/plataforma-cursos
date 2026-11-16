package com.academia.notificaciones.comun;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 401 y 403 que Spring Security genera en sus filtros, ANTES de llegar a un controlador
 * (por eso @RestControllerAdvice no los ve). Sin esto, la respuesta sale con cuerpo vacio.
 *
 *  401 NO_AUTENTICADO   -> la peticion no trae token
 *  401 SESION_INVALIDA  -> trae token pero esta vencido, mal firmado o es de otra audiencia
 *  403 ACCESO_DENEGADO  -> token valido pero sin el rol necesario (ej. estudiante en /api/admin)
 */
@Component
public class ErroresSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper mapper;

    public ErroresSeguridad(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e) throws IOException {
        String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
        boolean traiaToken = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7);
        ErrorRespuesta cuerpo = traiaToken
                ? ErrorRespuesta.de(HttpStatus.UNAUTHORIZED, "Tu sesion expiro o no es valida. Inicia sesion de nuevo", req.getRequestURI())
                        .conCodigo("SESION_INVALIDA")
                : ErrorRespuesta.de(HttpStatus.UNAUTHORIZED, null, req.getRequestURI());
        escribir(res, cuerpo);
    }

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException e) throws IOException {
        escribir(res, ErrorRespuesta.de(HttpStatus.FORBIDDEN, "Acceso denegado", req.getRequestURI()));
    }

    /** Tambien lo usa ClaveInternaFilter para responder con el mismo formato. */
    public static void escribir(HttpServletResponse res, ObjectMapper mapper, ErrorRespuesta cuerpo) throws IOException {
        if (res.isCommitted()) return;
        res.setStatus(cuerpo.status());
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), cuerpo);
    }

    private void escribir(HttpServletResponse res, ErrorRespuesta cuerpo) throws IOException {
        escribir(res, mapper, cuerpo);
    }
}
