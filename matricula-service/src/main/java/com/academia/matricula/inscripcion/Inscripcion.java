package com.academia.matricula.inscripcion;

import com.academia.matricula.auth.Usuario;
import com.academia.matricula.curso.Curso;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inscripciones")
@Getter @Setter @NoArgsConstructor
public class Inscripcion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curso_id")
    private Curso curso;

    @Enumerated(EnumType.STRING)
    private EstadoInscripcion estado;
    private BigDecimal monto;
    private String moneda;
    private String linkPago;
    private String pagoId;
    private String mpPaymentId;
    private String ultimoEstadoPago;
    private Instant fechaPago;
    private boolean correoInfoEnviado;
    private boolean correoInvitacionEnviado;
    private int intentosCorreo;
    private boolean recordatorioEnviado;
    private Instant creadoEn = Instant.now();
    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() { this.actualizadoEn = Instant.now(); }
}
