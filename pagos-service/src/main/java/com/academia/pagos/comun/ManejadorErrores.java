package com.academia.pagos.comun;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manejo global de errores con ResponseEntity: cualquier excepcion lanzada en un controlador
 * termina aqui y se convierte en un ErrorRespuesta con el codigo HTTP correcto.
 *
 *   400 -> datos invalidos, JSON mal formado, parametro con tipo incorrecto (/api/cursos/abc)
 *   401 -> no autenticado / credenciales invalidas
 *   403 -> sin permiso (por ejemplo, un estudiante entrando a /api/admin)
 *   404 -> recurso o ruta inexistente
 *   405 -> metodo HTTP equivocado
 *   409 -> duplicados o conflictos de concurrencia
 *   500 -> error no previsto: se registra en el log con un codigo de referencia y
 *          al cliente solo se le devuelve ese codigo (nunca el stack trace).
 *
 * Los 401/403 que ocurren ANTES de llegar al controlador (token ausente o vencido) los
 * resuelve ErroresSeguridad; los errores que llegan a /error, AtributosError.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    // ---------- errores del negocio (lanzados con ApiException) ----------
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorRespuesta> api(ApiException e, HttpServletRequest req) {
        if (e.getStatus().is5xxServerError()) log.warn("{} {} -> {}: {}", req.getMethod(), req.getRequestURI(), e.getStatus().value(), e.getMessage());
        return ErrorRespuesta.de(e.getStatus(), e.getMessage(), req.getRequestURI()).respuesta();
    }

    // ---------- 400 ----------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(g -> campos.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return ErrorRespuesta.de(HttpStatus.BAD_REQUEST, "Datos invalidos", req.getRequestURI())
                .conCodigo("VALIDACION").conDetalle(campos).respuesta();
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorRespuesta> validacionParametros(HandlerMethodValidationException e, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getAllValidationResults().forEach(r -> {
            String nombre = r.getMethodParameter().getParameterName();
            r.getResolvableErrors().forEach(err -> campos.putIfAbsent(nombre == null ? "parametro" : nombre, err.getDefaultMessage()));
        });
        return ErrorRespuesta.de(HttpStatus.BAD_REQUEST, "Datos invalidos", req.getRequestURI())
                .conCodigo("VALIDACION").conDetalle(campos.isEmpty() ? null : campos).respuesta();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorRespuesta> restricciones(ConstraintViolationException e, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> {
            String ruta = v.getPropertyPath().toString();
            campos.putIfAbsent(ruta.substring(ruta.lastIndexOf('.') + 1), v.getMessage());
        });
        return ErrorRespuesta.de(HttpStatus.BAD_REQUEST, "Datos invalidos", req.getRequestURI())
                .conCodigo("VALIDACION").conDetalle(campos).respuesta();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorRespuesta> jsonInvalido(HttpMessageNotReadableException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.BAD_REQUEST, "JSON invalido o incompleto", req.getRequestURI()).respuesta();
    }

    /** Ej.: GET /api/cursos/abc cuando se espera un numero. Antes terminaba en 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorRespuesta> tipoIncorrecto(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.BAD_REQUEST,
                "El valor '" + e.getValue() + "' no es valido para el parametro '" + e.getName() + "'", req.getRequestURI()).respuesta();
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorRespuesta> parametroFaltante(MissingServletRequestParameterException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.BAD_REQUEST, "Falta el parametro obligatorio '" + e.getParameterName() + "'",
                req.getRequestURI()).respuesta();
    }

    // ---------- 401 / 403 ----------
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorRespuesta> noAutenticado(AuthenticationException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.UNAUTHORIZED, null, req.getRequestURI()).respuesta();
    }

    /** Incluye AuthorizationDeniedException de @PreAuthorize. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorRespuesta> acceso(AccessDeniedException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.FORBIDDEN, "Acceso denegado", req.getRequestURI()).respuesta();
    }

    // ---------- 404 / 405 / 415 ----------
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorRespuesta> rutaInexistente(NoResourceFoundException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.NOT_FOUND, "La ruta solicitada no existe", req.getRequestURI()).respuesta();
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> metodo(HttpRequestMethodNotSupportedException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.METHOD_NOT_ALLOWED,
                "El metodo " + e.getMethod() + " no esta permitido en esta ruta", req.getRequestURI()).respuesta();
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorRespuesta> tipoContenido(HttpMediaTypeNotSupportedException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.UNSUPPORTED_MEDIA_TYPE, null, req.getRequestURI()).respuesta();
    }

    // ---------- 409 ----------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorRespuesta> integridad(DataIntegrityViolationException e, HttpServletRequest req) {
        log.info("Violacion de integridad en {}: {}", req.getRequestURI(), e.getMostSpecificCause().getMessage());
        return ErrorRespuesta.de(HttpStatus.CONFLICT, "El registro ya existe o viola una restriccion", req.getRequestURI()).respuesta();
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorRespuesta> concurrencia(ObjectOptimisticLockingFailureException e, HttpServletRequest req) {
        return ErrorRespuesta.de(HttpStatus.CONFLICT,
                "El registro fue modificado por otra operacion. Vuelve a intentarlo", req.getRequestURI()).respuesta();
    }

    // ---------- otros con status propio ----------
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorRespuesta> conStatus(ResponseStatusException e, HttpServletRequest req) {
        HttpStatus s = HttpStatus.resolve(e.getStatusCode().value());
        if (s == null) s = HttpStatus.BAD_REQUEST;
        return ErrorRespuesta.de(s, e.getReason(), req.getRequestURI()).respuesta();
    }

    // ---------- 500 ----------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> general(Exception e, HttpServletRequest req) {
        // Excepciones propias de Spring MVC que ya traen status (parametros, cabeceras, etc.)
        if (e instanceof ErrorResponse er) {
            HttpStatus s = HttpStatus.resolve(er.getStatusCode().value());
            if (s != null && !s.is5xxServerError()) {
                return ErrorRespuesta.de(s, null, req.getRequestURI()).respuesta();
            }
        }
        String referencia = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Error no controlado [ref {}] en {} {}", referencia, req.getMethod(), req.getRequestURI(), e);
        return ErrorRespuesta.de(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", req.getRequestURI())
                .conReferencia(referencia).respuesta();
    }
}
