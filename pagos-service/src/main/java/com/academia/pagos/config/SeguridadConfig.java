package com.academia.pagos.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

/** Valida el mismo JWT que emite matricula-service (defensa en profundidad detras del gateway). */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SeguridadConfig {

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
                .requestMatchers("/webhooks/**").permitAll()   // protegidos con firma HMAC
                .requestMatchers("/internal/**").permitAll()   // protegidos con ClaveInternaFilter
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(jwtDecoder()).jwtAuthenticationConverter(convertidorRoles())))
            .addFilterBefore(new ClaveInternaFilter(claveInterna), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(Base64.getDecoder().decode(secreto), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256).build();
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(emisor),
                new JwtClaimValidator<List<String>>("aud", a -> a != null && a.contains("api"))));
        return d;
    }

    private JwtAuthenticationConverter convertidorRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(roles);
        return conv;
    }

    public static Long usuarioId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }

    public static boolean esAdmin(Jwt jwt) {
        List<String> r = jwt.getClaimAsStringList("roles");
        return r != null && r.contains("ADMIN");
    }
}
