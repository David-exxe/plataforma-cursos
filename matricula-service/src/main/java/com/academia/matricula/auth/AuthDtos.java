package com.academia.matricula.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegistroRequest(
            @NotBlank @Size(max = 120) String nombre,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = "^\\+?[0-9 ]{9,15}$", message = "Telefono invalido") String telefono,
            @Pattern(regexp = "^[0-9A-Za-z]{8,12}$", message = "Documento invalido") String documento,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Debe tener letras y numeros") String password) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record Verificar2faRequest(@NotBlank String tokenTemporal, @NotBlank @Pattern(regexp = "^\\d{6}$") String codigo) {}

    public record CodigoRequest(@NotBlank @Pattern(regexp = "^\\d{6}$") String codigo) {}

    public record UsuarioDto(Long id, String nombre, String email, String telefono, String rol, boolean dosFactores) {
        public static UsuarioDto de(Usuario u) {
            return new UsuarioDto(u.getId(), u.getNombre(), u.getEmail(), u.getTelefono(), u.getRol().name(), u.isTotpHabilitado());
        }
    }

    /** Si requiere2fa = true, el front debe pedir el codigo y llamar a /api/auth/2fa/verificar. */
    public record LoginResponse(boolean requiere2fa, String accessToken, String tokenTemporal,
                                String tipo, long expiraEnSegundos, UsuarioDto usuario) {
        public static LoginResponse completo(String token, long exp, UsuarioDto u) {
            return new LoginResponse(false, token, null, "Bearer", exp, u);
        }
        public static LoginResponse pendiente2fa(String temporal, long exp) {
            return new LoginResponse(true, null, temporal, null, exp, null);
        }
    }

    public record Configurar2faResponse(String secreto, String otpauthUri) {}
}
