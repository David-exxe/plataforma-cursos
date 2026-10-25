#!/usr/bin/env bash
# Envia al gateway un webhook de Mercado Pago FIRMADO igual que lo hace Mercado Pago,
# para probar la validacion de x-signature.  Uso: ./scripts/simular-webhook-mp.sh <payment_id> [secreto]
set -euo pipefail
PAYMENT_ID=${1:?payment id}
SECRET=${2:-${MP_WEBHOOK_SECRET:-secreto-de-prueba}}
REQ_ID=$(cat /proc/sys/kernel/random/uuid 2>/dev/null || uuidgen)
TS=$(date +%s)
MANIFEST="id:${PAYMENT_ID};request-id:${REQ_ID};ts:${TS};"
V1=$(printf '%s' "$MANIFEST" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.*= //')
curl -s -w "\nHTTP %{http_code}\n" -X POST "http://localhost:8080/webhooks/mercadopago?data.id=${PAYMENT_ID}&type=payment" \
  -H "Content-Type: application/json" -H "x-request-id: ${REQ_ID}" -H "x-signature: ts=${TS},v1=${V1}" \
  -d "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"${PAYMENT_ID}\"}}"
