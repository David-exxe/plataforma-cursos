package com.academia.comprobantes.comprobante;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ComprobanteRepository extends JpaRepository<Comprobante, Long> {

    Optional<Comprobante> findByInscripcionId(Long inscripcionId);

    List<Comprobante> findByUsuarioIdOrderByFechaEmisionDesc(Long usuarioId);

    Optional<Comprobante> findFirstByTelefonoOrderByFechaEmisionDesc(String telefono);

    List<Comprobante> findTop200ByOrderByFechaEmisionDesc();

    List<Comprobante> findTop200ByEstadoEnvioOrderByFechaEmisionDesc(String estado);

    @Query(value = "SELECT nextval('comprobante_correlativo_seq')", nativeQuery = true)
    Long siguienteCorrelativo();

    @Query("select c.id from Comprobante c where c.estadoEnvio in ('PENDIENTE','ERROR') and c.proximoIntento <= :ahora order by c.proximoIntento")
    List<Long> paraEnviar(@Param("ahora") Instant ahora);
}
