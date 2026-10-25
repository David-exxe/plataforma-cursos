package com.academia.matricula.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Defensa en profundidad: aunque el gateway ya valido el JWT, este servicio lo vuelve a validar.
 * Si alguien llegara directo al puerto 8081 (sin pasar por el gateway) igual necesita token.
 * CORS NO se configura aqui: lo hace solo el gateway (si ambos lo hicieran, el navegador
 * veria cabeceras duplicadas y rechazaria la respuesta).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadConfig {

    public static final String AUD_API = "api";
    public static final String AUD_2FA = "2fa-pendiente";

    @Value("${seguridad.jwt.secreto}")
    private String secreto;
    @Value("${seguridad.jwt.emisor}")
    private String emisor;
    @Value("${seguridad.clave-interna}")
    private String claveInterna;

    @Bean
    public SecurityFilterChain cadena(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
            .cors(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/login", "/api/auth/2fa/verificar").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/cursos", "/api/cursos/**").permitAll()
                .requestMatchers("/internal/**").permitAll()          // protegido por ClaveInternaFilter
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwtDecoder()).jwtAuthenticationConverter(convertidorRoles())))
            .addFilterBefore(new ClaveInternaFilter(claveInterna), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    private SecretKey clave() {
        return new SecretKeySpec(Base64.getDecoder().decode(secreto), "HmacSHA256");
    }

    /** Decoder de tokens de acceso normales (audiencia "api"). */
    @Bean
    @Primary
    public JwtDecoder jwtDecoder() {
        return decoderConAudiencia(AUD_API);
    }

    /** Decoder SOLO para el token temporal entre la contrasena y el codigo 2FA. */
    @Bean
    public JwtDecoder jwtDecoder2fa() {
        return decoderConAudiencia(AUD_2FA);
    }

    private JwtDecoder decoderConAudiencia(String aud) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave()).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emisor),
                new JwtClaimValidator<List<String>>("aud", a -> a != null && a.contains(aud))));
        return decoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave()));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    private JwtAuthenticationConverter convertidorRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(roles);
        return conv;
    }
}
