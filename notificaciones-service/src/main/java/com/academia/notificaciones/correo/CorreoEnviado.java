package com.academia.notificaciones.correo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "correos_enviados")
@Getter @Setter @NoArgsConstructor
public class CorreoEnviado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID eventoId;
    private String tipo;
    private String destinatario;
    private String asunto;
    private Instant enviadoEn = Instant.now();
}
