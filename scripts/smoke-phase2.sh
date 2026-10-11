#!/bin/sh
# Actual Phase 0/1 deployment regressions and Phase 2 HTTPS/browser evidence.
set -eu
cd "$(dirname "$0")/.."
mkdir -p .cache/phase2-private .cache/tmp docs/verification/phase2
chmod 700 .cache/phase2-private
# The host-only private parent is not mounted. Its readable child is mounted for the runtime UID.
if [ ! -d .cache/phase2-private/keys ] && [ -d .cache/phase2-secrets ]; then
  mv .cache/phase2-secrets .cache/phase2-private/keys
fi
mkdir -p .cache/phase2-private/keys
export BUILDX_CONFIG="$PWD/.cache/buildx"
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"
export PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright"
export TMPDIR="$PWD/.cache/tmp"
export npm_config_cache="$PWD/.cache/npm"
# This original script writes only .cache; accepted Phase 0/1 evidence stays unchanged.
./scripts/smoke-phase0.sh
cp .cache/docker-smoke.log docs/verification/phase2/docker-smoke.txt
export KELIOS_AUTH_KEY_DIRECTORY="$PWD/.cache/phase2-private/keys"
export KELIOS_TLS_DIRECTORY="$KELIOS_AUTH_KEY_DIRECTORY"
export KELIOS_HTTPS_PORT="${KELIOS_HTTPS_PORT:-8443}"
export KELIOS_AUTH_PUBLIC_ORIGIN="https://localhost:$KELIOS_HTTPS_PORT"
export KELIOS_AUTH_ISSUER="$KELIOS_AUTH_PUBLIC_ORIGIN"
export KELIOS_TEST_LOGIN=phase2-smoke-admin
export KELIOS_TEST_PASSWORD_FILE="$KELIOS_AUTH_KEY_DIRECTORY/password"
export KELIOS_TEST_TLS=true
export KELIOS_BASE_URL="$KELIOS_AUTH_PUBLIC_ORIGIN"
# Ephemeral local fixture material only. Never use these local smoke files in production.
if [ ! -s "$KELIOS_AUTH_KEY_DIRECTORY/main.private.pem" ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$KELIOS_AUTH_KEY_DIRECTORY/main.private.pem" 2>/dev/null
  openssl pkey -in "$KELIOS_AUTH_KEY_DIRECTORY/main.private.pem" -pubout -out "$KELIOS_AUTH_KEY_DIRECTORY/main.public.pem"
  openssl rand -base64 32 > "$KELIOS_AUTH_KEY_DIRECTORY/main.aes"
  openssl req -x509 -newkey rsa:3072 -nodes -keyout "$KELIOS_TLS_DIRECTORY/private.pem" -out "$KELIOS_TLS_DIRECTORY/certificate.pem" -days 2 -subj /CN=localhost -addext 'subjectAltName=DNS:localhost,IP:127.0.0.1' 2>/dev/null
  # Exact password bytes: no incidental newline, no plaintext in command arguments or logs.
  random=$(openssl rand -hex 20)
  printf 'Aa9!%s' "$random" > "$KELIOS_TEST_PASSWORD_FILE"
  # Local disposable fixtures must be readable by the unprivileged container UID.
  chmod 755 "$KELIOS_AUTH_KEY_DIRECTORY"
  chmod 444 "$KELIOS_AUTH_KEY_DIRECTORY"/*
fi
compose() { docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml -f deploy/compose.auth.yaml "$@"; }
compose config --quiet
KELIOS_BRAND=blue compose up -d --no-build --wait --wait-timeout 180
# Check before command invocation so a rerun does not turn an expected recovery refusal into a false failure.
exists=$(compose exec -T postgres psql -U kelios -d kelios -Atc "SELECT count(*) FROM local_global_credential WHERE login_name='phase2-smoke-admin'")
if [ "$exists" = 0 ]; then
  compose run --rm --no-deps backend --spring.main.web-application-type=none --kelios.recovery.enabled=true --kelios.recovery.login=phase2-smoke-admin '--kelios.recovery.display-name=Phase 2 smoke administrator' --kelios.recovery.password-file=/run/secrets/kelios-auth/password > .cache/phase2-bootstrap.log 2>&1
fi
if compose run --rm --no-deps backend --spring.main.web-application-type=none --kelios.recovery.enabled=true --kelios.recovery.login=refused-extra-admin '--kelios.recovery.display-name=Refused administrator' --kelios.recovery.password-file=/run/secrets/kelios-auth/password > .cache/phase2-bootstrap-refusal.log 2>&1; then
  printf 'Bootstrap/recovery incorrectly created another administrator.\n' >&2
  exit 1
fi
printf 'Deployment bootstrap/recovery repeat refused as required.\n'
initial_image=''
for brand in blue green; do
  KELIOS_BRAND="$brand" compose up -d --no-build --no-deps --force-recreate --wait frontend
  container_id=$(compose ps -q frontend)
  image_id=$(docker inspect --format '{{.Image}}' "$container_id")
  if [ -n "$initial_image" ]; then test "$image_id" = "$initial_image"; fi
  initial_image="$image_id"
  printf '%s frontend image: %s\n' "$brand" "$image_id"
  # Run real authentication once; both-brand regressions use the same authenticated-capable image.
  if [ "$brand" = blue ]; then
    (cd frontend && KELIOS_EXPECTED_BRAND="$brand" npm run test:smoke)
  else
    (cd frontend && KELIOS_EXPECTED_BRAND="$brand" npm run test:smoke -- runtime-branding.spec.ts)
  fi
  cp "frontend/test-results/$brand.png" "docs/verification/phase2/$brand.png"
done
compose exec -T postgres psql -U kelios -d kelios -c 'SELECT version, description, success FROM flyway_schema_history;' > docs/verification/phase2/migrations.txt
compose logs --no-color backend > docs/verification/phase2/backend-startup.txt
printf 'Phase 2 HTTPS/authentication and both-brand deployment regression passed.\n'
