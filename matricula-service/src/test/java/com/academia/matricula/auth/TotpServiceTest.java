package com.academia.matricula.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** Vectores oficiales del RFC 6238 (SHA-1), ultimos 6 digitos. */
class TotpServiceTest {

    private final TotpService totp = new TotpService();
    private final byte[] clave = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void generaCodigosDelRfc() {
        assertThat(totp.generarCodigo(clave, 59 / 30)).isEqualTo("287082");
        assertThat(totp.generarCodigo(clave, 1111111109L / 30)).isEqualTo("081804");
        assertThat(totp.generarCodigo(clave, 1234567890L / 30)).isEqualTo("005924");
    }

    @Test
    void base32IdaYVuelta() {
        assertThat(TotpService.base32(clave)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpService.desdeBase32("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")).isEqualTo(clave);
    }

    @Test
    void noPermiteReutilizarElMismoCodigo() {
        String secreto = totp.generarSecreto();
        long paso = System.currentTimeMillis() / 1000 / 30;
        String codigo = totp.generarCodigo(TotpService.desdeBase32(secreto), paso);
        long usado = totp.verificar(secreto, codigo, null);
        assertThat(usado).isEqualTo(paso);
        assertThat(totp.verificar(secreto, codigo, usado)).isEqualTo(-1);
    }
}
