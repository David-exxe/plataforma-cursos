package com.academia.comprobantes.comprobante;

import java.math.BigDecimal;
import java.time.Instant;

public record ComprobanteDto(Long id, String numero, Long inscripcionId, String estudiante, String email,
                             String curso, BigDecimal monto, String moneda, Instant fechaEmision,
                             String estadoEnvio, int intentosEnvio, String ultimoError, Instant enviadoEn) {
    public static ComprobanteDto de(Comprobante c) {
        return new ComprobanteDto(c.getId(), c.getNumero(), c.getInscripcionId(), c.getEstudianteNombre(), c.getEmail(),
                c.getCursoNombre(), c.getMonto(), c.getMoneda(), c.getFechaEmision(), c.getEstadoEnvio(),
                c.getIntentosEnvio(), c.getUltimoError(), c.getEnviadoEn());
    }
}
