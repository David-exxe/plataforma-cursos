package com.academia.comprobantes.comun;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Errores que NO pasan por ManejadorErrores y Spring Boot envia a /error
 * (por ejemplo, una excepcion dentro de un filtro). Por defecto Boot responde
 * {"error":"Internal Server Error","path":...} en ingles; aqui se deja con el
 * mismo formato que ErrorRespuesta y con mensaje en espanol.
 */
@Component
public class AtributosError extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest request, ErrorAttributeOptions options) {
        Map<String, Object> base = super.getErrorAttributes(request, ErrorAttributeOptions.defaults());
        int status = base.get("status") instanceof Integer s ? s : 500;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", java.time.Instant.now().toString());
        m.put("status", status);
        m.put("codigo", ErrorRespuesta.codigoDe(status));
        m.put("error", ErrorRespuesta.mensajePorDefecto(status));
        if (base.get("path") != null) m.put("ruta", base.get("path"));
        return m;
    }
}
