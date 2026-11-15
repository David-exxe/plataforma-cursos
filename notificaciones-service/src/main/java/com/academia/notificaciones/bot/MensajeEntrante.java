package com.academia.notificaciones.bot;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "mensajes_entrantes")
@Getter @Setter @NoArgsConstructor
public class MensajeEntrante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String waMessageId;
    private String telefono;
    private String texto;
    private String respuesta;
    private Instant recibidoEn = Instant.now();
}
