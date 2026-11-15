package com.academia.notificaciones.notificacion;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Texto de cada mensaje de WhatsApp segun el evento.
 * Para cada uno tambien se define la plantilla equivalente (con sus parametros en orden),
 * que es la que hay que registrar y hacer aprobar en Meta Business para produccion.
 */
public final class Mensajes {
    private Mensajes() {}

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record Mensaje(String tipo, String telefono, String texto, String plantilla, List<String> parametros) {}

    public static Mensaje desdeEvento(String routingKey, JsonNode d, String telefonoAdmin, String frontend) {
        JsonNode est = d.path("estudiante");
        JsonNode curso = d.path("curso");
        String nombre = primerNombre(est.path("nombre").asText("estudiante"));
        String tel = est.path("telefono").asText(null);
        String cursoNombre = curso.path("nombre").asText("tu curso");

        return switch (routingKey) {
            case "inscripcion.creada" -> {
                String link = d.path("linkPago").asText("");
                String pagar = link.isBlank() ? "Puedes pagar desde " + frontend + "/mis-inscripciones" : "Paga aqui con Mercado Pago:\n" + link;
                yield new Mensaje("INSCRIPCION_RECIBIDA", tel,
                        "Hola " + nombre + " 👋\nRecibimos tu inscripcion a *" + cursoNombre + "* (inicia el "
                                + fecha(curso.path("fechaInicio").asText()) + ").\n\nMonto: " + d.path("moneda").asText() + " "
                                + d.path("monto").asText() + "\n" + pagar + "\n\nEscribe *MENU* si tienes dudas.",
                        "inscripcion_recibida", List.of(nombre, cursoNombre, link.isBlank() ? frontend : link));
            }
            case "pago.aprobado" -> new Mensaje("PAGO_RECIBIDO", tel,
                    "✅ ¡Pago recibido, " + nombre + "!\nTu inscripcion a *" + cursoNombre + "* esta *CONFIRMADA*.\n"
                            + "Monto: " + d.path("moneda").asText() + " " + d.path("monto").asText()
                            + "\nEn unos minutos te llega el comprobante a tu correo.",
                    "pago_confirmado", List.of(nombre, cursoNombre, d.path("moneda").asText() + " " + d.path("monto").asText()));
            case "pago.rechazado" -> new Mensaje("PAGO_RECHAZADO", tel,
                    "Hola " + nombre + ", tu pago para *" + cursoNombre + "* no fue aprobado.\n"
                            + "Puedes intentarlo de nuevo con otro medio de pago:\n" + d.path("linkPago").asText(frontend + "/mis-inscripciones"),
                    "pago_rechazado", List.of(nombre, cursoNombre));
            case "curso.recordatorio" -> {
                String ubic = curso.path("ubicacion").asText("");
                boolean online = "ONLINE".equals(curso.path("modalidad").asText());
                yield new Mensaje("RECORDATORIO_INICIO", tel,
                        "⏰ " + nombre + ", ¡mañana empieza *" + cursoNombre + "*!\nHorario: " + curso.path("horario").asText()
                                + "\n" + (online ? "Enlace: " : "Lugar: ") + ubic + "\n¡Te esperamos!",
                        "recordatorio_clase", List.of(nombre, cursoNombre, curso.path("horario").asText(), ubic));
            }
            case "comprobante.enviado" -> new Mensaje("COMPROBANTE_ENVIADO", d.path("telefono").asText(null),
                    "📄 " + primerNombre(d.path("nombre").asText("")) + ", te enviamos el comprobante *"
                            + d.path("numero").asText() + "* a " + d.path("email").asText() + ".\nRevisa tambien tu carpeta de spam.",
                    "comprobante_enviado", List.of(primerNombre(d.path("nombre").asText("")), d.path("numero").asText(), d.path("email").asText()));
            // Fallo definitivo del correo: se avisa al ADMIN (no al estudiante) para que lo resuelva a mano
            case "comprobante.fallido" -> new Mensaje("ALERTA_COMPROBANTE", telefonoAdmin,
                    "⚠️ ALERTA: no se pudo enviar el comprobante " + d.path("numero").asText() + " a "
                            + d.path("email").asText() + " tras " + d.path("intentos").asText() + " intentos.\nError: "
                            + d.path("ultimoError").asText() + "\nReenviar desde el panel admin.",
                    "alerta_admin", List.of(d.path("numero").asText(), d.path("email").asText()));
            default -> null;
        };
    }

    private static String primerNombre(String n) {
        return n == null || n.isBlank() ? "" : n.trim().split("\\s+")[0];
    }

    private static String fecha(String iso) {
        try { return LocalDate.parse(iso).format(FECHA); } catch (Exception e) { return iso; }
    }
}
