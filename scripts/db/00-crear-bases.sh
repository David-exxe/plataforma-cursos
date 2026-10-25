#!/bin/sh
# Se ejecuta UNA vez cuando el contenedor de Postgres arranca con el volumen vacio.
# Crea una base y un usuario por microservicio (Database per Service):
# ningun servicio puede leer ni escribir las tablas de otro.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-SQL
  CREATE USER matricula_user      WITH PASSWORD '${MATRICULA_DB_PASSWORD}';
  CREATE USER pagos_user          WITH PASSWORD '${PAGOS_DB_PASSWORD}';
  CREATE USER notificaciones_user WITH PASSWORD '${NOTIFICACIONES_DB_PASSWORD}';
  CREATE USER comprobantes_user   WITH PASSWORD '${COMPROBANTES_DB_PASSWORD}';

  CREATE DATABASE matricula_db      OWNER matricula_user;
  CREATE DATABASE pagos_db          OWNER pagos_user;
  CREATE DATABASE notificaciones_db OWNER notificaciones_user;
  CREATE DATABASE comprobantes_db   OWNER comprobantes_user;

  REVOKE ALL ON DATABASE matricula_db      FROM PUBLIC;
  REVOKE ALL ON DATABASE pagos_db          FROM PUBLIC;
  REVOKE ALL ON DATABASE notificaciones_db FROM PUBLIC;
  REVOKE ALL ON DATABASE comprobantes_db   FROM PUBLIC;
SQL
