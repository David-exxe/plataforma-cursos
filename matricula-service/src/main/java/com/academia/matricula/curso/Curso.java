package com.academia.matricula.curso;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cursos")
@Getter @Setter @NoArgsConstructor
public class Curso {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String codigo;
    private String nombre;
    private String descripcion;
    @Enumerated(EnumType.STRING)
    private Modalidad modalidad;
    private BigDecimal precio;
    private String moneda = "PEN";
    private LocalDate fechaInicio;
    private String horario;
    private String ubicacion;
    private int cupos;
    private boolean activo = true;
    private Instant creadoEn = Instant.now();
}
