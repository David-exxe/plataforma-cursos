package com.academia.comprobantes.comun;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Envio de correo con dos proveedores intercambiables (variable CORREO_PROVEEDOR):
 *   smtp  -> JavaMailSender (Mailpit en local, Gmail/Outlook con contrasena de aplicacion).
 *   brevo -> API HTTP de Brevo (https://api.brevo.com/v3/smtp/email).
 * Render (plan free) BLOQUEA los puertos SMTP 25/465/587, por eso en la nube se usa "brevo",
 * que viaja por HTTPS (puerto 443). Si el envio falla se lanza excepcion y el llamador reintenta.
 */
@Component
public class CorreoSender {

    private static final Logger log = LoggerFactory.getLogger(CorreoSender.class);
    private static final Pattern NOMBRE_EMAIL = Pattern.compile("^\\s*(.*?)\\s*<\\s*([^>]+)\\s*>\\s*$");

    private final String proveedor;
    private final String remitente;
    private final ObjectProvider<JavaMailSender> smtp;
    private final RestClient brevo;
    private final boolean brevoConfigurado;

    public CorreoSender(@Value("${correo.proveedor:smtp}") String proveedor,
                        @Value("${correo.remitente}") String remitente,
                        @Value("${correo.brevo-api-key:}") String brevoApiKey,
                        ObjectProvider<JavaMailSender> smtp) {
        this.proveedor = proveedor.trim().toLowerCase();
        this.remitente = remitente;
        this.smtp = smtp;
        this.brevoConfigurado = !brevoApiKey.isBlank();
        if (this.proveedor.equals("brevo") && !brevoConfigurado) {
            // No se detiene el servicio: los correos quedan en ERROR y se reintentan cuando se configure la clave
            log.warn("CORREO_PROVEEDOR=brevo pero BREVO_API_KEY esta vacia: los correos no saldran hasta configurarla");
        }
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(15000);
        this.brevo = RestClient.builder().baseUrl("https://api.brevo.com/v3").requestFactory(f)
                .defaultHeader("api-key", brevoApiKey)
                .defaultHeader("accept", "application/json").build();
        log.info("Proveedor de correo: {}", this.proveedor);
    }

    public void enviar(String para, String asunto, String html) throws Exception {
        enviar(para, asunto, html, null, null);
    }

    public void enviar(String para, String asunto, String html, String adjuntoNombre, byte[] adjunto) throws Exception {
        if (proveedor.equals("brevo")) {
            enviarBrevo(para, asunto, html, adjuntoNombre, adjunto);
        } else {
            enviarSmtp(para, asunto, html, adjuntoNombre, adjunto);
        }
    }

    private void enviarSmtp(String para, String asunto, String html, String adjuntoNombre, byte[] adjunto) throws Exception {
        JavaMailSender mail = smtp.getIfAvailable();
        if (mail == null) throw new IllegalStateException("SMTP no configurado (spring.mail.host)");
        MimeMessage m = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(m, adjunto != null, "UTF-8");
        h.setFrom(remitente);
        h.setTo(para);
        h.setSubject(asunto);
        h.setText(html, true);
        if (adjunto != null) h.addAttachment(adjuntoNombre, new ByteArrayResource(adjunto), "application/pdf");
        mail.send(m);
    }

    private void enviarBrevo(String para, String asunto, String html, String adjuntoNombre, byte[] adjunto) {
        if (!brevoConfigurado) throw new IllegalStateException("BREVO_API_KEY no configurada");
        Matcher mr = NOMBRE_EMAIL.matcher(remitente);
        Map<String, Object> sender = mr.matches()
                ? Map.of("name", mr.group(1).isBlank() ? "Academia" : mr.group(1), "email", mr.group(2))
                : Map.of("name", "Academia", "email", remitente.trim());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sender", sender);
        body.put("to", List.of(Map.of("email", para)));
        body.put("subject", asunto);
        body.put("htmlContent", html);
        if (adjunto != null) {
            body.put("attachment", List.of(Map.of("name", adjuntoNombre, "content", Base64.getEncoder().encodeToString(adjunto))));
        }
        brevo.post().uri("/smtp/email").body(body).retrieve().toBodilessEntity();
    }
}
