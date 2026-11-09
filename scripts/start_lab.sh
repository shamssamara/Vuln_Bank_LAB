#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose up -d --build
docker compose ps
printf '\nVulnerable: http://127.0.0.1:8080/health\nSecure: http://127.0.0.1:8082/health\n'
python3 scripts/verify_dual_mode.py
