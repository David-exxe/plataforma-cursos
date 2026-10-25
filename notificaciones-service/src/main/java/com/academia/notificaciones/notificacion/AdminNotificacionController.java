package com.academia.notificaciones.notificacion;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notificaciones")
public class AdminNotificacionController {

    private final NotificacionRepository repo;
    private final NotificacionService servicio;

    public AdminNotificacionController(NotificacionRepository repo, NotificacionService servicio) {
        this.repo = repo;
        this.servicio = servicio;
    }

    @GetMapping
    public List<Notificacion> listar(@RequestParam(required = false) String estado) {
        return estado == null ? repo.findTop200ByOrderByCreadoEnDesc() : repo.findTop200ByEstadoOrderByCreadoEnDesc(estado.toUpperCase());
    }

    @PostMapping("/{id}/reintentar")
    public ResponseEntity<Void> reintentar(@PathVariable Long id) {
        servicio.reprogramar(id);
        return ResponseEntity.accepted().build();
    }
}
