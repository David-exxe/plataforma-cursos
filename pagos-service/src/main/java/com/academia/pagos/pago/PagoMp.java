package com.academia.pagos.pago;

import com.mercadopago.resources.payment.Payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Vista mínima y neutral de un pago de Mercado Pago. PagoService trabaja solo con este record,
 * así no depende del SDK (y el modo mock / los tests pueden construirlo a mano).
 */
public record PagoMp(String id,
                     String status,
                     String statusDetail,
                     String externalReference,
                     BigDecimal monto,
                     String moneda,
                     String metodoPago,
                     OffsetDateTime fechaAprobacion) {

    public static PagoMp de(Payment p) {
        return new PagoMp(
                p.getId() == null ? null : String.valueOf(p.getId()),
                p.getStatus(),
                p.getStatusDetail(),
                p.getExternalReference(),
                p.getTransactionAmount(),
                p.getCurrencyId(),
                p.getPaymentMethodId(),
                p.getDateApproved());
    }
}
