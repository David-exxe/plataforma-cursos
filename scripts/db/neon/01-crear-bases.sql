-- =====================================================================
-- NeonDB: crear UNA base de datos por microservicio (Database per Service)
-- =====================================================================
-- Forma recomendada (sin SQL): Neon Console > tu proyecto > Branches > main > Databases > "New database"
--   matricula_db, pagos_db, notificaciones_db, comprobantes_db   (owner: neondb_owner)
--
-- Forma con SQL: en el SQL Editor de Neon (conectado a la base "neondb"), ejecuta
-- UNA sentencia a la vez (CREATE DATABASE no puede ir dentro de una transaccion):

CREATE DATABASE matricula_db;
CREATE DATABASE pagos_db;
CREATE DATABASE notificaciones_db;
CREATE DATABASE comprobantes_db;

-- NO crees las tablas a mano: cada microservicio las crea solo al arrancar con Flyway
-- (archivos <servicio>/src/main/resources/db/migration/V1__*.sql).
-- El esquema completo, para leerlo o entregarlo, esta en scripts/db/esquema-completo.sql
