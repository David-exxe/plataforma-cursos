package com.academia.matricula.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvento, Long> {
    @Query(value = """
            SELECT * FROM outbox_eventos
            WHERE estado <> 'PUBLICADO' AND intentos < 20
            ORDER BY creado_en
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvento> bloquearPendientes();
}
