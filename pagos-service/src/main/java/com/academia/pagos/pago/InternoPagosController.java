package com.academia.pagos.pago;

import org.springframework.web.bind.annotation.*;

/** Solo lo llama matricula-service (cabecera X-Internal-Key). */
@RestController
@RequestMapping("/internal/pagos")
public class InternoPagosController {

    private final PagoService servicio;

    public InternoPagosController(PagoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/preferencias")
    public PagoService.PreferenciaResponse crear(@RequestBody PagoService.CrearPreferenciaRequest r) {
        return servicio.crearPreferencia(r);
    }
}
