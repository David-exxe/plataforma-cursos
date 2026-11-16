package com.academia.notificaciones.comun;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

/**
 * Formato UNICO de error de toda la plataforma. Lo usan ManejadorErrores (errores en controladores),
 * ErroresSeguridad (401/403 de Spring Security) y AtributosError (errores que llegan a /error).
 *
 * Ejemplo:
 * {
 *   "timestamp": "2026-11-16T17:05:12Z",
 *   "status": 404,
 *   "codigo": "NO_ENCONTRADO",
 *   "error": "Curso no encontrado",
 *   "ruta": "/api/cursos/99"
 * }
 *
 * "error" se mantiene con ese nombre porque el frontend ya lo lee (core/http/api-error.ts).
 * Los campos nulos no se envian.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorRespuesta(
        String timestamp,
        int status,
        String codigo,
        String error,
        String ruta,
        Map<String, String> detalle,
        String referencia) {

    public static ErrorRespuesta de(HttpStatus status, String mensaje, String ruta) {
        return new ErrorRespuesta(Instant.now().toString(), status.value(), codigoDe(status.value()),
                mensaje == null || mensaje.isBlank() ? mensajePorDefecto(status.value()) : mensaje, ruta, null, null);
    }

    public ErrorRespuesta conCodigo(String nuevoCodigo) {
        return new ErrorRespuesta(timestamp, status, nuevoCodigo, error, ruta, detalle, referencia);
    }

    public ErrorRespuesta conDetalle(Map<String, String> campos) {
        return new ErrorRespuesta(timestamp, status, codigo, error, ruta, campos, referencia);
    }

    public ErrorRespuesta conReferencia(String ref) {
        return new ErrorRespuesta(timestamp, status, codigo, error, ruta, detalle, ref);
    }

    public ResponseEntity<ErrorRespuesta> respuesta() {
        return ResponseEntity.status(status).body(this);
    }

    /** Codigo estable para que el frontend (u otro cliente) decida sin depender del texto. */
    public static String codigoDe(int status) {
        return switch (status) {
            case 400 -> "SOLICITUD_INVALIDA";
            case 401 -> "NO_AUTENTICADO";
            case 403 -> "ACCESO_DENEGADO";
            case 404 -> "NO_ENCONTRADO";
            case 405 -> "METODO_NO_PERMITIDO";
            case 409 -> "CONFLICTO";
            case 413 -> "CONTENIDO_DEMASIADO_GRANDE";
            case 415 -> "TIPO_NO_SOPORTADO";
            case 429 -> "DEMASIADAS_PETICIONES";
            case 503 -> "SERVICIO_NO_DISPONIBLE";
            default -> status >= 500 ? "ERROR_INTERNO" : "ERROR";
        };
    }

    /** Mensaje en espanol cuando la excepcion no trae uno propio (nunca se muestra texto tecnico en ingles). */
    public static String mensajePorDefecto(int status) {
        return switch (status) {
            case 400 -> "La solicitud no es valida";
            case 401 -> "Debes iniciar sesion para continuar";
            case 403 -> "Acceso denegado";
            case 404 -> "El recurso solicitado no existe";
            case 405 -> "Metodo HTTP no permitido para esta ruta";
            case 409 -> "El registro ya existe o viola una restriccion";
            case 413 -> "El contenido enviado es demasiado grande";
            case 415 -> "Tipo de contenido no soportado. Envia JSON";
            case 429 -> "Demasiadas peticiones. Intenta en unos minutos";
            case 503 -> "Servicio no disponible temporalmente";
            default -> status >= 500 ? "Error interno" : "Error en la solicitud";
        };
    }
}
