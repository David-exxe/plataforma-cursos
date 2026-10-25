package com.academia.matricula.inscripcion;

import com.academia.matricula.auth.Usuario;
import com.academia.matricula.auth.UsuarioRepository;
import com.academia.matricula.comun.Telefonos;
import com.academia.matricula.curso.CursoDto;
import com.academia.matricula.curso.CursoRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API interna para el bot de WhatsApp (notificaciones-service). Protegida con X-Internal-Key. */
@RestController
@RequestMapping("/internal")
public class InternoController {

    private final UsuarioRepository usuarios;
    private final InscripcionRepository inscripciones;
    private final CursoRepository cursos;

    public InternoController(UsuarioRepository usuarios, InscripcionRepository inscripciones, CursoRepository cursos) {
        this.usuarios = usuarios;
        this.inscripciones = inscripciones;
        this.cursos = cursos;
    }

    @GetMapping("/consultas/estudiante")
    @Transactional(readOnly = true)
    public Map<String, Object> porTelefono(@RequestParam String telefono) {
        String tel = Telefonos.normalizar(telefono);
        List<Usuario> u = usuarios.findByTelefono(tel);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("encontrado", !u.isEmpty());
        if (u.isEmpty()) return r;
        r.put("nombre", u.get(0).getNombre());
        r.put("inscripciones", inscripciones.porTelefono(tel).stream().map(InscripcionDto::de).toList());
        return r;
    }

    @GetMapping("/cursos")
    @Transactional(readOnly = true)
    public List<CursoDto> cursos() {
        return cursos.findByActivoTrueAndFechaInicioGreaterThanEqualOrderByFechaInicio(LocalDate.now(ZoneId.of("America/Lima")))
                .stream().map(c -> CursoDto.publico(c, inscripciones.contarOcupados(c.getId()))).toList();
    }
}
