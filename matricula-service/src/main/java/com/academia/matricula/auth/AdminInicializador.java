package com.academia.matricula.auth;

import com.academia.matricula.comun.Telefonos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea el usuario ADMIN al arrancar (la contrasena viene de variable de entorno, nunca del codigo). */
@Component
public class AdminInicializador implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicializador.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    @Value("${app.admin.email}") private String email;
    @Value("${app.admin.password}") private String password;
    @Value("${app.admin.telefono}") private String telefono;

    public AdminInicializador(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank() || usuarios.existsByEmailIgnoreCase(email)) return;
        Usuario u = new Usuario();
        u.setNombre("Administrador");
        u.setEmail(email.toLowerCase());
        u.setTelefono(Telefonos.normalizar(telefono));
        u.setPasswordHash(encoder.encode(password));
        u.setRol(Rol.ADMIN);
        usuarios.save(u);
        log.info("Usuario ADMIN creado: {} (activa el 2FA en tu primer ingreso)", email);
    }
}
