-- =====================================================================
-- pagos_db : pagos con Mercado Pago, auditoria de webhooks, outbox
-- =====================================================================

CREATE TABLE pagos (
    id                    UUID          PRIMARY KEY,
    inscripcion_id        BIGINT        NOT NULL UNIQUE,     -- 1 pago vivo por inscripcion (idempotencia)
    usuario_id            BIGINT        NOT NULL,
    estudiante_nombre     VARCHAR(120)  NOT NULL,
    estudiante_email      VARCHAR(150)  NOT NULL,
    estudiante_telefono   VARCHAR(20),
    estudiante_documento  VARCHAR(20),
    curso_id              BIGINT        NOT NULL,
    curso_nombre          VARCHAR(150)  NOT NULL,
    monto                 NUMERIC(10,2) NOT NULL CHECK (monto > 0),
    moneda                VARCHAR(3)    NOT NULL,
    preference_id         VARCHAR(80),
    link_pago             VARCHAR(500),
    mp_payment_id         VARCHAR(40),
    estado                VARCHAR(20)   NOT NULL CHECK (estado IN ('PENDIENTE','EN_PROCESO','APROBADO','RECHAZADO','REEMBOLSADO')),
    estado_detalle        VARCHAR(80),                       -- status_detail de Mercado Pago
    metodo_pago           VARCHAR(40),
    fecha_aprobacion      TIMESTAMPTZ,
    creado_en             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pagos_estado ON pagos(estado, creado_en);
CREATE UNIQUE INDEX uq_pagos_mp_payment ON pagos(mp_payment_id) WHERE mp_payment_id IS NOT NULL;

-- Cada notificacion que manda Mercado Pago queda registrada (auditoria y depuracion)
CREATE TABLE webhook_eventos (
    id            BIGSERIAL    PRIMARY KEY,
    request_id    VARCHAR(80),
    tipo          VARCHAR(40),
    data_id       VARCHAR(60),
    firma_valida  BOOLEAN      NOT NULL,
    procesado     BOOLEAN      NOT NULL DEFAULT FALSE,
    error         VARCHAR(500),
    payload       TEXT,
    recibido_en   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_webhook_data_id ON webhook_eventos(data_id);

CREATE TABLE outbox_eventos (
    id            BIGSERIAL PRIMARY KEY,
    evento_id     UUID          NOT NULL UNIQUE,
    tipo          VARCHAR(60)   NOT NULL,                  -- pago.aprobado, pago.rechazado
    payload       TEXT          NOT NULL,
    estado        VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','PUBLICADO','ERROR')),
    intentos      INT           NOT NULL DEFAULT 0,
    ultimo_error  VARCHAR(500),
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    publicado_en  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendientes ON outbox_eventos(estado, creado_en) WHERE estado <> 'PUBLICADO';
