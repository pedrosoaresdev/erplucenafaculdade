#!/usr/bin/env bash
# Recria o banco de desenvolvimento, sobe backend (perfil dev) e executa o E2E contra o `ng serve` em :4200.
set -euo pipefail
cd "$(dirname "$0")/../.."
pkill -f 'spring-boot:run' 2>/dev/null || true
pkill -f 'erp.ErpApplication' 2>/dev/null || true
sleep 2
PGPASSWORD=${ERP_DB_PASSWORD:-erp} psql -h localhost -U ${ERP_DB_USER:-erp} -d erp -qc "drop schema public cascade; create schema public;"
(cd backend && nohup mvn -q -B spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/erp-backend.log 2>&1 &)
for _ in $(seq 1 90); do curl -sf localhost:8080/actuator/health >/dev/null && break; sleep 2; done
cd frontend && node e2e/login-flow.e2e.mjs "${1:-e2e-screens}"
