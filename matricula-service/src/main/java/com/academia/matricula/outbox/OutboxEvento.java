package com.academia.matricula.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_eventos")
@Getter @Setter @NoArgsConstructor
public class OutboxEvento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID eventoId;
    private String tipo;
    private String payload;
    private String estado = "PENDIENTE";
    private int intentos;
    private String ultimoError;
    private Instant creadoEn = Instant.now();
    private Instant publicadoEn;
}
