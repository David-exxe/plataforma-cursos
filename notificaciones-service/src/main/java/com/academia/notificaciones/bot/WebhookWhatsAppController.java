package com.academia.notificaciones.bot;

import com.academia.notificaciones.notificacion.NotificacionService;
import com.academia.notificaciones.whatsapp.WhatsAppClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Webhook de WhatsApp Cloud API.
 *  GET  -> verificacion inicial que hace Meta al registrar la URL (hub.challenge).
 *  POST -> mensajes entrantes del estudiante y estados de los mensajes que enviamos.
 *          Firma: X-Hub-Signature-256 = "sha256=" + HMAC-SHA256(cuerpo crudo, App Secret).
 */
@RestController
@RequestMapping("/webhooks/whatsapp")
public class WebhookWhatsAppController {

    private static final Logger log = LoggerFactory.getLogger(WebhookWhatsAppController.class);

    private final ObjectMapper mapper;
    private final BotService bot;
    private final WhatsAppClient whatsapp;
    private final MensajeEntranteRepository entrantes;
    private final NotificacionService notificaciones;
    private final String verifyToken;
    private final String appSecret;

    public WebhookWhatsAppController(ObjectMapper mapper, BotService bot, WhatsAppClient whatsapp,
                                     MensajeEntranteRepository entrantes, NotificacionService notificaciones,
                                     @Value("${whatsapp.verify-token}") String verifyToken,
                                     @Value("${whatsapp.app-secret}") String appSecret) {
        this.mapper = mapper;
        this.bot = bot;
        this.whatsapp = whatsapp;
        this.entrantes = entrantes;
        this.notificaciones = notificaciones;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
    }

    @GetMapping
    public ResponseEntity<String> verificar(@RequestParam(name = "hub.mode", required = false) String modo,
                                            @RequestParam(name = "hub.verify_token", required = false) String token,
                                            @RequestParam(name = "hub.challenge", required = false) String challenge) {
        if ("subscribe".equals(modo) && verifyToken.equals(token)) return ResponseEntity.ok(challenge);
        return ResponseEntity.status(403).build();
    }

    @PostMapping
    public ResponseEntity<Void> recibir(@RequestHeader(name = "X-Hub-Signature-256", required = false) String firma,
                                        @RequestBody String cuerpo) throws Exception {
        if (!appSecret.isBlank() && !firmaValida(cuerpo, firma)) {
            log.warn("Webhook de WhatsApp con firma invalida");
            return ResponseEntity.status(401).build();
        }
        JsonNode raiz = mapper.readTree(cuerpo);
        for (JsonNode entry : raiz.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                for (JsonNode m : value.path("messages")) procesarMensaje(m);
                for (JsonNode st : value.path("statuses")) {
                    notificaciones.actualizarEstado(st.path("id").asText(), st.path("status").asText(),
                            st.path("errors").path(0).path("title").asText(null));
                }
            }
        }
        return ResponseEntity.ok().build(); // siempre 200 rapido; si no, Meta reintenta
    }

    private void procesarMensaje(JsonNode m) {
        String waId = m.path("id").asText();
        String telefono = m.path("from").asText();
        String texto = switch (m.path("type").asText()) {
            case "text" -> m.path("text").path("body").asText();
            case "button" -> m.path("button").path("text").asText();
            case "interactive" -> m.path("interactive").path("button_reply").path("title").asText(
                                  m.path("interactive").path("list_reply").path("title").asText(""));
            default -> "";
        };
        MensajeEntrante e = new MensajeEntrante();
        e.setWaMessageId(waId);
        e.setTelefono(telefono);
        e.setTexto(texto);
        try {
            if (entrantes.existsByWaMessageId(waId)) return;  // Meta reenvio el mismo mensaje
            entrantes.save(e);
        } catch (DataIntegrityViolationException dup) {
            return;
        }
        String respuesta = bot.responder(telefono, texto);
        try {
            whatsapp.enviarTexto(telefono, respuesta);    // dentro de la ventana de 24 h: texto libre permitido
        } catch (Exception ex) {
            log.error("No se pudo responder a {}: {}", telefono, ex.getMessage());
        }
        e.setRespuesta(respuesta);
        entrantes.save(e);
    }

    private boolean firmaValida(String cuerpo, String firma) {
        if (firma == null || !firma.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String esperado = "sha256=" + HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), firma.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
