package com.academia.notificaciones.bot;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** El bot consulta a matricula y comprobantes por sus APIs internas (X-Internal-Key). */
@Component
public class ServiciosInternosClient {

    private final RestClient matricula;
    private final RestClient comprobantes;

    public ServiciosInternosClient(@Value("${app.matricula-url}") String matriculaUrl,
                                   @Value("${app.comprobantes-url}") String comprobantesUrl,
                                   @Value("${seguridad.clave-interna}") String clave) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(2000);
        f.setReadTimeout(5000);
        this.matricula = RestClient.builder().baseUrl(matriculaUrl).requestFactory(f).defaultHeader("X-Internal-Key", clave).build();
        this.comprobantes = RestClient.builder().baseUrl(comprobantesUrl).requestFactory(f).defaultHeader("X-Internal-Key", clave).build();
    }

    public JsonNode estudiante(String telefono) {
        return matricula.get().uri(u -> u.path("/internal/consultas/estudiante").queryParam("telefono", telefono).build())
                .retrieve().body(JsonNode.class);
    }

    public JsonNode cursos() {
        return matricula.get().uri("/internal/cursos").retrieve().body(JsonNode.class);
    }

    public JsonNode reenviarComprobante(String telefono) {
        return comprobantes.post().uri("/internal/comprobantes/reenviar-por-telefono/{tel}", telefono)
                .retrieve().body(JsonNode.class);
    }
}
