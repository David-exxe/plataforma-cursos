package com.academia.matricula.curso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    List<Curso> findByActivoTrueAndFechaInicioGreaterThanEqualOrderByFechaInicio(LocalDate desde);

    boolean existsByCodigoIgnoreCase(String codigo);

    /** SELECT ... FOR UPDATE: serializa las inscripciones concurrentes al mismo curso. */
    @Query(value = "SELECT id FROM cursos WHERE id = :id FOR UPDATE", nativeQuery = true)
    Long bloquear(@Param("id") Long id);
}
