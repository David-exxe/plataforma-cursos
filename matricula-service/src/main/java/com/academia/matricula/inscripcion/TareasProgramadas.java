package com.academia.matricula.inscripcion;

import com.academia.matricula.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final InscripcionRepository repo;
    private final OutboxService outbox;
    private final CorreoInscripcionService correo;
    private final int horasVencimiento;

    public TareasProgramadas(InscripcionRepository repo, OutboxService outbox, CorreoInscripcionService correo,
                             @Value("${app.inscripcion-horas-vencimiento}") int horasVencimiento) {
        this.repo = repo;
        this.outbox = outbox;
        this.correo = correo;
        this.horasVencimiento = horasVencimiento;
    }

    /** Todos los dias 9:00 am (hora Lima): recordatorio a quienes empiezan manana. */
    @Scheduled(cron = "0 0 9 * * *", zone = "America/Lima")
    @Transactional
    public int generarRecordatorios() {
        List<Inscripcion> lista = repo.paraRecordatorio(LocalDate.now(LIMA).plusDays(1));
        for (Inscripcion i : lista) {
            outbox.registrar("curso.recordatorio", Eventos.recordatorio(i));
            i.setRecordatorioEnviado(true);
        }
        if (!lista.isEmpty()) log.info("{} recordatorios de inicio de clases generados", lista.size());
        return lista.size();
    }

    /** Cada hora: libera el cupo de inscripciones que no pagaron en N horas. */
    @Scheduled(cron = "0 15 * * * *", zone = "America/Lima")
    @Transactional
    public void vencerPendientes() {
        List<Inscripcion> lista = repo.pendientesVencidas(Instant.now().minus(horasVencimiento, ChronoUnit.HOURS));
        lista.forEach(i -> i.setEstado(EstadoInscripcion.VENCIDA));
        if (!lista.isEmpty()) log.info("{} inscripciones vencidas por falta de pago", lista.size());
    }

    /** Cada 10 min: reintenta correos de informacion/invitacion que fallaron. */
    @Scheduled(fixedDelay = 600_000, initialDelay = 120_000)
    public void reintentarCorreos() {
        repo.conCorreoPendiente(Instant.now().minus(2, ChronoUnit.MINUTES)).forEach(correo::enviar);
    }
}
