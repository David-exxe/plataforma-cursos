package com.academia.notificaciones.bot;

import com.academia.notificaciones.notificacion.Mensajes;
import com.academia.notificaciones.notificacion.NotificacionService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;

/**
 * Bot de WhatsApp: responde las consultas frecuentes que antes atendia el personal a mano.
 * Funciona por menu numerico o palabras clave. Si no entiende -> deriva a un asesor humano.
 */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);

    static final String MENU = """
            Hola 👋 Soy el asistente de la Academia. Escribe el *numero* de tu consulta:

            1️⃣ Cursos disponibles y precios
            2️⃣ Estado de mi inscripcion / pago
            3️⃣ Horario, fecha de inicio y enlace de clase
            4️⃣ Reenviar mi comprobante de pago
            5️⃣ Hablar con un asesor""";

    private final ServiciosInternosClient internos;
    private final NotificacionService notificaciones;
    private final String telefonoAdmin;
    private final String frontend;

    public BotService(ServiciosInternosClient internos, NotificacionService notificaciones,
                      @Value("${whatsapp.telefono-admin}") String telefonoAdmin,
                      @Value("${app.frontend-url}") String frontend) {
        this.internos = internos;
        this.notificaciones = notificaciones;
        this.telefonoAdmin = telefonoAdmin;
        this.frontend = frontend;
    }

    public String responder(String telefono, String texto) {
        String t = normalizar(texto);
        try {
            if (t.equals("1") || t.contains("curso") || t.contains("precio")) return cursos();
            if (t.equals("2") || t.contains("estado") || t.contains("pago") || t.contains("inscrip")) return estado(telefono);
            if (t.equals("3") || t.contains("horario") || t.contains("inicio") || t.contains("enlace") || t.contains("link")) return horario(telefono);
            if (t.equals("4") || t.contains("comprobante") || t.contains("boleta") || t.contains("factura")) return comprobante(telefono);
            if (t.equals("5") || t.contains("asesor") || t.contains("humano") || t.contains("persona")) return derivar(telefono, texto);
        } catch (Exception e) {
            log.error("Bot: error consultando servicios internos: {}", e.getMessage());
            return "Estamos con problemas tecnicos en este momento 🙏. Un asesor te escribira pronto.";
        }
        return MENU;
    }

    private String cursos() {
        JsonNode lista = internos.cursos();
        if (lista == null || lista.isEmpty()) return "Por ahora no hay cursos abiertos. ¡Pronto publicaremos nuevos!";
        StringBuilder sb = new StringBuilder("📚 *Cursos disponibles*\n");
        for (JsonNode c : lista) {
            sb.append("\n• *").append(c.path("nombre").asText()).append("* (").append(c.path("modalidad").asText().toLowerCase())
              .append(")\n  Inicio: ").append(c.path("fechaInicio").asText()).append(" | ").append(c.path("moneda").asText())
              .append(" ").append(c.path("precio").asText()).append(" | Cupos: ").append(c.path("cuposDisponibles").asText());
        }
        return sb.append("\n\nInscribete en: ").append(frontend).toString();
    }

    private String estado(String telefono) {
        JsonNode est = internos.estudiante(telefono);
        if (!est.path("encontrado").asBoolean()) return noRegistrado();
        JsonNode ins = est.path("inscripciones");
        if (ins.isEmpty()) return "Hola " + est.path("nombre").asText() + ", aun no tienes inscripciones. Escribe *1* para ver los cursos.";
        StringBuilder sb = new StringBuilder("📋 *Tus inscripciones*\n");
        for (JsonNode i : ins) {
            sb.append("\n• ").append(i.path("curso").asText()).append(": *").append(traducir(i.path("estado").asText())).append("*");
            if ("PENDIENTE_PAGO".equals(i.path("estado").asText()) && i.hasNonNull("linkPago")) {
                sb.append("\n  Pagar: ").append(i.path("linkPago").asText());
            }
        }
        return sb.toString();
    }

    private String horario(String telefono) {
        JsonNode est = internos.estudiante(telefono);
        if (!est.path("encontrado").asBoolean()) return noRegistrado();
        StringBuilder sb = new StringBuilder();
        for (JsonNode i : est.path("inscripciones")) {
            if (!"PAGADA".equals(i.path("estado").asText())) continue;
            sb.append("\n• *").append(i.path("curso").asText()).append("*\n  Inicio: ").append(i.path("fechaInicio").asText())
              .append("\n  Horario: ").append(i.path("horario").asText())
              .append("\n  ").append("ONLINE".equals(i.path("modalidad").asText()) ? "Enlace: " : "Lugar: ").append(i.path("ubicacion").asText());
        }
        return sb.isEmpty() ? "No tienes cursos con pago confirmado. Escribe *2* para ver el estado de tus inscripciones."
                : "🗓️ *Tus clases*" + sb;
    }

    private String comprobante(String telefono) {
        JsonNode r = internos.reenviarComprobante(telefono);
        if (r != null && r.path("reenviado").asBoolean()) {
            return "📄 Listo, reenviamos tu comprobante *" + r.path("numero").asText() + "* a " + r.path("email").asText()
                    + ". Si no lo ves, revisa la carpeta de spam.";
        }
        return "No encontramos comprobantes asociados a este numero. Si ya pagaste, escribe *5* y un asesor te ayuda.";
    }

    private String derivar(String telefono, String texto) {
        Long id = notificaciones.registrar(null, new Mensajes.Mensaje("DERIVACION_ASESOR", telefonoAdmin,
                "🙋 Un estudiante pide hablar con un asesor.\nWhatsApp: +" + telefono + "\nMensaje: " + texto,
                null, List.of()));
        if (id != null) notificaciones.intentarEnvio(id);
        return "Listo 🙌 Un asesor te escribira en horario de atencion (lun-sab 9:00 a 18:00).";
    }

    private String noRegistrado() {
        return "No encontramos una cuenta con este numero de WhatsApp. Registrate en " + frontend
                + " usando este mismo numero, o escribe *5* para hablar con un asesor.";
    }

    private static String traducir(String estado) {
        return switch (estado) {
            case "PENDIENTE_PAGO" -> "Pendiente de pago";
            case "PAGADA" -> "Confirmada ✅";
            case "VENCIDA" -> "Vencida (no se pago a tiempo)";
            case "CANCELADA" -> "Cancelada";
            default -> estado;
        };
    }

    private static String normalizar(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toLowerCase();
    }
}
