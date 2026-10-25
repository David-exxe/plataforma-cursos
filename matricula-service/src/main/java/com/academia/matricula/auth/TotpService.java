package com.academia.matricula.auth;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Segundo factor (2FA) con TOTP - RFC 6238, compatible con Google Authenticator,
 * Microsoft Authenticator, Authy, etc. Implementado sin librerias externas.
 * Codigo de 6 digitos, periodo de 30 s, tolerancia de +-1 periodo por desfase de reloj.
 */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int PERIODO = 30;
    private final SecureRandom random = new SecureRandom();

    public String generarSecreto() {
        byte[] bytes = new byte[20]; // 160 bits, recomendado por RFC 4226
        random.nextBytes(bytes);
        return base32(bytes);
    }

    public String uriOtpAuth(String emisor, String cuenta, String secreto) {
        String etiqueta = URLEncoder.encode(emisor + ":" + cuenta, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + etiqueta + "?secret=" + secreto
                + "&issuer=" + URLEncoder.encode(emisor, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=6&period=" + PERIODO;
    }

    /**
     * Verifica el codigo. Devuelve el paso de tiempo usado (para impedir que el mismo codigo
     * se reutilice) o -1 si es invalido.
     */
    public long verificar(String secreto, String codigo, Long ultimoPasoUsado) {
        if (codigo == null || !codigo.matches("\\d{6}")) return -1;
        long pasoActual = Instant.now().getEpochSecond() / PERIODO;
        byte[] clave = desdeBase32(secreto);
        for (long p = pasoActual - 1; p <= pasoActual + 1; p++) {
            if (ultimoPasoUsado != null && p <= ultimoPasoUsado) continue;
            if (igualSeguro(generarCodigo(clave, p), codigo)) return p;
        }
        return -1;
    }

    String generarCodigo(byte[] clave, long paso) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(paso).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean igualSeguro(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    static String base32(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] desdeBase32(String s) {
        String limpio = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(limpio.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : limpio.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Base32 invalido");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return out.array();
    }
}
