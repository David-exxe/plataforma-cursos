package com.academia.gateway.seguridad;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Administracion manual de IPs (requiere rol ADMIN, ver SeguridadConfig). */
@RestController
@RequestMapping("/gateway/admin/ips")
public class IpAdminController {

    private final IpBloqueoService servicio;

    public IpAdminController(IpBloqueoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public Map<String, Object> listar() {
        return servicio.listar();
    }

    public record BloqueoRequest(String ip, Integer minutos, String motivo) {}

    @PostMapping
    public ResponseEntity<Void> bloquear(@RequestBody BloqueoRequest req) {
        if (req.ip() == null || req.ip().isBlank()) return ResponseEntity.badRequest().build();
        servicio.bloquear(req.ip().trim(), req.minutos() == null ? 60 : req.minutos(),
                req.motivo() == null ? "Bloqueo manual" : req.motivo());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{ip}")
    public ResponseEntity<Void> desbloquear(@PathVariable String ip) {
        servicio.desbloquear(ip);
        return ResponseEntity.noContent().build();
    }
}
