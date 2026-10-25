package com.academia.matricula.curso;

import com.academia.matricula.comun.ApiException;
import com.academia.matricula.inscripcion.InscripcionRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@RestController
public class CursoController {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final CursoRepository cursos;
    private final InscripcionRepository inscripciones;

    public CursoController(CursoRepository cursos, InscripcionRepository inscripciones) {
        this.cursos = cursos;
        this.inscripciones = inscripciones;
    }

    // ---------- Publico ----------
    @GetMapping("/api/cursos")
    public List<CursoDto> catalogo() {
        return cursos.findByActivoTrueAndFechaInicioGreaterThanEqualOrderByFechaInicio(LocalDate.now(LIMA)).stream()
                .map(c -> CursoDto.publico(c, inscripciones.contarOcupados(c.getId())))
                .toList();
    }

    @GetMapping("/api/cursos/{id}")
    public CursoDto detalle(@PathVariable Long id) {
        Curso c = cursos.findById(id).filter(Curso::isActivo)
                .orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado"));
        return CursoDto.publico(c, inscripciones.contarOcupados(id));
    }

    // ---------- Admin ----------
    @GetMapping("/api/admin/cursos")
    public List<Curso> todos() {
        return cursos.findAll();
    }

    @PostMapping("/api/admin/cursos")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Curso crear(@Valid @RequestBody CursoRequest r) {
        if (cursos.existsByCodigoIgnoreCase(r.codigo())) throw ApiException.conflicto("Codigo de curso repetido");
        Curso c = new Curso();
        copiar(r, c);
        return cursos.save(c);
    }

    @PutMapping("/api/admin/cursos/{id}")
    @Transactional
    public Curso actualizar(@PathVariable Long id, @Valid @RequestBody CursoRequest r) {
        Curso c = cursos.findById(id).orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado"));
        copiar(r, c);
        return c;
    }

    private void copiar(CursoRequest r, Curso c) {
        c.setCodigo(r.codigo().toUpperCase());
        c.setNombre(r.nombre());
        c.setDescripcion(r.descripcion());
        c.setModalidad(r.modalidad());
        c.setPrecio(r.precio());
        c.setMoneda(r.moneda() == null ? "PEN" : r.moneda());
        c.setFechaInicio(r.fechaInicio());
        c.setHorario(r.horario());
        c.setUbicacion(r.ubicacion());
        c.setCupos(r.cupos());
        c.setActivo(r.activo() == null || r.activo());
    }
}
