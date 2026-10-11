#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
sh -n kelios
for f in scripts/*.sh; do sh -n "$f"; done
python3 scripts/test-deployment-cli.py
