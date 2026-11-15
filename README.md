# Academia+ — Plataforma de venta de cursos (microservicios)

Frontend **Angular 20** + backend **Spring Boot 3.5** con API Gateway, 4 microservicios independientes
(matrícula, pagos con Mercado Pago, notificaciones/chatbot de WhatsApp, comprobantes PDF),
PostgreSQL (local o **NeonDB**) y RabbitMQ (local o CloudAMQP). Desplegable en **Render**, un Web Service por microservicio.

**Guía completa paso a paso: [GUIA.md](GUIA.md)**

```bash
./scripts/generar-secretos.sh
docker compose up -d --build
# Abre http://localhost:4200
```

| Carpeta | Qué es |
|---|---|
| `frontend/` | Angular (academia-web): catálogo, inscripción, pago, comprobantes, cuenta/2FA, panel admin |
| `api-gateway/` | Puerta de entrada: JWT, CORS, bloqueo de IPs, circuit breaker, estado del sistema |
| `matricula-service/` | Cuentas, 2FA, cursos, inscripciones |
| `pagos-service/` | Mercado Pago (preferencias, webhooks, conciliación) |
| `notificaciones-service/` | WhatsApp Cloud API y chatbot |
| `comprobantes-service/` | PDF y envío por correo con reintentos |
| `scripts/` | Bases de datos (local y Neon), secretos, pruebas |
| `render.yaml` | Despliegue independiente de cada servicio en Render |
