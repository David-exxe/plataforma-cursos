package com.academia.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Primera barrera de seguridad:
 *  - JWT: valida firma HS256, emisor, expiracion y audiencia "api" (un token temporal de 2FA NO sirve aqui).
 *  - CORS: solo el origen del frontend Angular.
 *  - Reglas de acceso por rol (ADMIN / ESTUDIANTE).
 * El bloqueo de IPs va antes que todo esto (ver IpBloqueoFilter).
 */
@Configuration
@EnableWebFluxSecurity
public class SeguridadConfig {

    @Value("${seguridad.jwt.secreto}")
    private String secreto;

    @Value("${seguridad.jwt.emisor}")
    private String emisor;

    @Value("${seguridad.cors.origenes-permitidos}")
    private String origenes;

    @Bean
    public SecurityWebFilterChain cadenaSeguridad(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .authorizeExchange(ex -> ex
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/actuator/health").permitAll()
                        // Publico: registro, login y segundo paso del 2FA
                        .pathMatchers(HttpMethod.POST, "/api/auth/registro", "/api/auth/login", "/api/auth/2fa/verificar").permitAll()
                        // Catalogo publico
                        .pathMatchers(HttpMethod.GET, "/api/cursos", "/api/cursos/**").permitAll()
                        // Webhooks: se protegen con firma HMAC dentro de cada servicio
                        .pathMatchers("/webhooks/**").permitAll()
                        .pathMatchers("/api/admin/**", "/gateway/admin/**").hasRole("ADMIN")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j
                        .jwtDecoder(jwtDecoder())
                        .jwtAuthenticationConverter(convertidorRoles())))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        SecretKey key = new SecretKeySpec(Base64.getDecoder().decode(secreto), "HmacSHA256");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audiencia = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains("api"));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(emisor), audiencia));
        return decoder;
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> convertidorRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(roles);
        return new ReactiveJwtAuthenticationConverterAdapter(conv);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        cfg.setExposedHeaders(List.of("Content-Disposition"));
        cfg.setAllowCredentials(false); // usamos Bearer token, no cookies
        cfg.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
