-- =====================================================================
-- OPCIONAL: un usuario (rol) distinto por microservicio, para que ninguno
-- pueda tocar la base de otro. Es lo mas "correcto" en microservicios.
-- =====================================================================
-- Forma recomendada: Neon Console > Branches > main > Roles > "New role"
--   (Neon genera una contrasena segura) y luego en Databases crea cada base
--   eligiendo como Owner su rol: matricula_db -> matricula_user, etc.
--
-- Forma con SQL (en el SQL Editor, conectado a neondb). Neon exige contrasenas
-- fuertes (60 bits de entropia o mas): usa algo como `openssl rand -base64 18`.
-- Ejecuta una sentencia a la vez.

CREATE ROLE matricula_user      WITH LOGIN PASSWORD 'CAMBIAR_clave_larga_1';
CREATE ROLE pagos_user          WITH LOGIN PASSWORD 'CAMBIAR_clave_larga_2';
CREATE ROLE notificaciones_user WITH LOGIN PASSWORD 'CAMBIAR_clave_larga_3';
CREATE ROLE comprobantes_user   WITH LOGIN PASSWORD 'CAMBIAR_clave_larga_4';

-- Si las bases ya existen con owner neondb_owner, da permisos al rol de cada servicio.
-- Conectate a CADA base (selector de base del SQL Editor) y ejecuta su bloque:

-- En matricula_db:
GRANT ALL ON SCHEMA public TO matricula_user;
-- En pagos_db:
GRANT ALL ON SCHEMA public TO pagos_user;
-- En notificaciones_db:
GRANT ALL ON SCHEMA public TO notificaciones_user;
-- En comprobantes_db:
GRANT ALL ON SCHEMA public TO comprobantes_user;

-- Y quita el acceso de los demas a cada base (ejecutar en neondb):
REVOKE CONNECT ON DATABASE matricula_db      FROM PUBLIC;
REVOKE CONNECT ON DATABASE pagos_db          FROM PUBLIC;
REVOKE CONNECT ON DATABASE notificaciones_db FROM PUBLIC;
REVOKE CONNECT ON DATABASE comprobantes_db   FROM PUBLIC;
GRANT  CONNECT ON DATABASE matricula_db      TO matricula_user;
GRANT  CONNECT ON DATABASE pagos_db          TO pagos_user;
GRANT  CONNECT ON DATABASE notificaciones_db TO notificaciones_user;
GRANT  CONNECT ON DATABASE comprobantes_db   TO comprobantes_user;
