package com.academia.matricula.inscripcion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Llamada SINCRONA a pagos-service: el estudiante necesita el link de pago en la respuesta.
 * Timeouts cortos: si pagos-service esta caido, la inscripcion se guarda igual y el link
 * se puede regenerar despues (GET /api/inscripciones/{id}/link-pago).
 */
@Component
public class PagosClient {

    private final RestClient client;

    public PagosClient(@Value("${app.pagos-url}") String url, @Value("${seguridad.clave-interna}") String clave) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(8000);
        this.client = RestClient.builder().baseUrl(url).requestFactory(f)
                .defaultHeader("X-Internal-Key", clave).build();
    }

    public record CrearPreferenciaRequest(Long inscripcionId, Long usuarioId, String estudianteNombre,
                                          String estudianteEmail, String estudianteTelefono, String estudianteDocumento,
                                          Long cursoId, String cursoNombre, BigDecimal monto, String moneda) {}

    public record PreferenciaResponse(String pagoId, String preferenceId, String linkPago, String estado) {}

    public PreferenciaResponse crearPreferencia(CrearPreferenciaRequest r) {
        return client.post().uri("/internal/pagos/preferencias").body(r).retrieve().body(PreferenciaResponse.class);
    }
}
