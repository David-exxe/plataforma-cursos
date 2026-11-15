package com.academia.pagos.pago;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferencePayerRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPResultsResourcesPage;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente de Mercado Pago (Checkout Pro) sobre el SDK oficial com.mercadopago:sdk-java.
 *   PreferenceClient.create  -> crea la preferencia y devuelve init_point (link de pago)
 *   PaymentClient.get        -> estado REAL del pago (NUNCA confiar en el cuerpo del webhook)
 *   PaymentClient.search     -> pagos por external_reference (conciliación)
 * En modo "mock" no se llama a Mercado Pago (desarrollo sin credenciales).
 */
@Component
public class MercadoPagoClient {

    private final boolean mock;
    private final boolean tokenPrueba;
    private final String urlPublica;
    private final String frontend;
    private final String descriptor;
    private final int horasValidezPreferencia;

    public MercadoPagoClient(@Value("${mercadopago.modo}") String modo,
                             @Value("${mercadopago.access-token}") String token,
                             @Value("${mercadopago.url-publica}") String urlPublica,
                             @Value("${mercadopago.frontend-url}") String frontend,
                             @Value("${mercadopago.descriptor}") String descriptor,
                             @Value("${mercadopago.preferencia-horas}") int horasValidezPreferencia,
                             @Value("${mercadopago.timeout-conexion-ms}") int timeoutConexionMs,
                             @Value("${mercadopago.timeout-lectura-ms}") int timeoutLecturaMs) {
        this.mock = !"real".equalsIgnoreCase(modo);
        if (!mock && (token == null || token.isBlank())) {
            throw new IllegalStateException("MP_MODO=real requiere la variable de entorno MP_ACCESS_TOKEN");
        }
        this.tokenPrueba = token != null && token.startsWith("TEST-");
        this.urlPublica = urlPublica;
        this.frontend = frontend;
        this.descriptor = descriptor;
        this.horasValidezPreferencia = horasValidezPreferencia;
        if (!mock) {
            MercadoPagoConfig.setAccessToken(token);
            MercadoPagoConfig.setConnectionTimeout(timeoutConexionMs);
            MercadoPagoConfig.setSocketTimeout(timeoutLecturaMs);
        }
    }

    public boolean esMock() { return mock; }

    public record Preferencia(String id, String linkPago) {}

    /** Error de Mercado Pago ya traducido a excepción no chequeada. */
    public static class MercadoPagoException extends RuntimeException {
        private final int httpStatus;

        public MercadoPagoException(String mensaje, int httpStatus, Throwable causa) {
            super(mensaje, causa);
            this.httpStatus = httpStatus;
        }

        /** 0 si no hubo respuesta HTTP (timeout, red caída...). */
        public int getHttpStatus() { return httpStatus; }

        public boolean esNoEncontrado() { return httpStatus == 404; }
    }

    // ------------------------------------------------------------------ preferencia

    public Preferencia crearPreferencia(Pago p) {
        if (mock) {
            return new Preferencia("MOCK-PREF-" + UUID.randomUUID(),
                    frontend + "/pago-simulado?inscripcion=" + p.getInscripcionId());
        }
        String inscripcion = String.valueOf(p.getInscripcionId());

        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .id("CURSO-" + p.getCursoId())
                .title(p.getCursoNombre())
                .description("Inscripción al curso " + p.getCursoNombre())
                .categoryId("learnings")
                .quantity(1)
                .currencyId(p.getMoneda())
                .unitPrice(p.getMonto())
                .build();

        String[] nombre = separarNombre(p.getEstudianteNombre());
        PreferencePayerRequest payer = PreferencePayerRequest.builder()
                .name(nombre[0])
                .surname(nombre[1])
                .email(p.getEstudianteEmail())
                .build();

        PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                .success(frontend + "/pago/resultado?estado=aprobado&inscripcion=" + inscripcion)
                .failure(frontend + "/pago/resultado?estado=rechazado&inscripcion=" + inscripcion)
                .pending(frontend + "/pago/resultado?estado=pendiente&inscripcion=" + inscripcion)
                .build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("inscripcion_id", p.getInscripcionId());
        metadata.put("pago_id", p.getId().toString());
        metadata.put("curso_id", p.getCursoId());

        PreferenceRequest.PreferenceRequestBuilder req = PreferenceRequest.builder()
                .items(List.of(item))
                .payer(payer)
                .backUrls(backUrls)
                .externalReference(inscripcion)                       // así enlazamos pago <-> inscripción
                .notificationUrl(urlPublica + "/webhooks/mercadopago") // Webhook (apunta al gateway)
                .statementDescriptor(descriptor)
                .metadata(metadata)
                .expires(true)
                .expirationDateTo(OffsetDateTime.now(ZoneOffset.UTC).plusHours(horasValidezPreferencia));
        if (frontend.startsWith("https://")) {
            req.autoReturn("approved");   // Mercado Pago lo rechaza con back_urls http://localhost
        }

        // La misma clave en cada reintento => Mercado Pago no crea preferencias duplicadas
        MPRequestOptions opciones = MPRequestOptions.builder()
                .customHeaders(Map.of("x-idempotency-key", p.getId().toString()))
                .build();
        try {
            Preference r = new PreferenceClient().create(req.build(), opciones);
            String link = tokenPrueba && r.getSandboxInitPoint() != null ? r.getSandboxInitPoint() : r.getInitPoint();
            return new Preferencia(r.getId(), link);
        } catch (MPApiException e) {
            throw traducir("crear la preferencia", e);
        } catch (MPException e) {
            throw new MercadoPagoException("No se pudo crear la preferencia: " + e.getMessage(), 0, e);
        }
    }

    // ------------------------------------------------------------------ consulta de pagos

    public PagoMp consultarPago(String paymentId) {
        long id;
        try {
            id = Long.parseLong(paymentId);
        } catch (NumberFormatException e) {
            throw new MercadoPagoException("payment id no numérico: " + paymentId, 404, e);
        }
        try {
            return PagoMp.de(new PaymentClient().get(id));
        } catch (MPApiException e) {
            throw traducir("consultar el pago " + paymentId, e);
        } catch (MPException e) {
            throw new MercadoPagoException("No se pudo consultar el pago " + paymentId + ": " + e.getMessage(), 0, e);
        }
    }

    public List<PagoMp> buscarPorReferencia(Long inscripcionId) {
        Map<String, Object> filtros = new HashMap<>();
        filtros.put("external_reference", String.valueOf(inscripcionId));
        filtros.put("sort", "date_created");
        filtros.put("criteria", "desc");
        MPSearchRequest busqueda = MPSearchRequest.builder().offset(0).limit(30).filters(filtros).build();
        try {
            MPResultsResourcesPage<Payment> pagina = new PaymentClient().search(busqueda);
            return pagina.getResults().stream().map(PagoMp::de).toList();
        } catch (MPApiException e) {
            throw traducir("buscar pagos de la inscripción " + inscripcionId, e);
        } catch (MPException e) {
            throw new MercadoPagoException("No se pudo buscar pagos: " + e.getMessage(), 0, e);
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static MercadoPagoException traducir(String accion, MPApiException e) {
        int status = e.getStatusCode();
        String detalle = e.getApiResponse() == null ? "" : e.getApiResponse().getContent();
        return new MercadoPagoException("Mercado Pago respondió " + status + " al " + accion + ": " + detalle, status, e);
    }

    /** "Ana María Pérez López" -> ["Ana", "María Pérez López"]. */
    private static String[] separarNombre(String completo) {
        String limpio = completo == null ? "" : completo.trim();
        int i = limpio.indexOf(' ');
        return i < 0 ? new String[]{limpio, ""} : new String[]{limpio.substring(0, i), limpio.substring(i + 1).trim()};
    }
}
