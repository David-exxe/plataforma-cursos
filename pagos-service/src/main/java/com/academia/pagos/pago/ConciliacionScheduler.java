package com.academia.pagos.pago;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Red de seguridad: si un webhook se perdio (servidor caido, ngrok cerrado, etc.)
 * cada 15 min se pregunta a Mercado Pago por los pagos que siguen pendientes.
 */
@Component
public class ConciliacionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionScheduler.class);
    private final PagoRepository repo;
    private final MercadoPagoClient mp;
    private final PagoService servicio;

    public ConciliacionScheduler(PagoRepository repo, MercadoPagoClient mp, PagoService servicio) {
        this.repo = repo;
        this.mp = mp;
        this.servicio = servicio;
    }

    @Scheduled(fixedDelay = 900_000, initialDelay = 60_000)
    public void conciliar() {
        if (mp.esMock()) return;
        Instant ahora = Instant.now();
        for (Pago p : repo.paraConciliar(ahora.minus(3, ChronoUnit.DAYS), ahora.minus(10, ChronoUnit.MINUTES))) {
            try {
                JsonNode r = mp.buscarPorReferencia(p.getInscripcionId());
                for (JsonNode pagoMp : r.path("results")) servicio.aplicar(pagoMp);
            } catch (Exception e) {
                log.warn("No se pudo conciliar inscripcion {}: {}", p.getInscripcionId(), e.getMessage());
            }
        }
    }
}
