package com.academia.pagos.pago;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Recibe las notificaciones (Webhooks) de Mercado Pago.
 * Mercado Pago espera 200/201 en menos de 22 s; si respondemos otra cosa REINTENTA
 * (15 min, 30 min, 6 h, 48 h...). Por eso:
 *   - firma invalida                   -> 401 (queda registrado, no se reintenta con exito)
 *   - pago inexistente (404 en la API) -> 200: es una notificacion de prueba o un id ajeno;
 *                                         reintentar nunca lo arreglaria
 *   - error transitorio consultando    -> 500 a proposito, para que MP vuelva a intentar
 *   - todo bien / tipo que no usamos   -> 200
 *
 * Flujo: validar firma -> GET /v1/payments/{id} (estado real) -> PagoService.aplicar(...)
 * -> si el estado cambia, PagoService escribe el evento en la tabla outbox DENTRO de la misma
 * transaccion -> OutboxPublicador lo publica en RabbitMQ (pago.aprobado / pago.rechazado /
 * pago.reembolsado) y comprobantes-service / notificaciones-service reaccionan.
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

        // 1) Firma HMAC (x-signature). El manifest usa el data.id de la QUERY, como indica Mercado Pago.
        if (firma.configurado()) {
            ev.setFirmaValida(firma.esValida(xSignature, xRequestId, dataIdQuery != null ? dataIdQuery : dataId));
            if (!ev.isFirmaValida()) {
                ev.setError("Firma invalida");
                eventos.save(ev);
                log.warn("Webhook con firma invalida (request-id {})", xRequestId);
                return ResponseEntity.status(401).build();
            }
        } else {
            log.warn("MP_WEBHOOK_SECRET no configurado: no se valida la firma (solo aceptable con MP_MODO=mock)");
        }

        // 2) Solo nos interesan los pagos (merchant_order, etc. se ignoran con 200)
        if (!"payment".equals(tipo) || dataId == null) {
            eventos.save(ev);
            return ResponseEntity.ok().build();
        }

        // 3) Consultar el pago real y aplicarlo (idempotente)
        try {
            servicio.procesarNotificacionDePago(dataId);
            ev.setProcesado(true);
            eventos.save(ev);
            return ResponseEntity.ok().build();
        } catch (MercadoPagoClient.MercadoPagoException e) {
            ev.setError(truncar(e.getMessage()));
            eventos.save(ev);
            if (e.esNoEncontrado()) {
                log.warn("Webhook para el pago {} que Mercado Pago no reconoce (¿notificacion de prueba?). Se responde 200.", dataId);
                return ResponseEntity.ok().build();
            }
            log.error("Error consultando el pago {}: {}. Mercado Pago reintentara.", dataId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        } catch (Exception e) {
            ev.setError(truncar(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            eventos.save(ev);
            log.error("Error procesando el pago {}: {}. Mercado Pago reintentara.", dataId, e.getMessage(), e);
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

    private static String truncar(String s) {
        return s == null ? null : s.substring(0, Math.min(490, s.length()));
    }
}
