-- =====================================================================
-- notificaciones_db : mensajes de WhatsApp enviados y recibidos (bot)
-- =====================================================================

CREATE TABLE notificaciones (
    id               BIGSERIAL    PRIMARY KEY,
    evento_id        UUID,                                   -- evento que la origino (null si es respuesta del bot)
    tipo             VARCHAR(40)  NOT NULL,
    telefono         VARCHAR(20)  NOT NULL,
    mensaje          TEXT         NOT NULL,                  -- texto libre (ventana de 24 h)
    plantilla        VARCHAR(60),                            -- plantilla aprobada por Meta (fuera de 24 h)
    parametros       TEXT,                                   -- JSON array con los parametros de la plantilla
    estado           VARCHAR(15)  NOT NULL CHECK (estado IN ('PENDIENTE','ENVIADO','ENTREGADO','LEIDO','FALLIDO')),
    intentos         INT          NOT NULL DEFAULT 0,
    proximo_intento  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    wa_message_id    VARCHAR(100),
    error            VARCHAR(500),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviado_en       TIMESTAMPTZ,
    CONSTRAINT uq_notif_evento_tipo UNIQUE (evento_id, tipo)   -- idempotencia: un evento = un mensaje
);
CREATE INDEX idx_notif_pendientes ON notificaciones(estado, proximo_intento) WHERE estado = 'PENDIENTE';
CREATE INDEX idx_notif_wa_id ON notificaciones(wa_message_id);

-- Mensajes que escriben los estudiantes al bot
CREATE TABLE mensajes_entrantes (
    id             BIGSERIAL    PRIMARY KEY,
    wa_message_id  VARCHAR(100) NOT NULL UNIQUE,              -- WhatsApp puede reenviar el mismo mensaje
    telefono       VARCHAR(20)  NOT NULL,
    texto          TEXT,
    respuesta      TEXT,
    recibido_en    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_entrantes_telefono ON mensajes_entrantes(telefono, recibido_en);
