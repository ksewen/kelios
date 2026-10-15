#!/bin/sh
# Fresh isolated database/image/HTTPS readiness regression; leaves its disposable volume intact.
set -eu
cd "$(dirname "$0")/.."
export COMPOSE_PROJECT_NAME="kelios-phase4-clean-$(date +%s)"
export KELIOS_HTTP_PORT=9180
export KELIOS_HTTPS_PORT=9543
export KELIOS_AUTH_PUBLIC_ORIGIN=https://localhost:9543
export KELIOS_AUTH_ISSUER="$KELIOS_AUTH_PUBLIC_ORIGIN"
compose() { docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml "$@"; }
mkdir -p .cache/verification/phase4
compose up -d --no-build --wait --wait-timeout 180
compose exec -T postgres psql -U kelios -d kelios -c "SELECT version,success FROM flyway_schema_history; SELECT count(*) AS assets FROM asset; SELECT count(*) AS users FROM app_user; SELECT count(*) AS history FROM asset_event;" > .cache/verification/phase4/clean-fresh-schema.txt
compose exec -T postgres psql -U kelios -d kelios -Atc "SELECT (SELECT count(*) FROM flyway_schema_history WHERE success)=6 AND (SELECT count(*) FROM asset)=0 AND (SELECT count(*) FROM app_user)=0 AND (SELECT count(*) FROM asset_event)=0" | grep '^t$'
compose exec -T backend sh -ec 'test "$(id -u)" != 0; ! command -v mvn; ! command -v javac; wget -q -O - http://127.0.0.1:8081/actuator/health/readiness'
curl -kfsS "$KELIOS_AUTH_PUBLIC_ORIGIN/api/v1/status"
compose logs --no-color backend > .cache/verification/phase4/clean-backend-startup.txt
compose ps > .cache/verification/phase4/clean-compose-health.txt
compose stop
printf 'Clean isolated V1-to-V6 image/HTTPS deployment passed; disposable volume retained.\n'
