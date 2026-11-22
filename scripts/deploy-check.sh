#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    echo "Creating .env from .env.example for readiness verification..."
    cp .env.example .env
  else
    echo "ERROR: Neither .env nor .env.example exists in $ROOT_DIR"
    exit 1
  fi
fi

./mvnw test -q
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod > /tmp/helpdesk-backend.log 2>&1 &
APP_PID=$!
sleep 20
curl -fsS http://localhost:8081/actuator/health || { kill $APP_PID || true; exit 1; }
kill $APP_PID || true

echo "Deployment readiness checks passed."
