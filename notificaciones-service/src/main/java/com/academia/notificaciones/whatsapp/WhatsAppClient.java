package com.academia.notificaciones.whatsapp;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * WhatsApp Cloud API (Meta):  POST {api-url}/{phone-number-id}/messages
 *   texto    : {"messaging_product":"whatsapp","to":"51987654321","type":"text","text":{"body":"..."}}
 *   plantilla: {"messaging_product":"whatsapp","to":"...","type":"template",
 *               "template":{"name":"pago_confirmado","language":{"code":"es"},
 *                           "components":[{"type":"body","parameters":[{"type":"text","text":"Ana"}]}]}}
 * Respuesta: {"messages":[{"id":"wamid.HBgL..."}]}  -> guardamos el id para seguir el estado (entregado/leido).
 */
@Component
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final RestClient client;
    private final boolean mock;
    private final String phoneNumberId;
    private final String idioma;

    public WhatsAppClient(@Value("${whatsapp.modo}") String modo,
                          @Value("${whatsapp.api-url}") String apiUrl,
                          @Value("${whatsapp.phone-number-id}") String phoneNumberId,
                          @Value("${whatsapp.access-token}") String token,
                          @Value("${whatsapp.idioma-plantillas}") String idioma) {
        this.mock = !"real".equalsIgnoreCase(modo);
        if (!mock && (token.isBlank() || phoneNumberId.isBlank())) {
            throw new IllegalStateException("WA_MODO=real requiere WA_ACCESS_TOKEN y WA_PHONE_NUMBER_ID");
        }
        this.phoneNumberId = phoneNumberId;
        this.idioma = idioma;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(10000);
        this.client = RestClient.builder().baseUrl(apiUrl).requestFactory(f)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    /** Devuelve el id del mensaje (wamid). Lanza excepcion si Meta lo rechaza. */
    public String enviarTexto(String telefono, String texto) {
        if (mock) return simular(telefono, texto);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", telefono);
        body.put("type", "text");
        body.put("text", Map.of("preview_url", true, "body", texto));
        return enviar(body);
    }

    public String enviarPlantilla(String telefono, String plantilla, List<String> parametros, String textoLog) {
        if (mock) return simular(telefono, "[plantilla " + plantilla + "] " + textoLog);
        List<Map<String, String>> params = parametros.stream().map(p -> Map.of("type", "text", "text", p)).toList();
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", plantilla);
        template.put("language", Map.of("code", idioma));
        template.put("components", List.of(Map.of("type", "body", "parameters", params)));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", telefono);
        body.put("type", "template");
        body.put("template", template);
        return enviar(body);
    }

    private String enviar(Map<String, Object> body) {
        JsonNode r = client.post().uri("/{id}/messages", phoneNumberId).body(body).retrieve().body(JsonNode.class);
        return r.path("messages").path(0).path("id").asText(null);
    }

    private String simular(String telefono, String texto) {
        log.info("\n[WHATSAPP MOCK] -> {}\n{}\n", telefono, texto);
        return "wamid.MOCK-" + UUID.randomUUID();
    }
}
