package com.academia.matricula.auth;

import com.academia.matricula.config.SeguridadConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final String emisor;
    private final long minutos;
    private final long minutos2fa;

    public TokenService(JwtEncoder encoder,
                        @Value("${seguridad.jwt.emisor}") String emisor,
                        @Value("${seguridad.jwt.minutos-expiracion}") long minutos,
                        @Value("${seguridad.jwt.minutos-expiracion-2fa}") long minutos2fa) {
        this.encoder = encoder;
        this.emisor = emisor;
        this.minutos = minutos;
        this.minutos2fa = minutos2fa;
    }

    public long segundosAcceso() { return minutos * 60; }
    public long segundos2fa() { return minutos2fa * 60; }

    public String tokenAcceso(Usuario u) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .audience(List.of(SeguridadConfig.AUD_API))
                .subject(String.valueOf(u.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(minutos, ChronoUnit.MINUTES))
                .id(UUID.randomUUID().toString())
                .claim("email", u.getEmail())
                .claim("nombre", u.getNombre())
                .claim("roles", List.of(u.getRol().name()))
                .claim("mfa", u.isTotpHabilitado())
                .build();
        return firmar(claims);
    }

    /** Token de 5 minutos que solo prueba que la contrasena fue correcta. NO da acceso a la API. */
    public String tokenTemporal2fa(Usuario u) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .audience(List.of(SeguridadConfig.AUD_2FA))
                .subject(String.valueOf(u.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(minutos2fa, ChronoUnit.MINUTES))
                .build();
        return firmar(claims);
    }

    private String firmar(JwtClaimsSet claims) {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
