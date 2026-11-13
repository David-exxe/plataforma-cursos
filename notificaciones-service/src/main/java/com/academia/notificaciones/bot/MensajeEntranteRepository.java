package com.academia.notificaciones.bot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MensajeEntranteRepository extends JpaRepository<MensajeEntrante, Long> {
    boolean existsByWaMessageId(String waMessageId);

    List<MensajeEntrante> findTop30ByTelefonoOrderByRecibidoEnDesc(String telefono);
}
