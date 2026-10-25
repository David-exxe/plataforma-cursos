package com.academia.pagos.pago;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Valida la cabecera x-signature de los webhooks de Mercado Pago:
 *   x-signature: ts=1704908010,v1=618c85345248dd820d5fd456117c2ab2ef8eda45a0282ff693eac24131a5e839
 *   manifest   : "id:{data.id};request-id:{x-request-id};ts:{ts};"
 *   v1         = HMAC-SHA256(manifest, clave secreta del webhook) en hexadecimal
 * Asi nadie puede enviarnos un "pago aprobado" falso. (Ademas, igual consultamos el pago a la API.)
 */
@Component
public class FirmaWebhookValidator {

    private final String secreto;

    public FirmaWebhookValidator(@Value("${mercadopago.webhook-secret}") String secreto) {
        this.secreto = secreto;
    }

    public boolean configurado() {
        return secreto != null && !secreto.isBlank();
    }

    public boolean esValida(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null || xSignature.isBlank()) return false;
        String ts = null, v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.split("=", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim(), v = kv[1].trim();
            if (k.equals("ts")) ts = v;
            else if (k.equals("v1")) v1 = v;
        }
        if (ts == null || v1 == null) return false;

        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // Mercado Pago indica: si data.id es alfanumerico, va en minusculas
            manifest.append("id:").append(dataId.toLowerCase()).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) manifest.append("request-id:").append(xRequestId).append(';');
        manifest.append("ts:").append(ts).append(';');

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculado = HexFormat.of().formatHex(mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(calculado.getBytes(StandardCharsets.UTF_8), v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
