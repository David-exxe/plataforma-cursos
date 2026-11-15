package com.academia.matricula.inscripcion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record InscripcionDto(Long id, Long cursoId, String curso, String modalidad, LocalDate fechaInicio,
                             String horario, String ubicacion, String estado, BigDecimal monto, String moneda,
                             String linkPago, Instant fechaPago, Instant creadoEn, String estudiante, String email) {

    public static InscripcionDto de(Inscripcion i) {
        boolean pagada = i.getEstado() == EstadoInscripcion.PAGADA;
        return new InscripcionDto(i.getId(), i.getCurso().getId(), i.getCurso().getNombre(),
                i.getCurso().getModalidad().name(), i.getCurso().getFechaInicio(), i.getCurso().getHorario(),
                pagada ? i.getCurso().getUbicacion() : null,          // el enlace/aula solo despues de pagar
                i.getEstado().name(), i.getMonto(), i.getMoneda(),
                i.getEstado() == EstadoInscripcion.PENDIENTE_PAGO ? i.getLinkPago() : null,
                i.getFechaPago(), i.getCreadoEn(), null, null);
    }

    public static InscripcionDto admin(Inscripcion i) {
        InscripcionDto d = de(i);
        return new InscripcionDto(d.id(), d.cursoId(), d.curso(), d.modalidad(), d.fechaInicio(), d.horario(),
                i.getCurso().getUbicacion(), d.estado(), d.monto(), d.moneda(), i.getLinkPago(), d.fechaPago(),
                d.creadoEn(), i.getUsuario().getNombre(), i.getUsuario().getEmail());
    }
}
