package com.academia.pagos.pago;

import java.math.BigDecimal;
import java.time.Instant;

public record PagoDto(String pagoId, Long inscripcionId, String curso, BigDecimal monto, String moneda,
                      String estado, String estadoDetalle, String metodoPago, String mpPaymentId,
                      String linkPago, Instant fechaAprobacion, Instant creadoEn) {
    public static PagoDto de(Pago p) {
        return new PagoDto(p.getId().toString(), p.getInscripcionId(), p.getCursoNombre(), p.getMonto(), p.getMoneda(),
                p.getEstado().name(), p.getEstadoDetalle(), p.getMetodoPago(), p.getMpPaymentId(),
                p.getEstado() == EstadoPago.PENDIENTE || p.getEstado() == EstadoPago.RECHAZADO ? p.getLinkPago() : null,
                p.getFechaAprobacion(), p.getCreadoEn());
    }
}
