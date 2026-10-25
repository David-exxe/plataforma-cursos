package com.academia.matricula.curso;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CursoDto(Long id, String codigo, String nombre, String descripcion, String modalidad,
                       BigDecimal precio, String moneda, LocalDate fechaInicio, String horario,
                       String ubicacion, int cupos, long cuposDisponibles, boolean activo) {

    /** La ubicacion (enlace de clase) solo se muestra a quien ya pago; en el catalogo publico se oculta si es online. */
    public static CursoDto publico(Curso c, long ocupados) {
        String ubic = c.getModalidad() == Modalidad.ONLINE ? "Se envia al confirmar el pago" : c.getUbicacion();
        return new CursoDto(c.getId(), c.getCodigo(), c.getNombre(), c.getDescripcion(), c.getModalidad().name(),
                c.getPrecio(), c.getMoneda(), c.getFechaInicio(), c.getHorario(), ubic, c.getCupos(),
                Math.max(0, c.getCupos() - ocupados), c.isActivo());
    }
}
