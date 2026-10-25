package com.academia.pagos.pago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recibe las notificaciones de Mercado Pago.
 * Mercado Pago espera 200/201 en menos de 22 s; si respondemos otra cosa REINTENTA
 * (15 min, 30 min, 6 h, 48 h...). Por eso:
 *   - firma invalida           -> 401 (y queda registrado)
 *   - error consultando el pago -> 500 a proposito, para que MP vuelva a intentar
 *   - todo bien / no nos interesa -> 200
 */
@RestController
public class WebhookMercadoPagoController {

    private static final Logger log = LoggerFactory.getLogger(WebhookMercadoPagoController.class);

    private final PagoService servicio;
    private final FirmaWebhookValidator firma;
    private final WebhookEventoRepository eventos;
    private final ObjectMapper mapper;

    public WebhookMercadoPagoController(PagoService servicio, FirmaWebhookValidator firma,
                                        WebhookEventoRepository eventos, ObjectMapper mapper) {
        this.servicio = servicio;
        this.firma = firma;
        this.eventos = eventos;
        this.mapper = mapper;
    }

    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> recibir(@RequestParam(name = "data.id", required = false) String dataIdQuery,
                                        @RequestParam(name = "type", required = false) String typeQuery,
                                        @RequestParam(name = "topic", required = false) String topicQuery,
                                        @RequestParam(name = "id", required = false) String idQuery,
                                        @RequestHeader(name = "x-signature", required = false) String xSignature,
                                        @RequestHeader(name = "x-request-id", required = false) String xRequestId,
                                        @RequestBody(required = false) String cuerpo) {
        JsonNode body = leer(cuerpo);
        String tipo = primero(typeQuery, topicQuery, body.path("type").asText(null), body.path("topic").asText(null));
        String dataId = primero(dataIdQuery, body.path("data").path("id").asText(null), idQuery);

        WebhookEvento ev = new WebhookEvento();
        ev.setRequestId(xRequestId);
        ev.setTipo(tipo);
        ev.setDataId(dataId);
        ev.setPayload(cuerpo);

        if (firma.configurado()) {
            ev.setFirmaValida(firma.esValida(xSignature, xRequestId, dataIdQuery != null ? dataIdQuery : dataId));
            if (!ev.isFirmaValida()) {
                ev.setError("Firma invalida");
                eventos.save(ev);
                log.warn("Webhook con firma invalida (request-id {})", xRequestId);
                return ResponseEntity.status(401).build();
            }
        } else {
            log.warn("MP_WEBHOOK_SECRET no configurado: no se valida la firma (solo aceptable en desarrollo)");
        }

        if (!"payment".equals(tipo) || dataId == null) {
            eventos.save(ev);
            return ResponseEntity.ok().build();   // otros tipos (merchant_order, etc.) no los usamos
        }

        try {
            servicio.procesarNotificacionDePago(dataId);
            ev.setProcesado(true);
            eventos.save(ev);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            ev.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(490, e.getMessage().length())));
            eventos.save(ev);
            log.error("Error procesando pago {}: {}. Mercado Pago reintentara.", dataId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    private JsonNode leer(String cuerpo) {
        try {
            return cuerpo == null || cuerpo.isBlank() ? mapper.createObjectNode() : mapper.readTree(cuerpo);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String primero(String... valores) {
        for (String v : valores) if (v != null && !v.isBlank()) return v;
        return null;
    }
}
