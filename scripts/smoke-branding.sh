#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p .cache/tmp
export PLAYWRIGHT_BROWSERS_PATH="$PWD/.cache/playwright"
export TMPDIR="$PWD/.cache/tmp"
export npm_config_cache="$PWD/.cache/npm"
initial_image=''
for brand in blue green; do
  KELIOS_BRAND="$brand" docker compose -f deploy/compose.yaml up -d --no-build --no-deps --force-recreate --wait frontend
  frontend_id=$(docker compose -f deploy/compose.yaml ps -q frontend)
  current_image=$(docker inspect --format '{{.Image}}' "$frontend_id")
  if [ -n "$initial_image" ]; then test "$initial_image" = "$current_image"; fi
  initial_image="$current_image"
  printf '%s frontend image: %s\n' "$brand" "$current_image"
  (cd frontend && KELIOS_EXPECTED_BRAND="$brand" npm run test:smoke)
  cp "frontend/test-results/$brand.png" "docs/verification/$brand.png"
done
printf 'Both rendered branding smoke suites passed with the same frontend image.\n'
