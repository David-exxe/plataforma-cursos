package com.academia.gateway.seguridad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bloqueo de IPs en tres niveles:
 *  1. Lista negra fija (configuracion).
 *  2. Rate limiting: mas de N peticiones por minuto -> bloqueo temporal.
 *  3. Fuerza bruta: mas de N logins/2FA fallidos en la ventana -> bloqueo temporal.
 * Ademas un ADMIN puede bloquear/desbloquear manualmente (IpAdminController).
 *
 * Nota: el estado esta en memoria. Si se levantan varias instancias del gateway,
 * mover estos mapas a Redis (mismo algoritmo, otra estructura).
 */
@Service
public class IpBloqueoService {

    private static final Logger log = LoggerFactory.getLogger(IpBloqueoService.class);

    private final IpBloqueoProperties props;
    private final Map<String, Instant> bloqueadas = new ConcurrentHashMap<>();
    private final Map<String, String> motivos = new ConcurrentHashMap<>();
    private final Map<String, Ventana> peticiones = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Instant>> fallidos = new ConcurrentHashMap<>();

    public IpBloqueoService(IpBloqueoProperties props) {
        this.props = props;
    }

    public boolean enListaBlanca(String ip) {
        return props.listaBlanca().stream().anyMatch(ip::startsWith);
    }

    /** Devuelve el instante hasta el que esta bloqueada, o null si puede pasar. */
    public Instant bloqueadaHasta(String ip) {
        if (enListaBlanca(ip)) return null;
        if (props.listaNegra().stream().anyMatch(ip::startsWith)) return Instant.MAX;
        Instant hasta = bloqueadas.get(ip);
        if (hasta == null) return null;
        if (Instant.now().isAfter(hasta)) {
            bloqueadas.remove(ip);
            motivos.remove(ip);
            return null;
        }
        return hasta;
    }

    /** Cuenta una peticion; si supera el limite por minuto bloquea la IP. */
    public boolean registrarPeticionYVerificarLimite(String ip) {
        if (enListaBlanca(ip)) return true;
        long minutoActual = Instant.now().getEpochSecond() / 60;
        Ventana v = peticiones.compute(ip, (k, actual) ->
                (actual == null || actual.minuto != minutoActual) ? new Ventana(minutoActual) : actual);
        if (v.contador.incrementAndGet() > props.maxPeticionesPorMinuto()) {
            bloquear(ip, props.minutosBloqueo(), "Exceso de peticiones (" + props.maxPeticionesPorMinuto() + "/min)");
            return false;
        }
        return true;
    }

    public void registrarLoginFallido(String ip) {
        if (enListaBlanca(ip)) return;
        Instant ahora = Instant.now();
        Instant limite = ahora.minus(props.ventanaLoginsFallidosMinutos(), ChronoUnit.MINUTES);
        ConcurrentLinkedDeque<Instant> cola = fallidos.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        cola.addLast(ahora);
        while (!cola.isEmpty() && cola.peekFirst().isBefore(limite)) cola.pollFirst();
        if (cola.size() >= props.maxLoginsFallidos()) {
            bloquear(ip, props.minutosBloqueo(), "Posible fuerza bruta: " + cola.size() + " logins fallidos");
            cola.clear();
        }
    }

    public void bloquear(String ip, int minutos, String motivo) {
        bloqueadas.put(ip, Instant.now().plus(minutos, ChronoUnit.MINUTES));
        motivos.put(ip, motivo);
        log.warn("IP BLOQUEADA {} por {} min. Motivo: {}", ip, minutos, motivo);
    }

    public void desbloquear(String ip) {
        bloqueadas.remove(ip);
        motivos.remove(ip);
        fallidos.remove(ip);
        log.info("IP desbloqueada manualmente: {}", ip);
    }

    public Map<String, Object> listar() {
        Map<String, Object> r = new ConcurrentHashMap<>();
        bloqueadas.forEach((ip, hasta) -> r.put(ip, Map.of("hasta", hasta.toString(), "motivo", motivos.getOrDefault(ip, ""))));
        props.listaNegra().forEach(ip -> r.put(ip, Map.of("hasta", "permanente", "motivo", "Lista negra")));
        return r;
    }

    /** Limpieza periodica para que los mapas no crezcan sin limite. */
    @Scheduled(fixedDelay = 300_000)
    public void limpiar() {
        long minutoActual = Instant.now().getEpochSecond() / 60;
        peticiones.entrySet().removeIf(e -> e.getValue().minuto < minutoActual - 1);
        bloqueadas.entrySet().removeIf(e -> Instant.now().isAfter(e.getValue()));
        Instant limite = Instant.now().minus(props.ventanaLoginsFallidosMinutos(), ChronoUnit.MINUTES);
        fallidos.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().isBefore(limite));
    }

    private static final class Ventana {
        final long minuto;
        final AtomicInteger contador = new AtomicInteger();
        Ventana(long minuto) { this.minuto = minuto; }
    }
}
