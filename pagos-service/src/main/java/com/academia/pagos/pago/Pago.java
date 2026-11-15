package com.academia.pagos.pago;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pagos")
@Getter @Setter @NoArgsConstructor
public class Pago {
    @Id
    private UUID id;
    private Long inscripcionId;
    private Long usuarioId;
    private String estudianteNombre;
    private String estudianteEmail;
    private String estudianteTelefono;
    private String estudianteDocumento;
    private Long cursoId;
    private String cursoNombre;
    private BigDecimal monto;
    private String moneda;
    private String preferenceId;
    private String linkPago;
    private String mpPaymentId;
    @Enumerated(EnumType.STRING)
    private EstadoPago estado = EstadoPago.PENDIENTE;
    private String estadoDetalle;
    private String metodoPago;
    private Instant fechaAprobacion;
    private Instant creadoEn = Instant.now();
    private Instant actualizadoEn = Instant.now();

    @PreUpdate
    void alActualizar() { actualizadoEn = Instant.now(); }
}
