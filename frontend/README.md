# Academia · Frontend (Angular 20)

Frontend de la plataforma de matrícula de cursos. Consume **únicamente el API Gateway** (`:8080`), igual que
describe `GUIA.md`; no conoce los microservicios internos.

## Cómo ejecutarlo

Requisitos: Node.js 20.19+ (o 22.12+) y npm.

```bash
cd frontend
npm install
npm start            # http://localhost:4200
```

El backend debe estar arriba (`docker compose up --build`) con `FRONTEND_URL=http://localhost:4200` en tu `.env`
(es el origen que el gateway permite por CORS). Para probar el pago sin cuenta de Mercado Pago deja `MP_MODO=mock`.

Cambia la URL del gateway en `src/environments/environment.ts` (desarrollo) y `environment.prod.ts` (producción).

### Probar el flujo completo (modo mock)

1. Entra como administrador con `ADMIN_EMAIL` / `ADMIN_PASSWORD` de tu `.env` → `/admin`.
2. Crea un curso (Cursos → Nuevo curso) con fecha de inicio de hoy o posterior.
3. Cierra sesión, crea una cuenta de estudiante y reserva el cupo en `/cursos/:id`.
4. En *Mis inscripciones* pulsa **Pagar ahora** → llegas a `/pago-simulado` → **Aprobar pago**.
5. `/pago/resultado` muestra el avance en vivo: pago → matrícula → comprobante PDF.
6. Vuelve al panel admin para ver el pago, el comprobante y las notificaciones de WhatsApp.

### Con Docker

Agrega este servicio a tu `docker-compose.yml` (el puerto 4200 coincide con `FRONTEND_URL`):

```yaml
  frontend:
    build:
      context: ./frontend
      args:
        API_URL: http://localhost:8080   # URL del gateway tal como la ve el NAVEGADOR
    ports:
      - "4200:80"
    depends_on:
      - api-gateway
```

## Qué hay en cada pantalla (y qué endpoint usa)

| Ruta | Acceso | Endpoints |
|---|---|---|
| `/cursos`, `/cursos/:id` | público | `GET /api/cursos`, `GET /api/cursos/{id}` |
| `/ingresar` | invitado | `POST /api/auth/login`, `POST /api/auth/2fa/verificar` (código TOTP) |
| `/registro` | invitado | `POST /api/auth/registro` (+ login automático) |
| `/mis-inscripciones` | sesión | `GET /api/inscripciones/mias`, `POST …/{id}/link-pago`, `POST …/{id}/cancelar`, `GET /api/comprobantes/mios` |
| `/pago/resultado` | sesión | `GET /api/pagos/inscripcion/{id}` + sondeo de inscripción y comprobante cada 3 s |
| `/pago-simulado` | sesión | `POST /api/pagos/simulacion/{id}?resultado=approved\|rejected` |
| `/comprobantes` | sesión | `GET /api/comprobantes/mios`, `GET /api/comprobantes/{id}/pdf` |
| `/cuenta` | sesión | `GET /api/auth/me`, `POST /api/auth/2fa/configurar\|activar\|desactivar` |
| `/admin/resumen` | ADMIN | las seis fuentes siguientes en paralelo; si un servicio cae, el resto sigue |
| `/admin/cursos` | ADMIN | `GET/POST /api/admin/cursos`, `PUT /api/admin/cursos/{id}` |
| `/admin/inscripciones` | ADMIN | `GET /api/admin/inscripciones?estado=` |
| `/admin/pagos` | ADMIN | `GET /api/admin/pagos?estado=` |
| `/admin/comprobantes` | ADMIN | `GET /api/admin/comprobantes?estado=`, `POST …/{id}/reenviar` |
| `/admin/notificaciones` | ADMIN | `GET /api/admin/notificaciones?estado=`, `POST …/{id}/reintentar`, `POST /api/admin/recordatorios/ejecutar` |
| `/admin/seguridad` | ADMIN | `GET/POST /gateway/admin/ips`, `DELETE /gateway/admin/ips/{ip}` |

Las rutas `/pago/resultado`, `/pago-simulado` y `/mis-inscripciones` son las que el backend ya usa en las
`back_urls` de Mercado Pago, el link de pago simulado y los mensajes de WhatsApp.

## Decisiones técnicas que conviene conocer

- **Token y rutas públicas.** El interceptor no envía el JWT a `/api/cursos*` ni a login/registro: Spring Security
  responde 401 a un Bearer vencido incluso en rutas `permitAll`, y eso rompería el catálogo con una sesión caducada.
- **Expiración.** El JWT dura 60 min y no hay refresh. La sesión se limpia sola al vencer; cualquier 401 en una ruta
  protegida redirige a `/ingresar?returnUrl=…` y vuelve a la pantalla original tras entrar.
- **Fechas.** `fechaInicio` es un `LocalDate`: se parsea sin zona horaria. `new Date('2026-10-28')` mostraría el
  día anterior en Lima. Los instantes (`creadoEn`, `fechaPago`…) se muestran en `America/Lima`.
- **Errores.** Todo error HTTP llega a los componentes como `ApiError` con mensaje en español. Los 400 con `detalle`
  se pintan en el campo correspondiente del formulario.
- **IP bloqueada.** Si el gateway responde 403 «IP bloqueada» o 429, aparece un aviso global con la hora de desbloqueo.
- **Estado de la UI.** Signals en todos los componentes; el proyecto usa zone.js, pero migrar a *zoneless* es directo.
- **`strictTemplates`** está en `false` en `tsconfig.json`. Puedes ponerlo en `true` cuando quieras más verificación.

## Estructura

```
src/app/
├─ core/        modelos, interceptores, AuthService, guards, servicios por microservicio
├─ shared/      componentes reutilizables (boleto de curso, medidor de cupos, modal, iconos, validadores…)
├─ layout/      shell del sitio público y shell del panel admin
└─ features/    auth · cursos · inscripciones · pagos · comprobantes · cuenta · admin
```
