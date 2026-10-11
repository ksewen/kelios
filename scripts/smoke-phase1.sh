#!/bin/sh
# Deployment/branding regression without overwriting any Phase 0 evidence.
set -eu
cd "$(dirname "$0")/.."
mkdir -p .cache docs/verification/phase1
export BUILDX_CONFIG="$PWD/.cache/buildx"
./scripts/smoke-phase0.sh
cp .cache/docker-smoke.log docs/verification/phase1/docker-smoke.txt
export PATH="/opt/homebrew/opt/node@24/bin:$PATH"
export PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright"
export TMPDIR="$PWD/.cache/tmp"
export npm_config_cache="$PWD/.cache/npm"
initial_image=''
for brand in blue green; do
  KELIOS_BRAND="$brand" docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml up -d --no-build --no-deps --force-recreate --wait frontend
  container_id=$(docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml ps -q frontend)
  image_id=$(docker inspect --format '{{.Image}}' "$container_id")
  if [ -n "$initial_image" ]; then test "$image_id" = "$initial_image"; fi
  initial_image="$image_id"
  printf '%s frontend image: %s\n' "$brand" "$image_id"
  (cd frontend && KELIOS_EXPECTED_BRAND="$brand" npm run test:smoke)
  cp "frontend/test-results/$brand.png" "docs/verification/phase1/$brand.png"
done
docker compose -f deploy/compose.yaml -f deploy/compose.postgres.yaml logs --no-color backend > docs/verification/phase1/backend-startup.txt
printf 'Phase 1 deployment and both-brand browser regression passed.\n'
