package com.academia.pagos.pago;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Cliente REST de Mercado Pago (Checkout Pro). Se usa la API directamente con RestClient
 * para que se vea claramente que viaja en cada llamada.
 *   POST /checkout/preferences      -> crea la preferencia y devuelve init_point (link de pago)
 *   GET  /v1/payments/{id}          -> estado real del pago (NUNCA confiar en el cuerpo del webhook)
 *   GET  /v1/payments/search        -> busca pagos por external_reference (conciliacion)
 * En modo "mock" no se llama a Mercado Pago (desarrollo sin credenciales).
 */
@Component
public class MercadoPagoClient {

    private final RestClient client;
    private final boolean mock;
    private final boolean tokenPrueba;
    private final String urlPublica;
    private final String frontend;
    private final String descriptor;

    public MercadoPagoClient(@Value("${mercadopago.modo}") String modo,
                             @Value("${mercadopago.api-url}") String apiUrl,
                             @Value("${mercadopago.access-token}") String token,
                             @Value("${mercadopago.url-publica}") String urlPublica,
                             @Value("${mercadopago.frontend-url}") String frontend,
                             @Value("${mercadopago.descriptor}") String descriptor) {
        this.mock = !"real".equalsIgnoreCase(modo);
        if (!mock && (token == null || token.isBlank())) {
            throw new IllegalStateException("MP_MODO=real requiere MP_ACCESS_TOKEN");
        }
        this.tokenPrueba = token != null && token.startsWith("TEST-");
        this.urlPublica = urlPublica;
        this.frontend = frontend;
        this.descriptor = descriptor;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(3000);
        f.setReadTimeout(10000);
        this.client = RestClient.builder().baseUrl(apiUrl).requestFactory(f)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token).build();
    }

    public boolean esMock() { return mock; }

    public record Preferencia(String id, String linkPago) {}

    public Preferencia crearPreferencia(Pago p) {
        if (mock) {
            return new Preferencia("MOCK-PREF-" + UUID.randomUUID(),
                    frontend + "/pago-simulado?inscripcion=" + p.getInscripcionId());
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "CURSO-" + p.getCursoId());
        item.put("title", p.getCursoNombre());
        item.put("quantity", 1);
        item.put("currency_id", p.getMoneda());
        item.put("unit_price", p.getMonto());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("payer", Map.of("name", p.getEstudianteNombre(), "email", p.getEstudianteEmail()));
        body.put("external_reference", String.valueOf(p.getInscripcionId()));   // asi enlazamos pago <-> inscripcion
        body.put("notification_url", urlPublica + "/webhooks/mercadopago?source_news=webhooks");
        body.put("statement_descriptor", descriptor);
        body.put("metadata", Map.of("inscripcion_id", p.getInscripcionId(), "pago_id", p.getId().toString()));
        body.put("back_urls", Map.of(
                "success", frontend + "/pago/resultado?estado=aprobado&inscripcion=" + p.getInscripcionId(),
                "failure", frontend + "/pago/resultado?estado=rechazado&inscripcion=" + p.getInscripcionId(),
                "pending", frontend + "/pago/resultado?estado=pendiente&inscripcion=" + p.getInscripcionId()));
        if (frontend.startsWith("https://")) body.put("auto_return", "approved"); // MP lo rechaza con http://localhost

        JsonNode r = client.post().uri("/checkout/preferences")
                .header("X-Idempotency-Key", p.getId().toString())   // reintentos no crean preferencias duplicadas
                .body(body).retrieve().body(JsonNode.class);
        String link = tokenPrueba && r.hasNonNull("sandbox_init_point") ? r.get("sandbox_init_point").asText() : r.path("init_point").asText();
        return new Preferencia(r.path("id").asText(), link);
    }

    public JsonNode consultarPago(String paymentId) {
        return client.get().uri("/v1/payments/{id}", paymentId).retrieve().body(JsonNode.class);
    }

    public JsonNode buscarPorReferencia(Long inscripcionId) {
        return client.get().uri(u -> u.path("/v1/payments/search")
                        .queryParam("external_reference", inscripcionId)
                        .queryParam("sort", "date_created").queryParam("criteria", "desc").build())
                .retrieve().body(JsonNode.class);
    }
}
