-- Para Postgres instalado en tu PC (sin Docker). Ejecutar como superusuario:
--   psql -U postgres -f scripts/db/crear-bases-local.sql
-- Las TABLAS las crea Flyway automaticamente al arrancar cada servicio
-- (ver <servicio>/src/main/resources/db/migration/V1__*.sql).
CREATE USER matricula_user      WITH PASSWORD 'matricula_pass';
CREATE USER pagos_user          WITH PASSWORD 'pagos_pass';
CREATE USER notificaciones_user WITH PASSWORD 'notificaciones_pass';
CREATE USER comprobantes_user   WITH PASSWORD 'comprobantes_pass';

CREATE DATABASE matricula_db      OWNER matricula_user;
CREATE DATABASE pagos_db          OWNER pagos_user;
CREATE DATABASE notificaciones_db OWNER notificaciones_user;
CREATE DATABASE comprobantes_db   OWNER comprobantes_user;

REVOKE ALL ON DATABASE matricula_db      FROM PUBLIC;
REVOKE ALL ON DATABASE pagos_db          FROM PUBLIC;
REVOKE ALL ON DATABASE notificaciones_db FROM PUBLIC;
REVOKE ALL ON DATABASE comprobantes_db   FROM PUBLIC;
