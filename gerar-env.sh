#!/usr/bin/env bash
# Gera o arquivo .env com chaves aleatórias seguras (macOS/Linux/Git Bash). Uso: ./gerar-env.sh
set -euo pipefail
cd "$(dirname "$0")"
if [ -f .env ]; then echo "O arquivo .env já existe. Nada foi alterado (apague-o para gerar de novo)."; exit 0; fi
db=$(openssl rand -base64 24 | tr -d '+/=')
jwt=$(openssl rand -base64 48 | tr -d '\n')
key=$(openssl rand -base64 32 | tr -d '\n')
sed -e "s|^ERP_DB_PASSWORD=.*|ERP_DB_PASSWORD=${db}|" \
    -e "s|^ERP_JWT_SECRET=.*|ERP_JWT_SECRET=${jwt}|" \
    -e "s|^ERP_ENCRYPTION_KEY=.*|ERP_ENCRYPTION_KEY=${key}|" .env.example > .env
chmod 600 .env
echo "Arquivo .env criado com chaves seguras. Guarde uma cópia dele em local seguro (ex.: gerenciador de senhas)."
echo "Próximo passo:  docker compose up --build"
