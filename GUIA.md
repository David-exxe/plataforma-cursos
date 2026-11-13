# Plataforma de venta de cursos — Microservicios + Frontend Angular

Angular 20 · Spring Boot 3.5 · Java 21 · PostgreSQL (local o NeonDB) · RabbitMQ (local o CloudAMQP) · Spring Cloud Gateway + Resilience4j · Mercado Pago · WhatsApp Cloud API · Render

**Índice rápido:** [Frontend (vistas)](#12-frontend-angular) · [Correr en tu PC](#6-cómo-ejecutarlo) · [NeonDB](#13-base-de-datos-en-neondb) · [Despliegue independiente en Render](#14-despliegue-independiente-en-render) · [Probar la tolerancia a fallos](#15-probar-que-un-servicio-caído-no-tumba-a-los-demás)

> **Sobre "n2f":** lo interpreté como **2FA (autenticación de dos factores)**. Está implementado con códigos TOTP de 6 dígitos (Google Authenticator / Microsoft Authenticator). Si tu profesor se refería a otra cosa, avísame.

---

## 1. Análisis de la casuística

### 1.1 Problema actual
La empresa trabaja a mano: registra inscripciones, revisa comprobantes uno por uno y responde WhatsApp con el mismo personal. Al crecer las matrículas:

| Síntoma | Causa | Qué lo resuelve en el sistema |
|---|---|---|
| Comprobantes salen tarde | Se arman y envían a mano | `comprobantes-service` genera el PDF y lo envía apenas Mercado Pago confirma |
| Estudiantes que "nunca recibieron confirmación" | No hay registro ni reintento | Cada envío (correo/WhatsApp) se **guarda con estado** y se **reintenta** |
| Consultas sin respuesta por horas | Un solo canal humano | Bot de WhatsApp que responde estado, horarios, cursos y reenvía comprobantes |
| Todo escala junto | Monolito manual | Microservicios desplegables por separado |

### 1.2 Actores
| Actor | Tipo | Qué hace |
|---|---|---|
| Estudiante | Humano | Se registra, se inscribe, paga, consulta por WhatsApp |
| Administrador | Humano | Gestiona cursos, revisa pagos/comprobantes, reenvía, desbloquea IPs |
| Mercado Pago | Sistema externo | Cobra y notifica el resultado (webhook) |
| WhatsApp Cloud API (Meta) | Sistema externo | Entrega mensajes y nos reenvía lo que escribe el estudiante |
| Servidor SMTP | Sistema externo | Entrega correos (Mailpit en desarrollo, Gmail/SES en producción) |
| Frontend Angular | Sistema interno | Consume solo el API Gateway |

### 1.3 Requerimientos funcionales
- **RF01** Registro e inicio de sesión con JWT; 2FA opcional (obligatorio recomendado para ADMIN).
- **RF02** Catálogo público de cursos (online/presencial) con cupos disponibles.
- **RF03** Inscripción a un curso con control de cupos y sin duplicados.
- **RF04** Cobro mediante Mercado Pago Checkout Pro.
- **RF05** Correo con información (al inscribirse) e invitación (al pagar).
- **RF06** WhatsApp en cada momento importante: inscripción recibida, pago recibido/rechazado, recordatorio un día antes del inicio, comprobante enviado.
- **RF07** Comprobante PDF enviado automáticamente al correo y descargable desde la web.
- **RF08** Bot de WhatsApp para consultas frecuentes y derivación a un asesor.
- **RF09** Panel admin: cursos, inscripciones, pagos, comprobantes, notificaciones, IPs bloqueadas.

### 1.4 Requerimientos no funcionales
- **Escalabilidad independiente**: cada área (catálogo/matrícula, pagos, notificaciones, comprobantes) es un servicio con su propia base de datos.
- **Confiabilidad**: ningún evento se pierde (outbox + colas durables + DLQ); todos los consumidores son idempotentes.
- **Seguridad**: JWT, CORS, bloqueo de IPs, 2FA, BCrypt, firmas HMAC en webhooks, clave interna entre servicios, secreto 2FA cifrado con AES-GCM.

---

## 2. Arquitectura

```
        Navegador ──► Frontend Angular (static site / Nginx :4200)
                              │  HTTPS + JWT  (solo conoce al gateway)
  Mercado Pago ──┐            ▼
  WhatsApp Meta ─┼──► ┌───────────────────────────────────────────────┐
                 └──► │ API GATEWAY :8080                             │
                      │ bloqueo IPs → JWT → CORS → rutas              │
                      │ circuit breaker POR servicio + fallback 503   │
                      │ /api/estado (salud de todos)                  │
                      └──┬──────────┬──────────────┬──────────────┬───┘
                         ▼          ▼              ▼              ▼
              ┌──────────────┐ ┌──────────┐ ┌────────────────┐ ┌──────────────┐
              │ matricula    │►│ pagos    │ │ notificaciones │ │ comprobantes │
              │ auth+2FA     │ │ Mercado  │ │ WhatsApp + bot │ │ PDF + correo │
              │ cursos, insc.│ │ Pago     │ │                │ │              │
              └──────┬───────┘ └────┬─────┘ └───────▲────────┘ └──────▲───────┘
                     └──────────────┴──► RabbitMQ "cursos.eventos" ◄──┘
                                         (colas durables + DLQ)
        matricula_db        pagos_db       notificaciones_db      comprobantes_db
        (PostgreSQL local o NeonDB: una base de datos por servicio)
```

Cada caja es un **proceso independiente** (su propio contenedor, puerto, base de datos y despliegue). En Render cada una es un **Web Service** distinto con su propia URL: si uno se cae, los demás siguen funcionando (sección 15).

**¿Por qué síncrono en un solo punto?** Al inscribirse, el estudiante necesita el link de pago en la misma respuesta, así que `matricula → pagos` es REST (con timeouts cortos). Todo lo demás (avisar, emitir comprobante, marcar pagada) es **asíncrono por eventos**: si un servicio está caído, el mensaje espera en su cola.

| Servicio | Puerto | Base de datos | Publica | Consume |
|---|---|---|---|---|
| api-gateway | 8080 | — | — | — |
| matricula-service | 8081 | matricula_db | `inscripcion.creada`, `curso.recordatorio` | `pago.aprobado`, `pago.rechazado` |
| pagos-service | 8082 | pagos_db | `pago.aprobado`, `pago.rechazado`, `pago.reembolsado` | — (webhook de MP) |
| notificaciones-service | 8083 | notificaciones_db | — | los 6 eventos de la tabla 3 |
| comprobantes-service | 8084 | comprobantes_db | `comprobante.enviado`, `comprobante.fallido` | `pago.aprobado` |

---

## 3. Qué información viaja en cada mensaje

Todos los eventos usan el mismo **sobre**:
```json
{
  "eventoId": "4f1c...-uuid",       // para idempotencia
  "tipo": "pago.aprobado",          // = routing key en RabbitMQ
  "version": 1,
  "origen": "pagos-service",
  "ocurridoEn": "2026-10-21T22:40:00Z",
  "datos": { ... }
}
```

| Evento | Lo publica | Lo consume | `datos` |
|---|---|---|---|
| `inscripcion.creada` | matricula | notificaciones | inscripcionId, estudiante{usuarioId, nombre, email, telefono}, curso{id, codigo, nombre, modalidad, fechaInicio, horario}, monto, moneda, linkPago |
| `pago.aprobado` | pagos | matricula, comprobantes, notificaciones | pagoId, mpPaymentId, inscripcionId, monto, moneda, metodoPago, fechaAprobacion, estudiante{usuarioId, nombre, email, telefono, documento}, curso{id, nombre} |
| `pago.rechazado` | pagos | matricula, notificaciones | igual que el anterior + motivo (status_detail de MP) + linkPago para reintentar |
| `curso.recordatorio` | matricula (9:00 a.m.) | notificaciones | inscripcionId, estudiante, curso{… + ubicacion (enlace o aula)} |
| `comprobante.enviado` | comprobantes | notificaciones | comprobanteId, numero, inscripcionId, nombre, email, telefono, cursoNombre |
| `comprobante.fallido` | comprobantes | notificaciones (alerta al ADMIN) | lo anterior + intentos, ultimoError |

**Mensajes síncronos (REST interno, cabecera `X-Internal-Key`):**

| Llamada | Request | Response |
|---|---|---|
| matricula → `POST pagos/internal/pagos/preferencias` | inscripcionId, usuarioId, datos del estudiante, cursoId, cursoNombre, monto, moneda | pagoId, preferenceId, linkPago, estado |
| bot → `GET matricula/internal/consultas/estudiante?telefono=` | teléfono WhatsApp | nombre, inscripciones[] |
| bot → `GET matricula/internal/cursos` | — | catálogo |
| bot → `POST comprobantes/internal/comprobantes/reenviar-por-telefono/{tel}` | teléfono | reenviado, numero, email |

**Mensajes con Mercado Pago:**
- Nosotros → MP `POST /checkout/preferences`: items (curso, precio), payer (nombre, email), **external_reference = inscripcionId**, notification_url, back_urls, cabecera `X-Idempotency-Key`.
- MP → nosotros `POST /webhooks/mercadopago?data.id=123&type=payment` con cabeceras `x-signature` y `x-request-id`. **No se confía en el cuerpo**: se valida la firma y luego se consulta `GET /v1/payments/{id}`.

---

## 4. Qué hace el sistema cuando algo falla

| Escenario | Qué pasa |
|---|---|
| **Mercado Pago confirmó el pago pero el correo con el comprobante no salió** (caso del enunciado) | 1) El comprobante y su PDF se guardan **antes** de intentar el correo. 2) `estado_envio = ERROR` y se reintenta a los 1, 5, 15, 60 min. 3) Al 5.º fallo → `FALLIDO` + evento `comprobante.fallido` → WhatsApp de alerta al admin. 4) El admin reenvía desde `POST /api/admin/comprobantes/{id}/reenviar`; el estudiante puede pedirlo al bot (opción 4) o descargarlo en `GET /api/comprobantes/{id}/pdf`. El pago y la matrícula **ya están confirmados** independientemente del correo. |
| pagos-service caído al inscribirse | La inscripción se guarda igual (PENDIENTE_PAGO, sin link). El estudiante regenera el link con `POST /api/inscripciones/{id}/link-pago`. |
| RabbitMQ caído | Los eventos quedan en la tabla `outbox_eventos` (se guardaron en la misma transacción que el cambio). El publicador reintenta cada 2 s hasta que vuelva. |
| Un consumidor lanza error (BD caída, bug) | Spring AMQP reintenta 4 veces (2 s, 6 s, 18 s); luego el mensaje va a la **DLQ** (`*.dlq`) para revisarlo en la consola de RabbitMQ. |
| Mercado Pago envía el mismo webhook 2 veces | El pago ya está APROBADO → no se publica nada nuevo. Comprobantes: 1 por inscripción (UNIQUE). Notificaciones: UNIQUE(evento_id, tipo). |
| Se pierde un webhook (servidor caído, ngrok cerrado) | Respondemos 500 cuando no pudimos procesar → MP reintenta (15 min, 30 min, 6 h…). Además, cada 15 min la **conciliación** consulta a MP los pagos pendientes por `external_reference`. |
| Webhook falso ("pago aprobado" inventado) | Firma `x-signature` inválida → 401. Y aunque pasara, consultamos el pago real a la API de MP. |
| Pago por un monto distinto | Se compara `transaction_amount` y moneda contra lo cobrado; si no coincide NO se aprueba y se registra alerta. |
| Webhooks desordenados (rechazado llega después de aprobado) | Un pago APROBADO no retrocede; solo puede pasar a REEMBOLSADO. |
| WhatsApp falla (token vencido, número inválido) | La notificación queda PENDIENTE con reintentos 1, 2, 4, 8 min; luego FALLIDO (visible en `/api/admin/notificaciones`, reintento manual). |
| Mensaje fuera de la ventana de 24 h de WhatsApp | Meta responde error 131047 → activar `WA_USAR_PLANTILLAS=true` con plantillas aprobadas (sección 8). |
| Dos personas toman el último cupo a la vez | `SELECT … FOR UPDATE` sobre el curso: se procesan en fila, la segunda recibe 409. |
| El estudiante no paga | A las 72 h la inscripción pasa a VENCIDA y libera el cupo. Si paga después, se respeta (entró dinero) y se registra advertencia. |

---

## 5. Implementación paso a paso

### Paso 1 — Proyecto multi-módulo
`pom.xml` raíz con `spring-boot-starter-parent 3.5.6`, BOM de Spring Cloud `2025.0.0` y 5 módulos. Cada módulo es un JAR ejecutable independiente.
```
plataforma-cursos/
├── pom.xml                  (padre)
├── api-gateway/
├── matricula-service/
├── pagos-service/
├── notificaciones-service/
├── comprobantes-service/
├── frontend/                (Angular 20: vistas de estudiante y panel admin)
├── scripts/                 (BD local y Neon, secretos, prueba de flujo, webhook firmado)
├── docker-compose.yml       (todo local: infraestructura + 5 servicios + frontend)
├── render.yaml              (despliegue independiente de cada servicio en Render)
└── .env.example
```
Cada microservicio tiene **su propio `Dockerfile`** (`<servicio>/Dockerfile`), así se construye y despliega por separado.
Dependencias por servicio: `web`, `data-jpa`, `validation`, `oauth2-resource-server` (JWT), `amqp`, `actuator`, `flyway`, `postgresql`, `lombok`; + `mail` (matrícula, comprobantes) + `openpdf` (comprobantes). Gateway: `spring-cloud-starter-gateway-server-webflux` + `oauth2-resource-server` + `spring-cloud-starter-circuitbreaker-reactor-resilience4j`.
Correo: clase `CorreoSender` con dos proveedores, `smtp` (local) o `brevo` (API HTTP, necesario en Render free porque bloquea los puertos SMTP).

### Paso 2 — PostgreSQL: una base por servicio
- `scripts/db/00-crear-bases.sh` (Docker) o `scripts/db/crear-bases-local.sql` (Postgres instalado): crea 4 usuarios y 4 bases; cada usuario solo puede conectarse a la suya.
- Las **tablas** las crea **Flyway** al arrancar cada servicio desde `src/main/resources/db/migration/V1__*.sql` (`ddl-auto: none`: Hibernate no toca el esquema).
- `V2__datos_iniciales.sql` carga 3 cursos con fechas relativas a hoy (uno empieza mañana para probar el recordatorio).
- `scripts/db/consultas-utiles.sql`: consultas para revisar/sustentar.

Tablas clave: `usuarios`, `cursos`, `inscripciones` (índice único parcial: una inscripción viva por estudiante y curso), `outbox_eventos`, `pagos` (UNIQUE inscripcion_id), `webhook_eventos`, `notificaciones` (UNIQUE evento_id+tipo), `mensajes_entrantes`, `comprobantes` (UNIQUE inscripcion_id, secuencia para el correlativo), `comprobante_pdf`.

### Paso 3 — RabbitMQ
- Exchange **topic** `cursos.eventos`; la routing key es el tipo de evento.
- Colas durables: `matricula.pagos.q`, `comprobantes.pagos.q`, `notificaciones.q`, cada una con `x-dead-letter-exchange = cursos.eventos.dlx` y su `*.dlq`.
- `default-requeue-rejected: false` + retry → tras 4 intentos el mensaje va a la DLQ (no se queda en bucle infinito).
- Publicación con **publisher confirms** (`waitForConfirmsOrDie`): solo marcamos PUBLICADO cuando el broker confirmó.

### Paso 4 — matricula-service
1. **Auth** (`auth/`): registro con BCrypt (cost 12), login, bloqueo de cuenta 15 min tras 5 intentos fallidos, mensaje genérico "Credenciales inválidas".
2. **JWT** (`TokenService`): HS256, emisor `plataforma-cursos`, audiencia `api`, claims `sub` (id), `email`, `nombre`, `roles`, `mfa`, expira en 60 min.
3. **2FA TOTP** (`TotpService`, RFC 6238, probado con los vectores oficiales):
   - `POST /api/auth/2fa/configurar` → `secreto` + `otpauthUri` (el front lo muestra como QR).
   - `POST /api/auth/2fa/activar {codigo}` → se activa solo si el código es válido.
   - Login con 2FA activo devuelve `{requiere2fa: true, tokenTemporal}` (audiencia `2fa-pendiente`, 5 min, **no sirve para la API**) → `POST /api/auth/2fa/verificar {tokenTemporal, codigo}` → JWT real.
   - El mismo código no se acepta dos veces (`totp_ultimo_paso`); el secreto se guarda cifrado con AES-256-GCM (`CifradoService`).
4. **Catálogo** (`curso/`): público con cupos disponibles; el enlace de clases online se oculta hasta pagar. CRUD admin.
5. **Inscripción** (`InscripcionService`): TX1 valida y guarda → llamada HTTP a pagos (fuera de transacción) → TX2 guarda link + evento en outbox → correo asíncrono.
6. **Outbox** (`outbox/`): `OutboxService.registrar()` exige transacción activa (`Propagation.MANDATORY`); `OutboxPublicador` usa `FOR UPDATE SKIP LOCKED` (varias instancias no publican lo mismo).
7. **Listener** `pago.aprobado` → PAGADA (idempotente) → correo de **invitación** con enlace/aula.
8. **Tareas programadas**: recordatorios 9:00 a.m. (Lima), vencimiento de pendientes cada hora, reintento de correos cada 10 min.

### Paso 5 — pagos-service (Mercado Pago)
1. `POST /internal/pagos/preferencias` → crea (o reutiliza) el `Pago` y la preferencia en MP; devuelve `init_point`.
2. `POST /webhooks/mercadopago` → valida `x-signature`:
   `manifest = "id:{data.id};request-id:{x-request-id};ts:{ts};"` → `HMAC-SHA256(manifest, clave secreta)` debe ser igual a `v1`.
3. Consulta `GET /v1/payments/{id}`, valida monto/moneda, aplica transición de estado y guarda el evento en el outbox **en la misma transacción** (con bloqueo de fila).
4. Conciliación cada 15 min con `GET /v1/payments/search?external_reference=`.
5. **Modo mock** (`MP_MODO=mock`): sin credenciales; `POST /api/pagos/simulacion/{inscripcionId}?resultado=approved|rejected` recorre exactamente el mismo código que un webhook real.

### Paso 6 — notificaciones-service (WhatsApp + bot)
1. Consume los 6 eventos, arma el texto (`Mensajes`), **guarda** la notificación y luego intenta enviarla (`WhatsAppClient` → `POST graph.facebook.com/v21.0/{phone-number-id}/messages`).
2. Reintentos con espera exponencial; estados ENVIADO → ENTREGADO → LEÍDO actualizados por el webhook de Meta.
3. Webhook `GET /webhooks/whatsapp` (verificación con `hub.challenge`) y `POST` (valida `X-Hub-Signature-256`).
4. **Bot** (`BotService`): menú 1-5 o palabras clave (cursos, estado/pago, horario/enlace, comprobante, asesor). Consulta a matrícula y comprobantes por la API interna; "asesor" envía WhatsApp al admin.
5. **Modo mock** (`WA_MODO=mock`): los mensajes se imprimen en el log del servicio.

### Paso 7 — comprobantes-service
1. Consume `pago.aprobado` → numera `B001-00000001` (secuencia de Postgres) → PDF con OpenPDF (datos del cliente, detalle, op. gravada, IGV 18 %, total, N.º de operación MP) → guarda todo.
2. Envía el correo con el PDF adjunto; máquina de estados `PENDIENTE → ENVIADO | ERROR → FALLIDO` (sección 4).
3. Publica `comprobante.enviado` / `comprobante.fallido`.

### Paso 8 — api-gateway
1. **Bloqueo de IPs** (`IpBloqueoFilter`, se ejecuta ANTES que la seguridad):
   - lista negra fija (`IP_LISTA_NEGRA`, acepta prefijos `203.0.113.`), lista blanca;
   - rate limit: más de `MAX_RPM` (120) peticiones/min → bloqueo 30 min (HTTP 429);
   - fuerza bruta: 5 respuestas 401 en `/api/auth/login` o `/2fa/verificar` en 15 min → bloqueo 30 min (HTTP 403);
   - admin: `GET/POST /gateway/admin/ips`, `DELETE /gateway/admin/ips/{ip}`.
2. **JWT**: valida firma, emisor, expiración y audiencia `api`. Rutas públicas: registro, login, 2FA-verificar, `GET /api/cursos/**`, `/webhooks/**`. `/api/admin/**` exige rol ADMIN.
3. **CORS**: solo `FRONTEND_URL` (http://localhost:4200), métodos y cabeceras explícitos, sin cookies. Solo el gateway pone cabeceras CORS (si los servicios también lo hicieran, el navegador vería cabeceras duplicadas y fallaría).
4. **Rutas**: elimina cualquier `X-Internal-Key` que venga de afuera; `/internal/**` nunca se publica.
5. **Circuit breaker por servicio** (`ResilienciaConfig`, Resilience4j): cada ruta tiene su propio circuito. Si de las últimas 10 llamadas a un servicio al menos la mitad fallan, el circuito se **abre** 20 s y el gateway responde al instante con `/fallback/{servicio}` (HTTP 503 y un mensaje claro) en vez de dejar al usuario esperando. Los demás circuitos siguen cerrados.
6. **Timeouts por ruta**: 90 s en la nube (un servicio dormido de Render tarda ~1 min en despertar), 20 s en Docker.
7. **`GET /api/estado`**: consulta en paralelo el `/actuator/health` de los 4 servicios (con timeout propio) y devuelve estado, latencia y componentes (BD, RabbitMQ). Sirve para revisar de un vistazo qué servicio está caído: ábrelo en el navegador.
8. **IP real detrás de un proxy**: `IP_CABECERAS` (en Render `CF-Connecting-IP,True-Client-IP,X-Forwarded-For`). `GET /api/mi-ip` muestra qué IP ve el gateway.

### Paso 9 — Resumen de seguridad
| Control | Dónde |
|---|---|
| JWT HS256 (emisor, audiencia, expiración) | Gateway + **cada** servicio (defensa en profundidad) |
| CORS | Gateway |
| Bloqueo de IPs / rate limit / anti fuerza bruta | Gateway |
| Bloqueo de cuenta tras 5 intentos | matricula |
| 2FA TOTP con anti-reuso | matricula |
| Contraseñas BCrypt(12), secretos 2FA AES-256-GCM | matricula |
| Clave interna entre servicios (comparación en tiempo constante) | `/internal/**` |
| Firma HMAC de webhooks (MP y Meta) | pagos, notificaciones |
| Validación de entrada (`@Valid`), errores sin detalles internos | todos |
| Usuario de BD distinto por servicio, contenedores sin root | Postgres, Dockerfile |
| Secretos solo por variables de entorno | `.env` (no se sube a Git) |

---

## 6. Cómo ejecutarlo

### Opción A — Todo con Docker (recomendada)
Requisitos: Docker Desktop abierto y Git Bash (Windows). En la carpeta del proyecto:
```bash
./scripts/generar-secretos.sh          # crea .env con secretos aleatorios
docker compose up -d --build           # 1.ª vez: 5–15 min (descarga y compila todo)
docker compose ps                      # 9 contenedores en "running"
```
| Qué | Dónde |
|---|---|
| **Frontend (la plataforma)** | http://localhost:4200 |
| API Gateway | http://localhost:8080 |
| Correos enviados (Mailpit) | http://localhost:8025 |
| RabbitMQ | http://localhost:15672 (usuario/clave de `.env`) |
| Admin | correo `ADMIN_EMAIL` y clave `ADMIN_PASSWORD` del `.env` |

Prueba por consola (opcional, necesita `jq`): `./scripts/probar-flujo.sh`. Su último paso bloquea tu IP a propósito; para desbloquear: `docker compose restart api-gateway`.

### Opción B — Frontend con `ng serve` y backend en Docker
Útil para modificar las vistas y verlas al instante:
```bash
docker compose up -d --build postgres rabbitmq mailpit api-gateway matricula-service pagos-service notificaciones-service comprobantes-service
cd frontend
npm install
npm start                              # http://localhost:4200 (recarga sola al guardar)
```

### Opción C — Sin Docker (VS Code / IntelliJ)
1. Instala PostgreSQL 16 (o usa NeonDB, sección 13) y RabbitMQ; para Postgres local: `psql -U postgres -f scripts/db/crear-bases-local.sql`.
2. Variables de entorno en cada servicio: `JWT_SECRET` e `INTERNAL_API_KEY` (iguales en todos), `RABBIT_URL`, y en matrícula `ENCRYPTION_KEY` y `ADMIN_PASSWORD`. Generarlas: `openssl rand -base64 64`, `openssl rand -hex 32`, `openssl rand -base64 32`.
3. `mvn clean package` en la raíz y arranca los 5 `*Application` (el gateway al final).
4. Frontend: `cd frontend && npm install && npm start`.

### Probar 2FA
En la web: **Mi cuenta** (`/cuenta`) → activar 2FA → escanea el QR con Google/Microsoft Authenticator → escribe el código. Desde ahí el login pide el código de 6 dígitos.

---

## 7. Mercado Pago real (sandbox)
1. Entra a https://www.mercadopago.com.pe/developers → **Tus integraciones** → crea una aplicación (Checkout Pro).
2. Crea **cuentas de prueba** (vendedor y comprador) y copia el **Access Token** de la cuenta vendedora.
3. Expón tu gateway: `ngrok http 8080` → copia la URL `https://xxxx.ngrok-free.app`.
4. En la aplicación → **Webhooks** → URL `https://xxxx.ngrok-free.app/webhooks/mercadopago`, evento **Pagos**. Copia la **clave secreta**.
5. En `.env`: `MP_MODO=real`, `MP_ACCESS_TOKEN=...`, `MP_WEBHOOK_SECRET=...`, `PUBLIC_URL=https://xxxx.ngrok-free.app` → `docker compose up -d`.
6. Inscríbete, abre `linkPago` con el usuario comprador de prueba y paga con las **tarjetas de prueba** de la documentación (el nombre del titular define el resultado: APRO = aprobado, OTHE = rechazado).
7. Revisa `webhook_eventos` en `pagos_db` para ver cada notificación recibida.
8. Probar la validación de firma sin MP: `./scripts/simular-webhook-mp.sh <payment_id> <clave_secreta>`.

## 8. WhatsApp Cloud API real
1. https://developers.facebook.com → crea una app tipo **Business** → agrega **WhatsApp**.
2. En **API Setup** copia el **Phone number ID** y un **token** (el temporal dura 24 h; para la demo final crea un token permanente de usuario del sistema). Agrega tu celular como número de prueba.
3. **Configuration → Webhook**: URL `https://xxxx.ngrok-free.app/webhooks/whatsapp`, Verify token = `WA_VERIFY_TOKEN`; suscríbete a `messages`. En **App settings → Basic** copia el **App Secret** → `WA_APP_SECRET`.
4. `.env`: `WA_MODO=real`, `WA_PHONE_NUMBER_ID`, `WA_ACCESS_TOKEN`, `WA_APP_SECRET`.
5. Escribe "hola" al número de prueba → el bot responde el menú.
6. **Regla de 24 h**: Meta solo deja enviar texto libre si el estudiante escribió en las últimas 24 h. Para avisos que inicia la empresa (pago recibido, recordatorio), crea en **Message Templates** plantillas en español con estos nombres y parámetros, y activa `WA_USAR_PLANTILLAS=true`:

| Plantilla | Parámetros {{1}}, {{2}}… |
|---|---|
| `inscripcion_recibida` | nombre, curso, link de pago |
| `pago_confirmado` | nombre, curso, monto |
| `pago_rechazado` | nombre, curso |
| `recordatorio_clase` | nombre, curso, horario, enlace/lugar |
| `comprobante_enviado` | nombre, número, email |
| `alerta_admin` | número, email |

## 9. Correo real

**En tu PC (SMTP con Gmail).** Activa verificación en 2 pasos en la cuenta → crea una **contraseña de aplicación** → en `.env`:
`MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USER=tucorreo@gmail.com`, `MAIL_PASSWORD=<contraseña de aplicación>`, `MAIL_AUTH=true`, `MAIL_TLS=true`, `MAIL_FROM=Academia <tucorreo@gmail.com>`.

**En Render (Brevo).** El plan gratuito de Render bloquea los puertos SMTP (25, 465 y 587), así que Gmail por SMTP no funciona ahí. Se usa la API HTTP de Brevo (gratis, 300 correos/día):
1. Crea una cuenta en https://www.brevo.com → **Senders, Domains & Dedicated IPs → Senders** → agrega y verifica tu correo remitente.
2. **SMTP & API → API Keys** → crea una clave.
3. Variables: `CORREO_PROVEEDOR=brevo`, `BREVO_API_KEY=<clave>`, `MAIL_FROM=Academia <correo-verificado@gmail.com>`.

---

## 10. Endpoints del API Gateway (los usa el frontend)

| Método | Ruta | Auth | Descripción |
|---|---|---|---|
| POST | /api/auth/registro | — | `{nombre, email, telefono, documento, password}` |
| POST | /api/auth/login | — | `{email, password}` → `accessToken` o `{requiere2fa, tokenTemporal}` |
| POST | /api/auth/2fa/verificar | — | `{tokenTemporal, codigo}` |
| GET | /api/auth/me | JWT | perfil |
| POST | /api/auth/2fa/configurar · activar · desactivar | JWT | gestión de 2FA |
| GET | /api/cursos · /api/cursos/{id} | — | catálogo |
| POST | /api/inscripciones | JWT | `{cursoId}` → incluye `linkPago` |
| GET | /api/inscripciones/mias · /{id} | JWT | mis inscripciones |
| POST | /api/inscripciones/{id}/link-pago · /cancelar | JWT | regenerar link / cancelar |
| GET | /api/pagos/inscripcion/{id} | JWT | estado del pago (página de retorno de MP) |
| POST | /api/pagos/simulacion/{id}?resultado=approved | JWT | solo modo mock |
| GET | /api/comprobantes/mios · /{id}/pdf | JWT | mis comprobantes / descargar PDF |
| GET/POST/PUT | /api/admin/cursos | ADMIN | CRUD de cursos |
| GET | /api/admin/inscripciones?estado= · /api/admin/pagos?estado= | ADMIN | listados |
| GET · POST | /api/admin/comprobantes?estado=ERROR · /{id}/reenviar | ADMIN | comprobantes con problemas |
| GET · POST | /api/admin/notificaciones · /{id}/reintentar | ADMIN | WhatsApp enviados/fallidos |
| POST | /api/admin/recordatorios/ejecutar | ADMIN | dispara recordatorios (demo) |
| GET · POST · DELETE | /gateway/admin/ips | ADMIN | IPs bloqueadas |
| POST · GET | /api/bot/mensaje · /api/bot/historial | JWT | chat web con el mismo bot de WhatsApp |
| GET | /api/estado · /api/mi-ip | — | salud de los 4 servicios / IP vista por el gateway |

---

## 11. Pendientes para producción (fuera del alcance de la prueba de concepto)
- El estado de IPs bloqueadas está en memoria del gateway: con varias instancias, moverlo a Redis.
- Refresh tokens y revocación de JWT (lista negra de `jti`).
- El PDF es un comprobante de control interno; la boleta/factura electrónica ante SUNAT requiere integrar un OSE/PSE.
- Pruebas de integración con Testcontainers y observabilidad (trazas distribuidas, métricas en Prometheus).
- En Render free los servicios se duermen: para producción real, plan pagado (siempre encendidos) o una sola instancia más grande.

---

## 12. Frontend Angular

Carpeta `frontend/` (proyecto `academia-web`, hecho por el equipo). Angular 20 con componentes standalone y lazy loading. Solo conoce **una URL**, la del API Gateway:
- **Desarrollo:** `src/environments/environment.ts` → `http://localhost:8080`.
- **Producción:** `src/environments/environment.prod.ts` trae `https://api.tu-dominio.com`. Al compilar, Docker y Render lo reemplazan por la variable `API_URL` (la URL real del gateway). No hace falta editarlo a mano.

### Vistas
| Ruta | Vista | Microservicio(s) que consume |
|---|---|---|
| `/` y `/cursos` | Catálogo de cursos | matricula |
| `/cursos/:id` | Detalle e inscripción | matricula → pagos |
| `/ingresar` · `/registro` | Ingreso (con código 2FA si está activo) y registro | matricula |
| `/mis-inscripciones` | Mis cursos: pagar, regenerar link, cancelar | matricula |
| `/pago-simulado` | Pasarela de prueba (cuando `MP_MODO=mock`) | pagos |
| `/pago/resultado` | Retorno de Mercado Pago | pagos + matricula |
| `/comprobantes` | Mis comprobantes y descarga del PDF | comprobantes |
| `/cuenta` | Mi cuenta y 2FA | matricula |
| `/admin/resumen`, `/admin/cursos`, `/admin/inscripciones`, `/admin/pagos`, `/admin/comprobantes`, `/admin/notificaciones`, `/admin/seguridad` | Panel administrador | los 4 + gateway |

Si el gateway bloquea la IP (403/429), el frontend lo muestra con un aviso global.

---

## 13. Base de datos en NeonDB

Neon es PostgreSQL administrado en la nube (plan gratis: 100 proyectos, 1 GB de almacenamiento y 100 horas de cómputo al mes por proyecto, se suspende tras 5 min sin uso y despierta en la siguiente conexión). El código no cambia: **solo cambian las variables `DB_URL`, `DB_USER` y `DB_PASSWORD`**.

1. Crea una cuenta en https://neon.com → **New project** (Postgres 17, región cercana a tu Render, ej. *AWS US West (Oregon)*).
2. Crea las 4 bases. La forma más fácil es **Branches → main → Databases → New database**: `matricula_db`, `pagos_db`, `notificaciones_db`, `comprobantes_db`. Con SQL: `scripts/db/neon/01-crear-bases.sql`, una sentencia a la vez.
3. *(Opcional, más aislamiento)* Un rol por servicio: `scripts/db/neon/02-roles-por-servicio.sql`, o **Roles → New role** en la consola.
4. Obtén la conexión con el botón **Connect** → elige la base → desactiva *Connection pooling* (Flyway necesita conexión directa) → copia host, usuario y contraseña.
5. Arma las variables de cada servicio (cambia el nombre de la base en cada uno):
   ```
   DB_URL=jdbc:postgresql://ep-xxxx-xxxx.us-west-2.aws.neon.tech/matricula_db?sslmode=require
   DB_USER=neondb_owner
   DB_PASSWORD=<la contraseña de Neon>
   ```
6. **Las tablas se crean solas**: al arrancar, cada servicio ejecuta sus migraciones Flyway (`db/migration/V1__*.sql`). No ejecutes el esquema a mano.
7. El esquema completo de las 4 bases, para leerlo o entregarlo, está en `scripts/db/esquema-completo.sql`.

**Probar Neon desde tu PC con Docker:** en `docker-compose.yml` cambia `DB_URL`, `DB_USER` y `DB_PASSWORD` del servicio por los de Neon (el contenedor `postgres` local deja de usarse).

---

## 14. Despliegue independiente en Render

Cada microservicio se despliega como un **Web Service separado**: su propio contenedor, URL, logs, variables y reinicios. Si uno falla o se reinicia, los demás siguen atendiendo. El frontend es un **Static Site** (gratis y no se duerme).

### Servicios externos gratuitos
| Pieza | Servicio | Por qué |
|---|---|---|
| PostgreSQL | **Neon** (sección 13) | Render solo da Postgres gratis por 30 días |
| RabbitMQ | **CloudAMQP**, plan *Little Lemur* | Render no ofrece RabbitMQ. Gratis: 20 conexiones, 1 M mensajes/mes |
| Correo | **Brevo** (sección 9) | Render free bloquea los puertos SMTP |

**CloudAMQP:** https://www.cloudamqp.com → *Create New Instance* → plan **Little Lemur (Free)** → elige una región → abre la instancia y copia la **AMQP URL** (empieza con `amqps://`). Esa es tu `RABBIT_URL`; Spring activa TLS solo por el `amqps://`.

### Paso a paso
1. **Sube el proyecto a GitHub** (con `frontend/`, `render.yaml` y los `Dockerfile` de cada servicio). Verifica que `.env` **no** se suba (está en `.gitignore`).
2. En https://dashboard.render.com → **New → Blueprint** → conecta el repositorio. Render lee `render.yaml` y propone 6 servicios: `cursos-gateway`, `cursos-matricula`, `cursos-pagos`, `cursos-notificaciones`, `cursos-comprobantes` y `cursos-web`.
3. Te pedirá las variables marcadas `sync: false`. En la primera pasada completa las que ya tienes (Neon, Brevo, `ADMIN_*`); las **URLs** de otros servicios aún no existen: escribe cualquier valor y corrígelas en el paso 5.
4. **Apply**. Cada servicio compila su propia imagen (5–10 min la primera vez).
5. Cuando estén creados, copia la URL de cada uno (arriba en su panel, ej. `https://cursos-pagos.onrender.com`) y ajusta:
   | Dónde | Variable | Valor |
   |---|---|---|
   | Env Group `cursos-comun` | `RABBIT_URL` | URL `amqps://...` de CloudAMQP |
   | Env Group `cursos-comun` | `FRONTEND_URL` | URL de `cursos-web` |
   | cursos-gateway | `CORS_ORIGINS` | URL de `cursos-web` |
   | cursos-gateway | `MATRICULA_URL`, `PAGOS_URL`, `NOTIFICACIONES_URL`, `COMPROBANTES_URL` | URL de cada servicio |
   | cursos-matricula | `PAGOS_URL` | URL de `cursos-pagos` |
   | cursos-pagos | `PUBLIC_URL` | URL de `cursos-gateway` (ahí llegan los webhooks de Mercado Pago, sin ngrok) |
   | cursos-notificaciones | `MATRICULA_URL`, `COMPROBANTES_URL` | URL de cada uno |
   | cursos-web | `API_URL` | URL de `cursos-gateway` |
6. Guarda: Render vuelve a desplegar **solo** el servicio que cambiaste. El static site necesita **Manual Deploy** para tomar el nuevo `API_URL`.
7. Abre `https://<tu-gateway>.onrender.com/api/estado`: los 4 servicios deben decir `"estado": "UP"` (si alguno dice `SIN_RESPUESTA`, está despertando: espera 1 min y recarga). Luego abre la URL de `cursos-web`.

### Lo que debes saber del plan gratuito de Render
- Un Web Service **se duerme tras 15 min sin tráfico** y tarda ~1 min en despertar. Por eso el gateway espera hasta 90 s. Antes de una demo abre `https://<tu-gateway>.onrender.com/api/estado`: esa consulta despierta a los 4 servicios.
- Los eventos de RabbitMQ **no** despiertan a un servicio dormido: los mensajes esperan en su cola de CloudAMQP y se procesan apenas despierta, sin perderse.
- Hay **750 horas gratis al mes por cuenta**, compartidas entre todos los servicios. No uses "pingers" que los mantengan despiertos todo el mes: 5 servicios encendidos 24/7 agotan las horas en ~6 días.
- Los recordatorios de las 9:00 a. m. solo corren si `cursos-matricula` está despierto a esa hora; en la demo usa el botón **Enviar recordatorios de mañana** del panel admin.
- Al hacer `git push`, Render redepliega **solo** el servicio cuyos archivos cambiaron (`buildFilter` en `render.yaml`).

### Alternativa sin Blueprint
**New → Web Service** por cada uno: *Language* `Docker`, *Dockerfile Path* `./pagos-service/Dockerfile`, *Docker Build Context Directory* `.`, *Health Check Path* `/actuator/health/liveness`, y las mismas variables. Para el frontend: **New → Static Site**, *Root Directory* `frontend`, *Build Command* `sed -i "s#https://api.tu-dominio.com#${API_URL}#" src/environments/environment.prod.ts && npm install && npx ng build --configuration production`, *Publish Directory* `dist/academia-web/browser`, variable `API_URL` = URL del gateway, y una regla *Rewrite* `/*` → `/index.html`.

---

## 15. Probar que un servicio caído no tumba a los demás

**En tu PC (Docker):**
```bash
docker compose stop pagos-service
```
- La web sigue funcionando: catálogo, login, mis cursos, comprobantes, panel admin.
- Al inscribirte, la inscripción se guarda igual y la web avisa que el link de pago se generará después.
- http://localhost:8080/api/estado muestra `pagos` como `SIN_RESPUESTA` y el resto `UP`. En el panel admin solo falla la sección de pagos.
- Tras varios fallos, el circuit breaker de *pagos* se abre: el gateway responde al instante `503 "El servicio de pagos no está disponible..."`.
```bash
docker compose start pagos-service      # en ~30 s vuelve a verde y "Mis cursos" permite generar el link
```
Repite con `notificaciones-service`: los mensajes de WhatsApp quedan en la cola `notificaciones.q` (míralo en http://localhost:15672) y salen todos al volver a encenderlo.

**En Render:** en el panel de un servicio → **Suspend**. El resto sigue respondiendo; **Resume** para volver.
