package com.academia.pagos.pago;

import com.academia.pagos.comun.ApiException;
import com.academia.pagos.config.SeguridadConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class PagoController {

    private final PagoRepository repo;
    private final PagoService servicio;
    private final ObjectMapper mapper;

    public PagoController(PagoRepository repo, PagoService servicio, ObjectMapper mapper) {
        this.repo = repo;
        this.servicio = servicio;
        this.mapper = mapper;
    }

    /** El front consulta esto al volver de Mercado Pago (back_url) para mostrar el resultado. */
    @GetMapping("/api/pagos/inscripcion/{inscripcionId}")
    @Transactional(readOnly = true)
    public PagoDto estado(@AuthenticationPrincipal Jwt jwt, @PathVariable Long inscripcionId) {
        Pago p = repo.findByInscripcionId(inscripcionId).orElseThrow(() -> ApiException.noEncontrado("Pago no encontrado"));
        if (!SeguridadConfig.esAdmin(jwt) && !p.getUsuarioId().equals(SeguridadConfig.usuarioId(jwt))) {
            throw ApiException.noEncontrado("Pago no encontrado");
        }
        return PagoDto.de(p);
    }

    /** Solo con MP_MODO=mock: simula que el estudiante pago (resultado=approved | rejected). */
    @PostMapping("/api/pagos/simulacion/{inscripcionId}")
    public PagoDto simular(@AuthenticationPrincipal Jwt jwt, @PathVariable Long inscripcionId,
                           @RequestParam(defaultValue = "approved") String resultado) {
        Pago p = repo.findByInscripcionId(inscripcionId).orElseThrow(() -> ApiException.noEncontrado("Pago no encontrado"));
        if (!SeguridadConfig.esAdmin(jwt) && !p.getUsuarioId().equals(SeguridadConfig.usuarioId(jwt))) {
            throw ApiException.noEncontrado("Pago no encontrado");
        }
        if (!resultado.equals("approved") && !resultado.equals("rejected")) throw ApiException.invalido("resultado: approved | rejected");
        return servicio.simular(inscripcionId, resultado, mapper);
    }

    @GetMapping("/api/admin/pagos")
    @Transactional(readOnly = true)
    public List<PagoDto> admin(@RequestParam(required = false) EstadoPago estado) {
        return (estado == null ? repo.findAllByOrderByCreadoEnDesc() : repo.findByEstadoOrderByCreadoEnDesc(estado))
                .stream().map(PagoDto::de).toList();
    }
}
