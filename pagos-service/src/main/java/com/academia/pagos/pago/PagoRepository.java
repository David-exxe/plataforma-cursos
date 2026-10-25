package com.academia.pagos.pago;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagoRepository extends JpaRepository<Pago, UUID> {

    Optional<Pago> findByInscripcionId(Long inscripcionId);

    /** Bloquea la fila: dos webhooks simultaneos del mismo pago se procesan uno detras de otro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Pago p where p.inscripcionId = :inscripcionId")
    Optional<Pago> bloquearPorInscripcion(@Param("inscripcionId") Long inscripcionId);

    List<Pago> findByEstadoOrderByCreadoEnDesc(EstadoPago estado);

    List<Pago> findAllByOrderByCreadoEnDesc();

    @Query("""
           select p from Pago p
           where p.estado in (com.academia.pagos.pago.EstadoPago.PENDIENTE, com.academia.pagos.pago.EstadoPago.EN_PROCESO)
             and p.preferenceId is not null and p.creadoEn between :desde and :hasta
           """)
    List<Pago> paraConciliar(@Param("desde") Instant desde, @Param("hasta") Instant hasta);
}
