package com.academia.matricula.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM para no guardar los secretos 2FA en texto plano en la base de datos. */
@Service
public class CifradoService {

    private static final int IV_BYTES = 12;
    private final SecretKey clave;
    private final SecureRandom random = new SecureRandom();

    public CifradoService(@Value("${seguridad.clave-cifrado}") String claveBase64) {
        byte[] k = Base64.getDecoder().decode(claveBase64);
        if (k.length != 32) throw new IllegalStateException("ENCRYPTION_KEY debe ser Base64 de 32 bytes");
        this.clave = new SecretKeySpec(k, "AES");
    }

    public String cifrar(String texto) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(128, iv));
            byte[] cifrado = c.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cifrar", e);
        }
    }

    public String descifrar(String base64) {
        try {
            byte[] todo = Base64.getDecoder().decode(base64);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(128, todo, 0, IV_BYTES));
            return new String(c.doFinal(todo, IV_BYTES, todo.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo descifrar", e);
        }
    }
}
