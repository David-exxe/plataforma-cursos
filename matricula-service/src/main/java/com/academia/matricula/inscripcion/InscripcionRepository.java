package com.academia.matricula.inscripcion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    @Query("select count(i) from Inscripcion i where i.curso.id = :cursoId and i.estado in :estados")
    long contarPorEstados(@Param("cursoId") Long cursoId, @Param("estados") List<EstadoInscripcion> estados);

    default long contarOcupados(Long cursoId) {
        return contarPorEstados(cursoId, List.of(EstadoInscripcion.PENDIENTE_PAGO, EstadoInscripcion.PAGADA));
    }

    @Query("select i from Inscripcion i join fetch i.curso join fetch i.usuario where i.id = :id")
    Optional<Inscripcion> buscarCompleta(@Param("id") Long id);

    @Query("select i from Inscripcion i join fetch i.curso where i.usuario.id = :usuarioId order by i.creadoEn desc")
    List<Inscripcion> delUsuario(@Param("usuarioId") Long usuarioId);

    @Query("select i from Inscripcion i join fetch i.curso join fetch i.usuario u where u.telefono = :telefono order by i.creadoEn desc")
    List<Inscripcion> porTelefono(@Param("telefono") String telefono);

    @Query("select i from Inscripcion i join fetch i.curso join fetch i.usuario order by i.creadoEn desc")
    List<Inscripcion> todasAdmin();

    @Query("select i from Inscripcion i join fetch i.curso join fetch i.usuario where i.estado = :estado order by i.creadoEn desc")
    List<Inscripcion> porEstadoAdmin(@Param("estado") EstadoInscripcion estado);

    @Query("""
           select i from Inscripcion i join fetch i.curso c join fetch i.usuario
           where i.estado = com.academia.matricula.inscripcion.EstadoInscripcion.PAGADA
             and i.recordatorioEnviado = false and c.fechaInicio = :fecha
           """)
    List<Inscripcion> paraRecordatorio(@Param("fecha") LocalDate fecha);

    @Query("""
           select i from Inscripcion i
           where i.estado = com.academia.matricula.inscripcion.EstadoInscripcion.PENDIENTE_PAGO
             and i.creadoEn < :limite
           """)
    List<Inscripcion> pendientesVencidas(@Param("limite") Instant limite);

    @Query("""
           select i.id from Inscripcion i
           where i.intentosCorreo < 5 and i.creadoEn < :limite
             and (i.correoInfoEnviado = false
                  or (i.estado = com.academia.matricula.inscripcion.EstadoInscripcion.PAGADA and i.correoInvitacionEnviado = false))
           """)
    List<Long> conCorreoPendiente(@Param("limite") Instant limite);
}
