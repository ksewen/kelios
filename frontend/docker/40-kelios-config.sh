#!/bin/sh
set -eu
mkdir -p /tmp/kelios
# nginx always serves the stable URL; deployments can mount the source anywhere.
# A symlink observes updates to mounted files without an image rebuild.
if [ -r "$KELIOS_CONFIG_FILE" ] && [ -f "$KELIOS_CONFIG_FILE" ]; then
  ln -sf "$KELIOS_CONFIG_FILE" /tmp/kelios/config.json
else
  printf '%s\n' '{"schemaVersion":1}' > /tmp/kelios/config.json
  printf '%s\n' 'KELIOS branding source unavailable; using defaults.' >&2
fi
