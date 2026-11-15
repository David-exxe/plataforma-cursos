/** Desarrollo: el frontend solo conoce el API Gateway (único puerto expuesto del backend). */
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080',
  /** Debe coincidir con app.inscripcion-horas-vencimiento de matricula-service. */
  horasReserva: 72,
};
