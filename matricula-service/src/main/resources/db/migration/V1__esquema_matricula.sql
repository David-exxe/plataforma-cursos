-- =====================================================================
-- matricula_db : usuarios, catalogo de cursos, inscripciones, outbox
-- =====================================================================

CREATE TABLE usuarios (
    id                     BIGSERIAL PRIMARY KEY,
    nombre                 VARCHAR(120) NOT NULL,
    email                  VARCHAR(150) NOT NULL UNIQUE,
    telefono               VARCHAR(20)  NOT NULL,          -- formato WhatsApp: 51987654321
    documento              VARCHAR(20),                    -- DNI / CE (va en el comprobante)
    password_hash          VARCHAR(100) NOT NULL,
    rol                    VARCHAR(20)  NOT NULL CHECK (rol IN ('ESTUDIANTE','ADMIN')),
    totp_secreto_cifrado   VARCHAR(255),                   -- secreto 2FA cifrado con AES-GCM
    totp_habilitado        BOOLEAN      NOT NULL DEFAULT FALSE,
    totp_ultimo_paso       BIGINT,                         -- evita reutilizar el mismo codigo
    intentos_fallidos      INT          NOT NULL DEFAULT 0,
    bloqueado_hasta        TIMESTAMPTZ,
    creado_en              TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_usuarios_telefono ON usuarios(telefono);

CREATE TABLE cursos (
    id            BIGSERIAL PRIMARY KEY,
    codigo        VARCHAR(30)   NOT NULL UNIQUE,
    nombre        VARCHAR(150)  NOT NULL,
    descripcion   TEXT,
    modalidad     VARCHAR(20)   NOT NULL CHECK (modalidad IN ('ONLINE','PRESENCIAL')),
    precio        NUMERIC(10,2) NOT NULL CHECK (precio > 0),
    moneda        VARCHAR(3)    NOT NULL DEFAULT 'PEN',
    fecha_inicio  DATE          NOT NULL,
    horario       VARCHAR(120),
    ubicacion     VARCHAR(255),                            -- aula o enlace de Zoom/Meet
    cupos         INT           NOT NULL CHECK (cupos > 0),
    activo        BOOLEAN       NOT NULL DEFAULT TRUE,
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE inscripciones (
    id                    BIGSERIAL PRIMARY KEY,
    usuario_id            BIGINT        NOT NULL REFERENCES usuarios(id),
    curso_id              BIGINT        NOT NULL REFERENCES cursos(id),
    estado                VARCHAR(20)   NOT NULL CHECK (estado IN ('PENDIENTE_PAGO','PAGADA','VENCIDA','CANCELADA')),
    monto                 NUMERIC(10,2) NOT NULL,
    moneda                VARCHAR(3)    NOT NULL,
    link_pago             VARCHAR(500),
    pago_id               VARCHAR(60),                     -- id en pagos-service
    mp_payment_id         VARCHAR(40),                     -- id del pago en Mercado Pago
    ultimo_estado_pago    VARCHAR(60),
    fecha_pago            TIMESTAMPTZ,
    correo_info_enviado   BOOLEAN       NOT NULL DEFAULT FALSE,   -- correo con informacion + link de pago
    correo_invitacion_enviado BOOLEAN   NOT NULL DEFAULT FALSE,   -- correo con invitacion (enlace/aula) tras pagar
    intentos_correo       INT           NOT NULL DEFAULT 0,
    recordatorio_enviado  BOOLEAN       NOT NULL DEFAULT FALSE,
    creado_en             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    actualizado_en        TIMESTAMPTZ   NOT NULL DEFAULT now()
);
-- Un estudiante no puede tener dos inscripciones vivas al mismo curso
CREATE UNIQUE INDEX uq_inscripcion_activa ON inscripciones(usuario_id, curso_id)
    WHERE estado IN ('PENDIENTE_PAGO','PAGADA');
CREATE INDEX idx_inscripciones_estado ON inscripciones(estado);

-- Patron Transactional Outbox: el evento se guarda en la MISMA transaccion
-- que el cambio de negocio y un proceso aparte lo publica en RabbitMQ.
CREATE TABLE outbox_eventos (
    id            BIGSERIAL PRIMARY KEY,
    evento_id     UUID          NOT NULL UNIQUE,
    tipo          VARCHAR(60)   NOT NULL,                  -- routing key: inscripcion.creada, curso.recordatorio
    payload       TEXT          NOT NULL,                  -- JSON del evento
    estado        VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE' CHECK (estado IN ('PENDIENTE','PUBLICADO','ERROR')),
    intentos      INT           NOT NULL DEFAULT 0,
    ultimo_error  VARCHAR(500),
    creado_en     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    publicado_en  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pendientes ON outbox_eventos(estado, creado_en) WHERE estado <> 'PUBLICADO';
