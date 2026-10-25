-- Catalogo de ejemplo. Las fechas son relativas a hoy para poder probar el recordatorio.
-- (El usuario ADMIN lo crea la aplicacion al arrancar con ADMIN_EMAIL / ADMIN_PASSWORD)
INSERT INTO cursos (codigo, nombre, descripcion, modalidad, precio, moneda, fecha_inicio, horario, ubicacion, cupos) VALUES
('SPR-01', 'Spring Boot desde cero', 'APIs REST, JPA, seguridad con JWT y microservicios.', 'ONLINE', 250.00, 'PEN',
  CURRENT_DATE + 1,  'Lun y Mie 7:00 pm - 10:00 pm', 'https://meet.google.com/aaa-bbbb-ccc', 30),
('ANG-01', 'Angular profesional', 'Componentes, servicios, rutas, guards e interceptores.', 'ONLINE', 220.00, 'PEN',
  CURRENT_DATE + 7,  'Mar y Jue 7:00 pm - 10:00 pm', 'https://meet.google.com/ddd-eeee-fff', 30),
('PG-01',  'PostgreSQL para desarrolladores', 'Modelado, indices, transacciones y optimizacion.', 'PRESENCIAL', 180.00, 'PEN',
  CURRENT_DATE + 14, 'Sabados 9:00 am - 1:00 pm', 'Sede Centro - Aula 204', 20);
