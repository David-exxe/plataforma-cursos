package com.academia.matricula.auth;

import com.academia.matricula.auth.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioDto registro(@Valid @RequestBody RegistroRequest r) {
        return auth.registrar(r);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r);
    }

    @PostMapping("/2fa/verificar")
    public LoginResponse verificar2fa(@Valid @RequestBody Verificar2faRequest r) {
        return auth.verificar2fa(r);
    }

    @GetMapping("/me")
    public UsuarioDto me(@AuthenticationPrincipal Jwt jwt) {
        return auth.perfil(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/2fa/configurar")
    public Configurar2faResponse configurar(@AuthenticationPrincipal Jwt jwt) {
        return auth.configurar2fa(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/2fa/activar")
    public UsuarioDto activar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodigoRequest r) {
        return auth.activar2fa(Long.valueOf(jwt.getSubject()), r.codigo());
    }

    @PostMapping("/2fa/desactivar")
    public UsuarioDto desactivar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodigoRequest r) {
        return auth.desactivar2fa(Long.valueOf(jwt.getSubject()), r.codigo());
    }
}
