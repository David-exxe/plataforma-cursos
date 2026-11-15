package com.academia.notificaciones.correo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CorreoEnviadoRepository extends JpaRepository<CorreoEnviado, Long> {
    boolean existsByEventoIdAndTipo(UUID eventoId, String tipo);
}
