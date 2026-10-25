package com.academia.matricula.auth;

import com.academia.matricula.auth.AuthDtos.*;
import com.academia.matricula.comun.ApiException;
import com.academia.matricula.comun.Telefonos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String EMISOR_2FA = "Academia Cursos";

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final TotpService totp;
    private final CifradoService cifrado;
    private final JwtDecoder decoder2fa;
    private final int maxIntentos;
    private final int minutosBloqueo;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, TokenService tokens, TotpService totp,
                       CifradoService cifrado, @Qualifier("jwtDecoder2fa") JwtDecoder decoder2fa,
                       @Value("${seguridad.max-intentos-login}") int maxIntentos,
                       @Value("${seguridad.minutos-bloqueo-cuenta}") int minutosBloqueo) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
        this.totp = totp;
        this.cifrado = cifrado;
        this.decoder2fa = decoder2fa;
        this.maxIntentos = maxIntentos;
        this.minutosBloqueo = minutosBloqueo;
    }

    @Transactional
    public UsuarioDto registrar(RegistroRequest r) {
        String email = r.email().trim().toLowerCase();
        if (usuarios.existsByEmailIgnoreCase(email)) throw ApiException.conflicto("El correo ya esta registrado");
        Usuario u = new Usuario();
        u.setNombre(r.nombre().trim());
        u.setEmail(email);
        u.setTelefono(Telefonos.normalizar(r.telefono()));
        u.setDocumento(r.documento());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setRol(Rol.ESTUDIANTE);
        return UsuarioDto.de(usuarios.save(u));
    }

    /**
     * Paso 1 del login. Mensaje de error generico para no revelar si el correo existe.
     * noRollbackFor: el contador de intentos fallidos debe guardarse aunque se lance la excepcion.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(LoginRequest r) {
        Usuario u = usuarios.findByEmailIgnoreCase(r.email().trim())
                .orElseThrow(() -> ApiException.noAutorizado("Credenciales invalidas"));

        if (u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(Instant.now())) {
            throw ApiException.noAutorizado("Cuenta bloqueada temporalmente por intentos fallidos");
        }
        if (!encoder.matches(r.password(), u.getPasswordHash())) {
            registrarFallo(u);
            throw ApiException.noAutorizado("Credenciales invalidas");
        }
        u.setIntentosFallidos(0);
        u.setBloqueadoHasta(null);

        if (u.isTotpHabilitado()) {
            return LoginResponse.pendiente2fa(tokens.tokenTemporal2fa(u), tokens.segundos2fa());
        }
        return LoginResponse.completo(tokens.tokenAcceso(u), tokens.segundosAcceso(), UsuarioDto.de(u));
    }

    /** Paso 2 del login: token temporal + codigo de la app autenticadora. */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse verificar2fa(Verificar2faRequest r) {
        Jwt temporal;
        try {
            temporal = decoder2fa.decode(r.tokenTemporal());
        } catch (JwtException e) {
            throw ApiException.noAutorizado("Token temporal invalido o expirado. Inicia sesion de nuevo");
        }
        Usuario u = usuarios.findById(Long.valueOf(temporal.getSubject()))
                .orElseThrow(() -> ApiException.noAutorizado("Usuario no encontrado"));
        if (!u.isTotpHabilitado()) throw ApiException.invalido("El usuario no tiene 2FA activo");
        if (u.getBloqueadoHasta() != null && u.getBloqueadoHasta().isAfter(Instant.now())) {
            throw ApiException.noAutorizado("Cuenta bloqueada temporalmente por intentos fallidos");
        }

        long paso = totp.verificar(cifrado.descifrar(u.getTotpSecretoCifrado()), r.codigo(), u.getTotpUltimoPaso());
        if (paso < 0) {
            registrarFallo(u);
            throw ApiException.noAutorizado("Codigo 2FA incorrecto");
        }
        u.setTotpUltimoPaso(paso);
        u.setIntentosFallidos(0);
        return LoginResponse.completo(tokens.tokenAcceso(u), tokens.segundosAcceso(), UsuarioDto.de(u));
    }

    /** Genera un secreto nuevo (aun no activo). El front muestra el otpauthUri como codigo QR. */
    @Transactional
    public Configurar2faResponse configurar2fa(Long usuarioId) {
        Usuario u = obtener(usuarioId);
        if (u.isTotpHabilitado()) throw ApiException.conflicto("El 2FA ya esta activo. Desactivalo primero");
        String secreto = totp.generarSecreto();
        u.setTotpSecretoCifrado(cifrado.cifrar(secreto));
        u.setTotpUltimoPaso(null);
        return new Configurar2faResponse(secreto, totp.uriOtpAuth(EMISOR_2FA, u.getEmail(), secreto));
    }

    /** Activa el 2FA solo si el usuario demuestra que escaneo el QR (envia un codigo valido). */
    @Transactional
    public UsuarioDto activar2fa(Long usuarioId, String codigo) {
        Usuario u = obtener(usuarioId);
        if (u.getTotpSecretoCifrado() == null) throw ApiException.invalido("Primero llama a /api/auth/2fa/configurar");
        long paso = totp.verificar(cifrado.descifrar(u.getTotpSecretoCifrado()), codigo, u.getTotpUltimoPaso());
        if (paso < 0) throw ApiException.invalido("Codigo incorrecto");
        u.setTotpHabilitado(true);
        u.setTotpUltimoPaso(paso);
        log.info("2FA activado para usuario {}", u.getId());
        return UsuarioDto.de(u);
    }

    @Transactional
    public UsuarioDto desactivar2fa(Long usuarioId, String codigo) {
        Usuario u = obtener(usuarioId);
        if (!u.isTotpHabilitado()) throw ApiException.invalido("El 2FA no esta activo");
        long paso = totp.verificar(cifrado.descifrar(u.getTotpSecretoCifrado()), codigo, u.getTotpUltimoPaso());
        if (paso < 0) throw ApiException.invalido("Codigo incorrecto");
        u.setTotpHabilitado(false);
        u.setTotpSecretoCifrado(null);
        u.setTotpUltimoPaso(null);
        return UsuarioDto.de(u);
    }

    @Transactional(readOnly = true)
    public UsuarioDto perfil(Long usuarioId) {
        return UsuarioDto.de(obtener(usuarioId));
    }

    private Usuario obtener(Long id) {
        return usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("Usuario no encontrado"));
    }

    private void registrarFallo(Usuario u) {
        u.setIntentosFallidos(u.getIntentosFallidos() + 1);
        if (u.getIntentosFallidos() >= maxIntentos) {
            u.setBloqueadoHasta(Instant.now().plus(minutosBloqueo, ChronoUnit.MINUTES));
            u.setIntentosFallidos(0);
            log.warn("Cuenta {} bloqueada {} min por intentos fallidos", u.getEmail(), minutosBloqueo);
        }
        usuarios.save(u);
    }
}
