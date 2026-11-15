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
