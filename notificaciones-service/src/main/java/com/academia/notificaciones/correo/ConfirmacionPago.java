package com.academia.notificaciones.correo;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Lo que necesita el correo, extraido del campo "datos" del evento pago.aprobado
 * (el sobre y los campos los define PagoService.datosEvento en pagos-service).
 */
public record ConfirmacionPago(String nombre,
                               String email,
                               String curso,
                               BigDecimal monto,
                               String moneda,
                               String operacionId,
                               Instant fecha) {

    public static ConfirmacionPago desde(JsonNode datos) {
        JsonNode est = datos.path("estudiante");
        String operacion = datos.path("mpPaymentId").asText(null);
        if (operacion == null || operacion.isBlank()) operacion = datos.path("pagoId").asText("");
        return new ConfirmacionPago(
                est.path("nombre").asText(""),
                est.path("email").asText(""),
                datos.path("curso").path("nombre").asText("tu curso"),
                datos.path("monto").decimalValue(),
                datos.path("moneda").asText("PEN"),
                operacion,
                fecha(datos.path("fechaAprobacion").asText(null)));
    }

    private static Instant fecha(String iso) {
        try {
            return iso == null || iso.isBlank() ? Instant.now() : Instant.parse(iso);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}

