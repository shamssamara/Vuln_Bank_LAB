#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --build
docker compose ps
printf '\nAPI health: https://localhost:8443/health\n'
printf 'Check with: python3 scripts/smoke_test.py\n'
