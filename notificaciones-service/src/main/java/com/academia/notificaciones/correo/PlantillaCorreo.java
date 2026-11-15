package com.academia.notificaciones.correo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PlantillaCorreo {

    private final String pagoConfirmado;

    public PlantillaCorreo(@Value("classpath:templates/pago-confirmado.html") Resource recurso) {
        try {
            this.pagoConfirmado = recurso.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la plantilla de correo", e);
        }
    }

    public String pagoConfirmado(Map<String, String> valores) {
        String html = pagoConfirmado;
        for (Map.Entry<String, String> v : valores.entrySet()) {
            html = html.replace("{{" + v.getKey() + "}}", HtmlUtils.htmlEscape(v.getValue() == null ? "" : v.getValue()));
        }
        return html;
    }
}
