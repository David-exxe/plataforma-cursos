#!/usr/bin/env bash
# Prueba de concepto de punta a punta (con MP_MODO=mock y WA_MODO=mock).
# Requiere: curl y jq.   Uso: ./scripts/probar-flujo.sh
set -euo pipefail
GW=${GW:-http://localhost:8080}
ENV_FILE="$(dirname "$0")/../.env"
leer() { grep "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2- | tr -d '\r'; }
ADMIN_EMAIL=$(leer ADMIN_EMAIL)
ADMIN_PASSWORD=$(leer ADMIN_PASSWORD)
EMAIL="alumno$RANDOM@correo.com"
paso() { echo -e "\n\033[1;34m== $* ==\033[0m"; }

paso "1. Registro del estudiante ($EMAIL)"
curl -sf -X POST $GW/api/auth/registro -H 'Content-Type: application/json' \
  -d "{\"nombre\":\"Ana Torres\",\"email\":\"$EMAIL\",\"telefono\":\"987654321\",\"documento\":\"74581236\",\"password\":\"Clave2026\"}" | jq

paso "2. Login -> JWT"
TOKEN=$(curl -sf -X POST $GW/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Clave2026\"}" | jq -r .accessToken)
echo "${TOKEN:0:40}..."

paso "3. Catalogo publico"
curl -sf $GW/api/cursos | jq '.[] | {id, nombre, precio, fechaInicio, cuposDisponibles}'
CURSO=$(curl -sf $GW/api/cursos | jq '.[0].id')

paso "4. Inscripcion al curso $CURSO (matricula -> pagos crea la preferencia de Mercado Pago)"
INS=$(curl -sf -X POST $GW/api/inscripciones -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d "{\"cursoId\":$CURSO}")
echo "$INS" | jq
ID=$(echo "$INS" | jq .id)

paso "5. Simulacion: Mercado Pago aprueba el pago"
curl -sf -X POST "$GW/api/pagos/simulacion/$ID?resultado=approved" -H "Authorization: Bearer $TOKEN" | jq

paso "6. Esperando que los eventos viajen por RabbitMQ..."
sleep 8
curl -sf $GW/api/inscripciones/$ID -H "Authorization: Bearer $TOKEN" | jq '{id, curso, estado, ubicacion, fechaPago}'

paso "7. Comprobante emitido"
curl -sf $GW/api/comprobantes/mios -H "Authorization: Bearer $TOKEN" | jq
CID=$(curl -sf $GW/api/comprobantes/mios -H "Authorization: Bearer $TOKEN" | jq '.[0].id')
curl -sf $GW/api/comprobantes/$CID/pdf -H "Authorization: Bearer $TOKEN" -o comprobante-demo.pdf && echo "PDF guardado en comprobante-demo.pdf"

paso "8. Admin: notificaciones de WhatsApp generadas"
ADMIN=$(curl -sf -X POST $GW/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.accessToken // empty')
if [ -n "$ADMIN" ]; then
  curl -sf "$GW/api/admin/notificaciones" -H "Authorization: Bearer $ADMIN" | jq '.[0:4] | .[] | {tipo, telefono, estado}'
else
  echo "(El admin tiene 2FA activo: entra con /api/auth/2fa/verificar)"
fi

paso "9. Seguridad: token invalido -> 401 en el gateway"
curl -s -o /dev/null -w "HTTP %{http_code}\n" $GW/api/inscripciones/mias -H "Authorization: Bearer token.falso.x"

paso "10. Seguridad: 5 logins fallidos -> la IP queda bloqueada (403)"
for i in 1 2 3 4 5 6; do
  curl -s -o /dev/null -w "intento $i -> HTTP %{http_code}\n" -X POST $GW/api/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"x@x.com","password":"mala1234"}'
done
echo "Para desbloquear: reinicia el gateway o DELETE $GW/gateway/admin/ips/<ip> con token ADMIN"
echo -e "\nCorreos enviados: http://localhost:8025   |   Mensajes WhatsApp (mock): docker compose logs notificaciones-service"
