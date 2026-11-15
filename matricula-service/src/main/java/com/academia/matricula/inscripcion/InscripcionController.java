package com.academia.matricula.inscripcion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class InscripcionController {

    private final InscripcionService servicio;
    private final TareasProgramadas tareas;

    public InscripcionController(InscripcionService servicio, TareasProgramadas tareas) {
        this.servicio = servicio;
        this.tareas = tareas;
    }

    public record InscribirRequest(@NotNull Long cursoId) {}

    @PostMapping("/api/inscripciones")
    @ResponseStatus(HttpStatus.CREATED)
    public InscripcionDto inscribir(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InscribirRequest r) {
        return servicio.inscribir(usuario(jwt), r.cursoId());
    }

    @GetMapping("/api/inscripciones/mias")
    public List<InscripcionDto> mias(@AuthenticationPrincipal Jwt jwt) {
        return servicio.misInscripciones(usuario(jwt));
    }

    @GetMapping("/api/inscripciones/{id}")
    public InscripcionDto detalle(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return servicio.detalle(usuario(jwt), id, esAdmin(jwt));
    }

    @PostMapping("/api/inscripciones/{id}/link-pago")
    public InscripcionDto regenerarLink(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return servicio.linkPago(usuario(jwt), id);
    }

    @PostMapping("/api/inscripciones/{id}/cancelar")
    public InscripcionDto cancelar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return servicio.cancelar(usuario(jwt), id);
    }

    // ---------- Admin ----------
    @GetMapping("/api/admin/inscripciones")
    public List<InscripcionDto> admin(@RequestParam(required = false) EstadoInscripcion estado) {
        return servicio.admin(estado);
    }

    /** Para la demo: dispara los recordatorios sin esperar al cron de las 9 am. */
    @PostMapping("/api/admin/recordatorios/ejecutar")
    public Map<String, Integer> recordatorios() {
        return Map.of("recordatoriosGenerados", tareas.generarRecordatorios());
    }

    private static Long usuario(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    private static boolean esAdmin(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains("ADMIN");
    }
}
