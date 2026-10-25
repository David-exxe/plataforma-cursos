package com.academia.notificaciones.notificacion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notificaciones")
@Getter @Setter @NoArgsConstructor
public class Notificacion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID eventoId;
    private String tipo;
    private String telefono;
    private String mensaje;
    private String plantilla;
    private String parametros;
    private String estado = "PENDIENTE";
    private int intentos;
    private Instant proximoIntento = Instant.now();
    private String waMessageId;
    private String error;
    private Instant creadoEn = Instant.now();
    private Instant enviadoEn;
}
