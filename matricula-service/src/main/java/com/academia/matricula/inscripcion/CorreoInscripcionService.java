package com.academia.matricula.inscripcion;

import com.academia.matricula.curso.Curso;
import com.academia.matricula.curso.Modalidad;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;

/**
 * Correos que envia matricula-service:
 *  - INFORMACION: al inscribirse (datos del curso + link de pago).
 *  - INVITACION: al confirmarse el pago (enlace de la clase online o aula presencial).
 * Si el SMTP falla, la bandera queda en false y TareasProgramadas reintenta (max. 5 veces).
 */
@Service
public class CorreoInscripcionService {

    private static final Logger log = LoggerFactory.getLogger(CorreoInscripcionService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InscripcionRepository repo;
    private final JavaMailSender mail;
    private final TransactionTemplate tx;
    private final String remitente;
    private final String frontend;

    public CorreoInscripcionService(InscripcionRepository repo, JavaMailSender mail, PlatformTransactionManager tm,
                                    @Value("${app.correo-remitente}") String remitente,
                                    @Value("${app.frontend-url}") String frontend) {
        this.repo = repo;
        this.mail = mail;
        this.tx = new TransactionTemplate(tm);
        this.remitente = remitente;
        this.frontend = frontend;
    }

    @Async
    public void enviar(Long inscripcionId) {
        Inscripcion i = tx.execute(s -> repo.buscarCompleta(inscripcionId).orElse(null));
        if (i == null) return;
        boolean info = !i.isCorreoInfoEnviado();
        boolean invitacion = i.getEstado() == EstadoInscripcion.PAGADA && !i.isCorreoInvitacionEnviado();
        try {
            if (info) enviarHtml(i.getUsuario().getEmail(), "Tu inscripcion a " + i.getCurso().getNombre(), htmlInformacion(i));
            if (invitacion) enviarHtml(i.getUsuario().getEmail(), "Invitacion: " + i.getCurso().getNombre(), htmlInvitacion(i));
            tx.executeWithoutResult(s -> repo.findById(inscripcionId).ifPresent(x -> {
                if (info) x.setCorreoInfoEnviado(true);
                if (invitacion) x.setCorreoInvitacionEnviado(true);
            }));
        } catch (Exception e) {
            log.warn("Fallo correo de inscripcion {}: {}", inscripcionId, e.getMessage());
            tx.executeWithoutResult(s -> repo.findById(inscripcionId).ifPresent(x -> {
                x.setIntentosCorreo(x.getIntentosCorreo() + 1);
            }));
        }
    }

    private void enviarHtml(String para, String asunto, String html) throws Exception {
        MimeMessage m = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(m, false, "UTF-8");
        h.setFrom(remitente);
        h.setTo(para);
        h.setSubject(asunto);
        h.setText(html, true);
        mail.send(m);
    }

    private String htmlInformacion(Inscripcion i) {
        Curso c = i.getCurso();
        String pago = i.getEstado() == EstadoInscripcion.PENDIENTE_PAGO && i.getLinkPago() != null
                ? "<p><a href='" + esc(i.getLinkPago()) + "' style='background:#009ee3;color:#fff;padding:10px 18px;border-radius:6px;text-decoration:none'>Pagar con Mercado Pago</a></p>"
                : "<p>Puedes completar tu pago desde <a href='" + esc(frontend) + "/mis-inscripciones'>Mis inscripciones</a>.</p>";
        return plantilla("Hola " + esc(i.getUsuario().getNombre()) + ",",
                "<p>Registramos tu inscripcion al curso <b>" + esc(c.getNombre()) + "</b>.</p>"
                + detalleCurso(c, false)
                + "<p><b>Monto:</b> " + i.getMoneda() + " " + i.getMonto() + "</p>" + pago
                + "<p>Te avisaremos por WhatsApp y correo cuando confirmemos tu pago.</p>");
    }

    private String htmlInvitacion(Inscripcion i) {
        Curso c = i.getCurso();
        return plantilla("Bienvenido(a), " + esc(i.getUsuario().getNombre()) + "!",
                "<p>Tu pago fue confirmado y tu matricula en <b>" + esc(c.getNombre()) + "</b> esta lista.</p>"
                + detalleCurso(c, true)
                + "<p>Tu comprobante de pago llegara en un correo aparte.</p>");
    }

    private String detalleCurso(Curso c, boolean conUbicacion) {
        String ubicacion = "";
        if (conUbicacion && c.getUbicacion() != null) {
            ubicacion = c.getModalidad() == Modalidad.ONLINE
                    ? "<li><b>Enlace de clase:</b> <a href='" + esc(c.getUbicacion()) + "'>" + esc(c.getUbicacion()) + "</a></li>"
                    : "<li><b>Lugar:</b> " + esc(c.getUbicacion()) + "</li>";
        }
        return "<ul><li><b>Inicio:</b> " + c.getFechaInicio().format(FECHA) + "</li>"
                + "<li><b>Horario:</b> " + esc(c.getHorario()) + "</li>"
                + "<li><b>Modalidad:</b> " + c.getModalidad() + "</li>" + ubicacion + "</ul>";
    }

    private String plantilla(String saludo, String cuerpo) {
        return "<div style='font-family:Arial,sans-serif;max-width:560px;margin:auto;color:#222'>"
                + "<h2 style='color:#1f3a93'>Academia</h2><p>" + saludo + "</p>" + cuerpo
                + "<hr><small>Consultas por WhatsApp: escribe <b>MENU</b> a nuestro numero.</small></div>";
    }

    private static String esc(String s) { return s == null ? "" : HtmlUtils.htmlEscape(s); }
}
