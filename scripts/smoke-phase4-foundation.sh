#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .cache
exec > .cache/phase4-foundation.log 2>&1
test -s deploy/secrets/db-password
docker compose -f deploy/compose.yaml config --quiet
docker compose -f deploy/compose.yaml build
KELIOS_BRAND=blue docker compose -f deploy/compose.yaml up -d --wait --wait-timeout 180
docker compose -f deploy/compose.yaml ps

docker compose -f deploy/compose.yaml exec -T backend sh -ec '
  test "$(id -u)" != 0
  if command -v mvn || command -v javac; then exit 1; fi
  test ! -d /root/.m2
  wget -q -O - http://127.0.0.1:8081/actuator/health/readiness
  wget -q -O - http://127.0.0.1:8081/actuator/health/liveness
'
docker compose -f deploy/compose.yaml exec -T frontend sh -ec '
  test "$(id -u)" != 0
  if command -v node || command -v npm; then exit 1; fi
  test ! -d /workspace
  wget -q -O - http://127.0.0.1:8080/healthz
'
docker compose -f deploy/compose.yaml exec -T postgres psql -U kelios -d kelios -c 'SELECT version, description, success FROM flyway_schema_history;'
docker compose -f deploy/compose.yaml exec -T postgres psql -U kelios -d kelios -c "SELECT tablename FROM pg_tables WHERE schemaname = 'public';"

curl -fsS "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/api/v1/status"
test "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/api/v1/not-implemented")" = 404
test "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/runtime/missing.json")" = 404
test "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/assets/missing.js")" = 404
curl -fsS -D - "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/runtime/config.json"
blue_logo=$(curl -fsS "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/runtime/logos/logo.svg")
printf '%s\n' "$blue_logo"
case "$blue_logo" in *'KELIOS blue sample'*) ;; *) exit 1 ;; esac

frontend_id=$(docker compose -f deploy/compose.yaml ps -q frontend)
blue_image=$(docker inspect --format '{{.Image}}' "$frontend_id")
printf '\nBlue frontend image: %s\n' "$blue_image"

KELIOS_BRAND=green docker compose -f deploy/compose.yaml up -d --no-build --no-deps --force-recreate --wait frontend
frontend_id=$(docker compose -f deploy/compose.yaml ps -q frontend)
green_image=$(docker inspect --format '{{.Image}}' "$frontend_id")
test "$blue_image" = "$green_image"
printf 'Green frontend image: %s\n' "$green_image"
curl -fsS "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/runtime/config.json"
green_logo=$(curl -fsS "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/runtime/logos/logo.svg")
printf '%s\n' "$green_logo"
case "$green_logo" in *'KELIOS green sample'*) ;; *) exit 1 ;; esac
test "$blue_logo" != "$green_logo"
test "$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:${KELIOS_HTTP_PORT:-8080}/api/v1/assets")" = 401
printf '\nContainer smoke tests passed. Run browser smoke tests with both brands.\n'
