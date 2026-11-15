package com.academia.comprobantes.comprobante;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "comprobantes")
@Getter @Setter @NoArgsConstructor
public class Comprobante {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String serie;
    private Long correlativo;
    private String numero;
    private Long inscripcionId;
    private UUID eventoId;
    private String pagoId;
    private String mpPaymentId;
    private Long usuarioId;
    private String estudianteNombre;
    private String email;
    private String telefono;
    private String documento;
    private Long cursoId;
    private String cursoNombre;
    private BigDecimal monto;
    private String moneda;
    private String metodoPago;
    private Instant fechaPago;
    private Instant fechaEmision = Instant.now();
    private String estadoEnvio = "PENDIENTE";
    private int intentosEnvio;
    private String ultimoError;
    private Instant proximoIntento = Instant.now();
    private Instant enviadoEn;
}
