package com.academia.notificaciones.bot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeEntranteRepository extends JpaRepository<MensajeEntrante, Long> {
    boolean existsByWaMessageId(String waMessageId);
}
