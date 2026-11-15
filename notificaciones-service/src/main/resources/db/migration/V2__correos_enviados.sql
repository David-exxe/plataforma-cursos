-- Correos de confirmacion enviados. UNIQUE (evento_id, tipo) = idempotencia:
-- RabbitMQ entrega "al menos una vez", asi que el mismo pago.aprobado puede llegar dos veces
-- y el estudiante no debe recibir dos correos.
CREATE TABLE correos_enviados (
    id            BIGSERIAL    PRIMARY KEY,
    evento_id     UUID         NOT NULL,
    tipo          VARCHAR(40)  NOT NULL,
    destinatario  VARCHAR(150) NOT NULL,
    asunto        VARCHAR(250) NOT NULL,
    enviado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_correo_evento_tipo UNIQUE (evento_id, tipo)
);
