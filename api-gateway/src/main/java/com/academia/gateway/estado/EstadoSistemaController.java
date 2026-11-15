package com.academia.gateway.estado;

import com.academia.gateway.seguridad.ClienteIpResolver;
import com.academia.gateway.seguridad.IpBloqueoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * GET /api/estado: consulta en PARALELO el /actuator/health de cada microservicio y devuelve
 * su estado, latencia y componentes (base de datos, RabbitMQ). Lo usa la pagina "Estado del sistema".
 * Un servicio caido no bloquea la respuesta: cada consulta tiene su propio timeout.
 * En Render free esta consulta ademas "despierta" a los servicios dormidos.
 */
@RestController
public class EstadoSistemaController {

    private record Servicio(String id, String nombre, String descripcion, String url) {}

    private final WebClient web = WebClient.builder().build();
    private final List<Servicio> servicios;
    private final ClienteIpResolver ipResolver;
    private final IpBloqueoService bloqueos;

    public EstadoSistemaController(@Value("${servicios.matricula}") String matricula,
                                   @Value("${servicios.pagos}") String pagos,
                                   @Value("${servicios.notificaciones}") String notificaciones,
                                   @Value("${servicios.comprobantes}") String comprobantes,
                                   ClienteIpResolver ipResolver, IpBloqueoService bloqueos) {
        this.servicios = List.of(
                new Servicio("matricula", "Matricula", "Cuentas, 2FA, cursos e inscripciones", matricula),
                new Servicio("pagos", "Pagos", "Mercado Pago, webhooks y conciliacion", pagos),
                new Servicio("notificaciones", "Notificaciones", "WhatsApp y chatbot", notificaciones),
                new Servicio("comprobantes", "Comprobantes", "PDF y envio por correo", comprobantes));
        this.ipResolver = ipResolver;
        this.bloqueos = bloqueos;
    }

    @GetMapping("/api/estado")
    public Mono<Map<String, Object>> estado() {
        return Flux.fromIterable(servicios)
                .flatMap(this::consultar)
                .collectList()
                .map(lista -> {
                    lista.sort(Comparator.comparingInt(m -> idx((String) m.get("id"))));
                    long arriba = lista.stream().filter(m -> "UP".equals(m.get("estado"))).count();
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("consultadoEn", Instant.now().toString());
                    r.put("gateway", "UP");
                    r.put("serviciosArriba", arriba);
                    r.put("serviciosTotal", lista.size());
                    r.put("servicios", lista);
                    return r;
                });
    }

    @GetMapping("/api/mi-ip")
    public Map<String, Object> miIp(ServerWebExchange exchange) {
        String ip = ipResolver.resolver(exchange);
        return Map.of("ip", ip, "bloqueada", bloqueos.bloqueadaHasta(ip) != null);
    }

    private Mono<Map<String, Object>> consultar(Servicio s) {
        long inicio = System.nanoTime();
        return web.get().uri(s.url() + "/actuator/health")
                .exchangeToMono(resp -> resp.bodyToMono(JsonNode.class)
                        .map(body -> resultado(s, body, inicio))
                        .switchIfEmpty(Mono.fromSupplier(() -> sinRespuesta(s, "HTTP " + resp.statusCode().value(), inicio))))
                .timeout(Duration.ofSeconds(12))
                .onErrorResume(e -> Mono.just(sinRespuesta(s, e.getClass().getSimpleName(), inicio)));
    }

    private Map<String, Object> resultado(Servicio s, JsonNode body, long inicio) {
        Map<String, Object> m = base(s, inicio);
        m.put("estado", body.path("status").asText("DESCONOCIDO"));
        Map<String, String> componentes = new LinkedHashMap<>();
        body.path("components").fields().forEachRemaining(e -> {
            if (!e.getKey().equals("livenessState") && !e.getKey().equals("readinessState") && !e.getKey().equals("ping")) {
                componentes.put(e.getKey(), e.getValue().path("status").asText());
            }
        });
        m.put("componentes", componentes);
        return m;
    }

    private Map<String, Object> sinRespuesta(Servicio s, String motivo, long inicio) {
        Map<String, Object> m = base(s, inicio);
        m.put("estado", "SIN_RESPUESTA");
        m.put("motivo", motivo);
        m.put("componentes", Map.of());
        return m;
    }

    private Map<String, Object> base(Servicio s, long inicio) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.id());
        m.put("nombre", s.nombre());
        m.put("descripcion", s.descripcion());
        m.put("latenciaMs", (System.nanoTime() - inicio) / 1_000_000);
        return m;
    }

    private int idx(String id) {
        for (int i = 0; i < servicios.size(); i++) if (servicios.get(i).id().equals(id)) return i;
        return 99;
    }
}
