package com.academia.notificaciones.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Envia el correo de confirmacion de pago.
 *  - Idempotente: un mismo eventoId solo genera un correo (tabla correos_enviados).
 *  - Si el envio falla lanza la excepcion: el listener la propaga y RabbitMQ reintenta
 *    (spring.rabbitmq.listener.simple.retry) y, agotados los intentos, el mensaje va a la DLQ.
 */
@Service
public class CorreoService {

    private static final Logger log = LoggerFactory.getLogger(CorreoService.class);
    private static final String TIPO = "PAGO_CONFIRMADO";
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final JavaMailSender mail;
    private final PlantillaCorreo plantilla;
    private final CorreoEnviadoRepository repo;
    private final String remitente;
    private final String empresa;
    private final String frontend;

    public CorreoService(JavaMailSender mail,
                         PlantillaCorreo plantilla,
                         CorreoEnviadoRepository repo,
                         @Value("${correo.remitente}") String remitente,
                         @Value("${correo.empresa}") String empresa,
                         @Value("${app.frontend-url}") String frontend) {
        this.mail = mail;
        this.plantilla = plantilla;
        this.repo = repo;
        this.remitente = remitente;
        this.empresa = empresa;
        this.frontend = frontend;
    }

    public void enviarConfirmacionPago(UUID eventoId, ConfirmacionPago c) throws MessagingException {
        if (c.email() == null || c.email().isBlank()) {
            log.warn("Evento {} sin email del estudiante: no se envia el correo", eventoId);
            return;                                   // reintentar no lo arreglaria
        }
        if (repo.existsByEventoIdAndTipo(eventoId, TIPO)) {
            log.info("Evento {} ya tenia correo de confirmacion enviado", eventoId);
            return;
        }

        String monto = simbolo(c.moneda()) + c.monto().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String fecha = ZonedDateTime.ofInstant(c.fecha(), LIMA).format(FECHA);
        String asunto = sinSaltos("Pago confirmado - " + c.curso());

        String html = plantilla.pagoConfirmado(Map.of(
                "empresa", empresa,
                "nombre", primerNombre(c.nombre()),
                "curso", c.curso(),
                "monto", monto,
                "operacion", c.operacionId(),
                "fecha", fecha,
                "enlace", frontend + "/mis-inscripciones",
                "anio", String.valueOf(ZonedDateTime.now(LIMA).getYear())));
        String texto = "Hola " + primerNombre(c.nombre()) + ", recibimos tu pago de " + monto + " por el curso "
                + c.curso() + ". N.º de operación: " + c.operacionId() + ". Fecha: " + fecha
                + ". Tu inscripción está confirmada. — " + empresa;

        MimeMessage m = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(m, "UTF-8");
        h.setFrom(remitente);
        h.setTo(c.email().trim());
        h.setSubject(asunto);
        h.setText(texto, html);                       // texto plano + HTML (mejor entregabilidad)
        mail.send(m);

        try {
            CorreoEnviado r = new CorreoEnviado();
            r.setEventoId(eventoId);
            r.setTipo(TIPO);
            r.setDestinatario(c.email().trim());
            r.setAsunto(asunto);
            repo.save(r);
        } catch (DataIntegrityViolationException duplicado) {
            log.warn("Evento {}: otro hilo ya registro el correo", eventoId);
        }
        log.info("Correo de confirmacion enviado a {} (evento {})", c.email(), eventoId);
    }

    private static String simbolo(String moneda) {
        return switch (moneda == null ? "" : moneda.toUpperCase()) {
            case "PEN" -> "S/ ";
            case "USD" -> "US$ ";
            default -> (moneda == null ? "" : moneda) + " ";
        };
    }

    private static String primerNombre(String n) {
        return n == null || n.isBlank() ? "estudiante" : n.trim().split("\\s+")[0];
    }

    private static String sinSaltos(String s) {
        return s.replaceAll("[\\r\\n]+", " ");
    }
}
