#!/usr/bin/env bash
# Sobe o backend SEM administrador Master (instalação pendente), captura o código do console e roda o E2E da instalação.
set -euo pipefail
cd "$(dirname "$0")/../.."
pkill -f 'spring-boot:run' 2>/dev/null || true
pkill -f 'erp.ErpApplication' 2>/dev/null || true
sleep 2
PGPASSWORD=${ERP_DB_PASSWORD:-erp} psql -h localhost -U ${ERP_DB_USER:-erp} -d erp -qc "drop schema public cascade; create schema public;"
OWNER=${OWNER_EMAIL:-pedrolucenasoaresdev@gmail.com}
(cd backend && ERP_BOOTSTRAP_MASTER_USERNAME= ERP_OWNER_EMAIL="$OWNER" nohup mvn -q -B spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/erp-backend.log 2>&1 &)
for _ in $(seq 1 90); do curl -sf localhost:8080/actuator/health >/dev/null && break; sleep 2; done
CODE=$(grep -oE 'código de instalação:  [A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}' /tmp/erp-backend.log | tail -1 | awk '{print $NF}')
[ -n "$CODE" ] || { echo "código de instalação não encontrado no log"; exit 1; }
cd frontend && OWNER_EMAIL="$OWNER" SETUP_CODE="$CODE" node e2e/setup-flow.e2e.mjs "${1:-e2e-screens}"
