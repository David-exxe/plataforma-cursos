package com.academia.pagos.comun;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException noEncontrado(String m) { return new ApiException(HttpStatus.NOT_FOUND, m); }
    public static ApiException conflicto(String m) { return new ApiException(HttpStatus.CONFLICT, m); }
    public static ApiException noAutorizado(String m) { return new ApiException(HttpStatus.UNAUTHORIZED, m); }
    public static ApiException prohibido(String m) { return new ApiException(HttpStatus.FORBIDDEN, m); }
    public static ApiException invalido(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
}
