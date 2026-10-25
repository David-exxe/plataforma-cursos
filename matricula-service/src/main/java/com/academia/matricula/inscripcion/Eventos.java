package com.academia.matricula.inscripcion;

import com.academia.matricula.auth.Usuario;
import com.academia.matricula.curso.Curso;

import java.util.LinkedHashMap;
import java.util.Map;

/** Arma el bloque "datos" de los eventos que publica matricula-service. */
final class Eventos {
    private Eventos() {}

    static Map<String, Object> estudiante(Usuario u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("usuarioId", u.getId());
        m.put("nombre", u.getNombre());
        m.put("email", u.getEmail());
        m.put("telefono", u.getTelefono());
        return m;
    }

    static Map<String, Object> curso(Curso c, boolean incluirUbicacion) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("codigo", c.getCodigo());
        m.put("nombre", c.getNombre());
        m.put("modalidad", c.getModalidad().name());
        m.put("fechaInicio", c.getFechaInicio().toString());
        m.put("horario", c.getHorario());
        if (incluirUbicacion) m.put("ubicacion", c.getUbicacion());
        return m;
    }

    /** inscripcion.creada -> lo consume notificaciones (WhatsApp "recibimos tu inscripcion, paga aqui"). */
    static Map<String, Object> inscripcionCreada(Inscripcion i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inscripcionId", i.getId());
        m.put("estudiante", estudiante(i.getUsuario()));
        m.put("curso", curso(i.getCurso(), false));
        m.put("monto", i.getMonto());
        m.put("moneda", i.getMoneda());
        m.put("linkPago", i.getLinkPago());
        return m;
    }

    /** curso.recordatorio -> notificaciones (WhatsApp "tu curso empieza manana"). */
    static Map<String, Object> recordatorio(Inscripcion i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inscripcionId", i.getId());
        m.put("estudiante", estudiante(i.getUsuario()));
        m.put("curso", curso(i.getCurso(), true));
        return m;
    }
}
