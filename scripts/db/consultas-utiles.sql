-- ===================== Consultas para revisar / sustentar =====================

-- [matricula_db] Inscripciones con su estado
SELECT i.id, u.nombre, c.nombre AS curso, i.estado, i.monto, i.correo_info_enviado,
       i.correo_invitacion_enviado, i.recordatorio_enviado, i.creado_en
FROM inscripciones i JOIN usuarios u ON u.id = i.usuario_id JOIN cursos c ON c.id = i.curso_id
ORDER BY i.id DESC;

-- [matricula_db] / [pagos_db] Eventos del outbox que aun no salieron a RabbitMQ
SELECT evento_id, tipo, estado, intentos, ultimo_error, creado_en FROM outbox_eventos WHERE estado <> 'PUBLICADO';

-- [pagos_db] Pagos y lo que llego de Mercado Pago
SELECT inscripcion_id, estado, estado_detalle, monto, mp_payment_id, fecha_aprobacion FROM pagos ORDER BY creado_en DESC;
SELECT recibido_en, tipo, data_id, firma_valida, procesado, error FROM webhook_eventos ORDER BY id DESC LIMIT 20;

-- [comprobantes_db] El caso "MP confirmo el pago pero el correo no salio"
SELECT numero, email, estado_envio, intentos_envio, ultimo_error, proximo_intento
FROM comprobantes WHERE estado_envio IN ('ERROR','FALLIDO');

-- [notificaciones_db] WhatsApp enviados / fallidos
SELECT tipo, telefono, estado, intentos, error, creado_en FROM notificaciones ORDER BY id DESC LIMIT 20;
