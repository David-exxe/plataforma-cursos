package com.academia.comprobantes.comprobante;

import com.academia.comprobantes.comun.ApiException;
import com.academia.comprobantes.config.SeguridadConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ComprobanteController {

    private final ComprobanteRepository repo;
    private final ComprobantePdfRepository pdfs;
    private final ComprobanteService servicio;

    public ComprobanteController(ComprobanteRepository repo, ComprobantePdfRepository pdfs, ComprobanteService servicio) {
        this.repo = repo;
        this.pdfs = pdfs;
        this.servicio = servicio;
    }

    @GetMapping("/api/comprobantes/mios")
    public List<ComprobanteDto> mios(@AuthenticationPrincipal Jwt jwt) {
        return repo.findByUsuarioIdOrderByFechaEmisionDesc(SeguridadConfig.usuarioId(jwt)).stream().map(ComprobanteDto::de).toList();
    }

    /** Descarga directa desde la web: aunque el correo falle, el estudiante tiene su comprobante. */
    @GetMapping("/api/comprobantes/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        Comprobante c = repo.findById(id).orElseThrow(() -> ApiException.noEncontrado("Comprobante no encontrado"));
        if (!SeguridadConfig.esAdmin(jwt) && !c.getUsuarioId().equals(SeguridadConfig.usuarioId(jwt))) {
            throw ApiException.noEncontrado("Comprobante no encontrado");
        }
        byte[] pdf = pdfs.findById(id).map(ComprobantePdf::getContenido).orElseThrow(() -> ApiException.noEncontrado("PDF no encontrado"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + c.getNumero() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/api/admin/comprobantes")
    public List<ComprobanteDto> admin(@RequestParam(required = false) String estado) {
        return (estado == null ? repo.findTop200ByOrderByFechaEmisionDesc() : repo.findTop200ByEstadoEnvioOrderByFechaEmisionDesc(estado.toUpperCase()))
                .stream().map(ComprobanteDto::de).toList();
    }

    @PostMapping("/api/admin/comprobantes/{id}/reenviar")
    public Map<String, Object> reenviar(@PathVariable Long id) {
        if (!repo.existsById(id)) throw ApiException.noEncontrado("Comprobante no encontrado");
        boolean ok = servicio.reenviar(id);
        return Map.of("reenviado", ok, "comprobante", ComprobanteDto.de(repo.findById(id).orElseThrow()));
    }

    /** Para el bot de WhatsApp (opcion 4). */
    @PostMapping("/internal/comprobantes/reenviar-por-telefono/{telefono}")
    public Map<String, Object> reenviarPorTelefono(@PathVariable String telefono) {
        Map<String, Object> r = new LinkedHashMap<>();
        var c = repo.findFirstByTelefonoOrderByFechaEmisionDesc(telefono.replaceAll("\\D", ""));
        if (c.isEmpty()) {
            r.put("reenviado", false);
            return r;
        }
        r.put("reenviado", servicio.reenviar(c.get().getId()));
        r.put("numero", c.get().getNumero());
        r.put("email", c.get().getEmail());
        return r;
    }
}
