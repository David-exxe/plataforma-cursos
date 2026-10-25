package com.academia.comprobantes.comprobante;

import com.academia.comprobantes.config.RabbitConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CASO CLAVE DEL ENUNCIADO: "Mercado Pago confirmo el pago pero el correo con el comprobante no salio".
 *  1. El comprobante (numero + PDF) se genera y GUARDA primero. El pago nunca se pierde.
 *  2. El envio por correo es un paso aparte con estado propio: PENDIENTE -> ENVIADO | ERROR -> FALLIDO.
 *  3. Si el SMTP falla: ERROR + reintento con espera creciente (1, 5, 15, 60 min).
 *  4. Si agota los intentos: FALLIDO + evento comprobante.fallido -> WhatsApp de alerta al admin,
 *     que puede reenviarlo desde el panel. El estudiante tambien puede pedirlo al bot (opcion 4)
 *     y descargarlo desde la web (GET /api/comprobantes/{id}/pdf).
 */
@Service
public class ComprobanteService {

    private static final Logger log = LoggerFactory.getLogger(ComprobanteService.class);
    private static final long[] ESPERAS_MIN = {1, 5, 15, 60, 240};

    private final ComprobanteRepository repo;
    private final ComprobantePdfRepository pdfs;
    private final PdfComprobanteGenerator generador;
    private final JavaMailSender mail;
    private final RabbitTemplate rabbit;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final String serie;
    private final String remitente;
    private final String empresa;
    private final int maxIntentos;

    public ComprobanteService(ComprobanteRepository repo, ComprobantePdfRepository pdfs, PdfComprobanteGenerator generador,
                              JavaMailSender mail, RabbitTemplate rabbit, ObjectMapper mapper, PlatformTransactionManager tm,
                              @Value("${comprobante.serie}") String serie,
                              @Value("${comprobante.remitente}") String remitente,
                              @Value("${comprobante.empresa}") String empresa,
                              @Value("${comprobante.max-intentos}") int maxIntentos) {
        this.repo = repo;
        this.pdfs = pdfs;
        this.generador = generador;
        this.mail = mail;
        this.rabbit = rabbit;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(tm);
        this.serie = serie;
        this.remitente = remitente;
        this.empresa = empresa;
        this.maxIntentos = maxIntentos;
    }

    /** IDEMPOTENTE: si el evento pago.aprobado llega 2 veces, devuelve el comprobante ya emitido. */
    public Long emitir(UUID eventoId, JsonNode d) {
        Long inscripcionId = d.path("inscripcionId").asLong();
        try {
            return tx.execute(s -> {
                var existente = repo.findByInscripcionId(inscripcionId);
                if (existente.isPresent()) return existente.get().getId();

                JsonNode est = d.path("estudiante");
                Comprobante c = new Comprobante();
                long correlativo = repo.siguienteCorrelativo();
                c.setSerie(serie);
                c.setCorrelativo(correlativo);
                c.setNumero(serie + "-" + String.format("%08d", correlativo));
                c.setInscripcionId(inscripcionId);
                c.setEventoId(eventoId);
                c.setPagoId(d.path("pagoId").asText(null));
                c.setMpPaymentId(d.path("mpPaymentId").asText(null));
                c.setUsuarioId(est.path("usuarioId").asLong());
                c.setEstudianteNombre(est.path("nombre").asText());
                c.setEmail(est.path("email").asText());
                c.setTelefono(est.path("telefono").asText(null));
                c.setDocumento(est.path("documento").asText(null));
                c.setCursoId(d.path("curso").path("id").asLong());
                c.setCursoNombre(d.path("curso").path("nombre").asText());
                c.setMonto(d.path("monto").decimalValue());
                c.setMoneda(d.path("moneda").asText("PEN"));
                c.setMetodoPago(d.path("metodoPago").asText(null));
                String f = d.path("fechaAprobacion").asText(null);
                c.setFechaPago(f == null || f.isBlank() ? Instant.now() : Instant.parse(f));
                repo.save(c);
                pdfs.save(new ComprobantePdf(c.getId(), generador.generar(c)));
                log.info("Comprobante {} emitido para inscripcion {}", c.getNumero(), inscripcionId);
                return c.getId();
            });
        } catch (DataIntegrityViolationException carrera) {
            return tx.execute(s -> repo.findByInscripcionId(inscripcionId).orElseThrow().getId());
        }
    }

    /** Intenta mandar el correo. Nunca lanza excepcion: el resultado queda en estado_envio. */
    public boolean intentarEnvio(Long id) {
        Comprobante c = tx.execute(s -> repo.findById(id).orElse(null));
        if (c == null || "ENVIADO".equals(c.getEstadoEnvio()) || "FALLIDO".equals(c.getEstadoEnvio())) return false;
        try {
            byte[] pdf = tx.execute(s -> pdfs.findById(id).map(ComprobantePdf::getContenido).orElseThrow());
            enviarCorreo(c, pdf);
            tx.executeWithoutResult(s -> repo.findById(id).ifPresent(x -> {
                x.setEstadoEnvio("ENVIADO");
                x.setEnviadoEn(Instant.now());
                x.setIntentosEnvio(x.getIntentosEnvio() + 1);
                x.setUltimoError(null);
            }));
            publicar("comprobante.enviado", c, null);
            log.info("Comprobante {} enviado a {}", c.getNumero(), c.getEmail());
            return true;
        } catch (Exception e) {
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().substring(0, Math.min(490, e.getMessage().length()));
            Comprobante actualizado = tx.execute(s -> {
                Comprobante x = repo.findById(id).orElseThrow();
                int n = x.getIntentosEnvio() + 1;
                x.setIntentosEnvio(n);
                x.setUltimoError(error);
                if (n >= maxIntentos) {
                    x.setEstadoEnvio("FALLIDO");
                } else {
                    x.setEstadoEnvio("ERROR");
                    x.setProximoIntento(Instant.now().plus(ESPERAS_MIN[Math.min(n - 1, ESPERAS_MIN.length - 1)], ChronoUnit.MINUTES));
                }
                return x;
            });
            if ("FALLIDO".equals(actualizado.getEstadoEnvio())) {
                log.error("Comprobante {} FALLIDO tras {} intentos: {}", c.getNumero(), actualizado.getIntentosEnvio(), error);
                publicar("comprobante.fallido", actualizado, error);
            } else {
                log.warn("Correo de comprobante {} fallo (intento {}). Proximo intento {}", c.getNumero(),
                        actualizado.getIntentosEnvio(), actualizado.getProximoIntento());
            }
            return false;
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void reintentar() {
        repo.paraEnviar(Instant.now()).forEach(this::intentarEnvio);
    }

    /** Reenvio manual (admin o bot). Vuelve a PENDIENTE y reintenta en el momento. */
    public boolean reenviar(Long id) {
        tx.executeWithoutResult(s -> repo.findById(id).ifPresent(c -> {
            c.setEstadoEnvio("PENDIENTE");
            c.setIntentosEnvio(0);
            c.setProximoIntento(Instant.now());
        }));
        return intentarEnvio(id);
    }

    private void enviarCorreo(Comprobante c, byte[] pdf) throws Exception {
        MimeMessage m = mail.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(m, true, "UTF-8");
        h.setFrom(remitente);
        h.setTo(c.getEmail());
        h.setSubject("Comprobante de pago " + c.getNumero() + " - " + c.getCursoNombre());
        h.setText("<div style='font-family:Arial,sans-serif;max-width:560px'>"
                + "<p>Hola " + HtmlUtils.htmlEscape(c.getEstudianteNombre()) + ",</p>"
                + "<p>Gracias por tu pago. Adjuntamos tu comprobante <b>" + c.getNumero() + "</b> por el curso <b>"
                + HtmlUtils.htmlEscape(c.getCursoNombre()) + "</b>.</p>"
                + "<p><b>Total:</b> " + c.getMoneda() + " " + c.getMonto() + "</p>"
                + "<p>Saludos,<br>" + HtmlUtils.htmlEscape(empresa) + "</p></div>", true);
        h.addAttachment(c.getNumero() + ".pdf", new ByteArrayResource(pdf), "application/pdf");
        mail.send(m);
    }

    private void publicar(String tipo, Comprobante c, String error) {
        try {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("comprobanteId", c.getId());
            datos.put("numero", c.getNumero());
            datos.put("inscripcionId", c.getInscripcionId());
            datos.put("nombre", c.getEstudianteNombre());
            datos.put("email", c.getEmail());
            datos.put("telefono", c.getTelefono());
            datos.put("cursoNombre", c.getCursoNombre());
            if (error != null) {
                datos.put("intentos", c.getIntentosEnvio());
                datos.put("ultimoError", error);
            }
            UUID eventoId = UUID.randomUUID();
            Map<String, Object> sobre = new LinkedHashMap<>();
            sobre.put("eventoId", eventoId.toString());
            sobre.put("tipo", tipo);
            sobre.put("version", 1);
            sobre.put("origen", "comprobantes-service");
            sobre.put("ocurridoEn", OffsetDateTime.now().toString());
            sobre.put("datos", datos);
            rabbit.send(RabbitConfig.EXCHANGE, tipo, MessageBuilder
                    .withBody(mapper.writeValueAsString(sobre).getBytes(StandardCharsets.UTF_8))
                    .setContentType("application/json").setMessageId(eventoId.toString())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build());
        } catch (Exception e) {
            // El aviso por WhatsApp es secundario: el comprobante ya esta guardado y su estado es la fuente de verdad
            log.warn("No se pudo publicar {} del comprobante {}: {}", tipo, c.getNumero(), e.getMessage());
        }
    }
}
