-- =====================================================================
-- ESQUEMA COMPLETO de la plataforma (4 bases de datos, una por microservicio)
-- =====================================================================
-- SOLO PARA LEER / ENTREGAR. No lo ejecutes a mano: Flyway crea estas tablas
-- automaticamente al arrancar cada servicio, y si ya existen el servicio no arranca.
-- Generado a partir de <servicio>/src/main/resources/db/migration/*.sql


-- #####################################################################
-- BASE: matricula_db   (microservicio matricula-service)
-- #####################################################################

-- ---- V1__esquema_matricula.sql ----
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

-- ---- V2__datos_iniciales.sql ----
-- Catalogo de ejemplo. Las fechas son relativas a hoy para poder probar el recordatorio.
-- (El usuario ADMIN lo crea la aplicacion al arrancar con ADMIN_EMAIL / ADMIN_PASSWORD)
INSERT INTO cursos (codigo, nombre, descripcion, modalidad, precio, moneda, fecha_inicio, horario, ubicacion, cupos) VALUES
('SPR-01', 'Spring Boot desde cero', 'APIs REST, JPA, seguridad con JWT y microservicios.', 'ONLINE', 250.00, 'PEN',
  CURRENT_DATE + 1,  'Lun y Mie 7:00 pm - 10:00 pm', 'https://meet.google.com/aaa-bbbb-ccc', 30),
('ANG-01', 'Angular profesional', 'Componentes, servicios, rutas, guards e interceptores.', 'ONLINE', 220.00, 'PEN',
  CURRENT_DATE + 7,  'Mar y Jue 7:00 pm - 10:00 pm', 'https://meet.google.com/ddd-eeee-fff', 30),
('PG-01',  'PostgreSQL para desarrolladores', 'Modelado, indices, transacciones y optimizacion.', 'PRESENCIAL', 180.00, 'PEN',
  CURRENT_DATE + 14, 'Sabados 9:00 am - 1:00 pm', 'Sede Centro - Aula 204', 20);

-- #####################################################################
-- BASE: pagos_db   (microservicio pagos-service)
-- #####################################################################

-- ---- V1__esquema_pagos.sql ----
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

-- #####################################################################
-- BASE: notificaciones_db   (microservicio notificaciones-service)
-- #####################################################################

-- ---- V1__esquema_notificaciones.sql ----
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

-- #####################################################################
-- BASE: comprobantes_db   (microservicio comprobantes-service)
-- #####################################################################

-- ---- V1__esquema_comprobantes.sql ----
-- =====================================================================
-- comprobantes_db : comprobantes de pago (PDF) y estado del envio por correo
-- =====================================================================

CREATE SEQUENCE comprobante_correlativo_seq START 1;

CREATE TABLE comprobantes (
    id                BIGSERIAL     PRIMARY KEY,
    serie             VARCHAR(4)    NOT NULL,
    correlativo       BIGINT        NOT NULL,
    numero            VARCHAR(20)   NOT NULL UNIQUE,         -- B001-00000001
    inscripcion_id    BIGINT        NOT NULL UNIQUE,         -- idempotencia: 1 comprobante por inscripcion
    evento_id         UUID,
    pago_id           VARCHAR(60),
    mp_payment_id     VARCHAR(40),
    usuario_id        BIGINT        NOT NULL,
    estudiante_nombre VARCHAR(120)  NOT NULL,
    email             VARCHAR(150)  NOT NULL,
    telefono          VARCHAR(20),
    documento         VARCHAR(20),
    curso_id          BIGINT,
    curso_nombre      VARCHAR(150)  NOT NULL,
    monto             NUMERIC(10,2) NOT NULL,
    moneda            VARCHAR(3)    NOT NULL,
    metodo_pago       VARCHAR(40),
    fecha_pago        TIMESTAMPTZ,
    fecha_emision     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    estado_envio      VARCHAR(15)   NOT NULL DEFAULT 'PENDIENTE'
                      CHECK (estado_envio IN ('PENDIENTE','ENVIADO','ERROR','FALLIDO')),
    intentos_envio    INT           NOT NULL DEFAULT 0,
    ultimo_error      VARCHAR(500),
    proximo_intento   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    enviado_en        TIMESTAMPTZ,
    UNIQUE (serie, correlativo)
);
CREATE INDEX idx_comprobantes_envio ON comprobantes(estado_envio, proximo_intento)
    WHERE estado_envio IN ('PENDIENTE','ERROR');
CREATE INDEX idx_comprobantes_usuario ON comprobantes(usuario_id);
CREATE INDEX idx_comprobantes_telefono ON comprobantes(telefono);

-- El PDF va en tabla aparte para no cargarlo en cada listado
CREATE TABLE comprobante_pdf (
    comprobante_id  BIGINT  PRIMARY KEY REFERENCES comprobantes(id) ON DELETE CASCADE,
    contenido       BYTEA   NOT NULL
);
