package com.academia.notificaciones.notificacion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    boolean existsByEventoIdAndTipo(UUID eventoId, String tipo);

    Optional<Notificacion> findFirstByWaMessageId(String waMessageId);

    @Query("select n.id from Notificacion n where n.estado = 'PENDIENTE' and n.proximoIntento <= :ahora order by n.proximoIntento")
    List<Long> pendientes(@Param("ahora") Instant ahora);

    List<Notificacion> findTop200ByOrderByCreadoEnDesc();

    List<Notificacion> findTop200ByEstadoOrderByCreadoEnDesc(String estado);
}
