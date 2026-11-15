#!/usr/bin/env bash
# Crea .env a partir de .env.example con secretos aleatorios fuertes.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] && { echo ".env ya existe, no se sobrescribe"; exit 0; }
cp .env.example .env
JWT=$(openssl rand -base64 64 | tr -d '\n')
INTERNA=$(openssl rand -hex 32)
CIFRADO=$(openssl rand -base64 32 | tr -d '\n')
sed -i.bak \
  -e "s|^JWT_SECRET=.*|JWT_SECRET=${JWT}|" \
  -e "s|^INTERNAL_API_KEY=.*|INTERNAL_API_KEY=${INTERNA}|" \
  -e "s|^ENCRYPTION_KEY=.*|ENCRYPTION_KEY=${CIFRADO}|" .env
rm -f .env.bak
echo ".env creado con secretos nuevos. Revisa ADMIN_PASSWORD y las credenciales de Mercado Pago / WhatsApp."
