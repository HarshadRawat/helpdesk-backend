#!/usr/bin/env bash
set -euo pipefail

if [ ! -f .env ]; then
  echo ".env file is missing. Copy .env.example to .env and fill in secrets."
  exit 1
fi

./mvnw test -q
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod > /tmp/helpdesk-backend.log 2>&1 &
APP_PID=$!
sleep 20
curl -fsS http://localhost:8080/actuator/health || { kill $APP_PID || true; exit 1; }
kill $APP_PID || true

echo "Deployment readiness checks passed."
