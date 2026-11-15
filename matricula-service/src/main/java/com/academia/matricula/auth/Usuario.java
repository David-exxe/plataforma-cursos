package com.academia.matricula.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "usuarios")
@Getter @Setter @NoArgsConstructor
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    private String email;
    private String telefono;
    private String documento;
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    private Rol rol;
    private String totpSecretoCifrado;
    private boolean totpHabilitado;
    private Long totpUltimoPaso;
    private int intentosFallidos;
    private Instant bloqueadoHasta;
    private Instant creadoEn = Instant.now();
}
