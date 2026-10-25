package com.academia.pagos.comun;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return cuerpo(e.getStatus(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.put(f.getField(), f.getDefaultMessage()));
        return cuerpo(HttpStatus.BAD_REQUEST, "Datos invalidos", campos);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException e) {
        return cuerpo(HttpStatus.CONFLICT, "El registro ya existe o viola una restriccion", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> acceso(AccessDeniedException e) {
        return cuerpo(HttpStatus.FORBIDDEN, "Acceso denegado", null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException e) {
        return cuerpo(HttpStatus.BAD_REQUEST, "JSON invalido o incompleto", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> general(Exception e) {
        // 404, 405, parametros faltantes, etc. de Spring traen su propio status
        if (e instanceof ErrorResponse er) {
            HttpStatus s = HttpStatus.resolve(er.getStatusCode().value());
            return cuerpo(s == null ? HttpStatus.BAD_REQUEST : s, er.getBody().getDetail(), null);
        }
        log.error("Error no controlado", e);
        return cuerpo(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", null); // no filtrar detalles
    }

    private ResponseEntity<Map<String, Object>> cuerpo(HttpStatus s, String msg, Object detalle) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", s.value());
        m.put("error", msg);
        if (detalle != null) m.put("detalle", detalle);
        return ResponseEntity.status(s).body(m);
    }
}
