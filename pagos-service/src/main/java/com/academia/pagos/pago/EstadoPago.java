package com.academia.pagos.pago;

public enum EstadoPago {
    PENDIENTE, EN_PROCESO, APROBADO, RECHAZADO, REEMBOLSADO;

    /** Traduce el "status" de Mercado Pago a nuestro estado. */
    public static EstadoPago desdeMercadoPago(String status) {
        if (status == null) return PENDIENTE;
        return switch (status) {
            case "approved" -> APROBADO;
            case "rejected", "cancelled" -> RECHAZADO;
            case "refunded", "charged_back" -> REEMBOLSADO;
            case "in_process", "in_mediation", "authorized", "pending" -> EN_PROCESO;
            default -> PENDIENTE;
        };
    }
}
