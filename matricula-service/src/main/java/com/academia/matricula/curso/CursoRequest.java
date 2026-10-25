package com.academia.matricula.curso;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CursoRequest(
        @NotBlank @Size(max = 30) String codigo,
        @NotBlank @Size(max = 150) String nombre,
        String descripcion,
        @NotNull Modalidad modalidad,
        @NotNull @DecimalMin("0.01") BigDecimal precio,
        @Pattern(regexp = "^(PEN|USD)$") String moneda,
        @NotNull @FutureOrPresent LocalDate fechaInicio,
        @Size(max = 120) String horario,
        @Size(max = 255) String ubicacion,
        @Min(1) int cupos,
        Boolean activo) {}
