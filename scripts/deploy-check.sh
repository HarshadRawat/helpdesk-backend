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

# Export non-comment variables from .env
set -a
# shellcheck disable=SC1091
source .env
set +a

# Provide testable fallbacks if local MySQL is not running
export DB_URL="${DB_URL:-jdbc:h2:mem:helpdesk_check;MODE=MySQL;DB_CLOSE_DELAY=-1}"
export DB_USERNAME="${DB_USERNAME:-sa}"
export DB_PASSWORD="${DB_PASSWORD:-}"
export JWT_SECRET="${JWT_SECRET:-deploy-check-local-secret-at-least-32-chars-long!}"
export SPRING_FLYWAY_ENABLED="${SPRING_FLYWAY_ENABLED:-false}"
export SPRING_JPA_HIBERNATE_DDL_AUTO="${SPRING_JPA_HIBERNATE_DDL_AUTO:-create-drop}"

./mvnw test -q
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod > /tmp/helpdesk-backend.log 2>&1 &
APP_PID=$!

HEALTHY=false
for i in $(seq 1 40); do
  if curl -fsS http://localhost:8081/actuator/health > /dev/null 2>&1; then
    echo "Actuator health check passed on port 8081."
    HEALTHY=true
    break
  fi
  sleep 2
done

kill $APP_PID || true
wait $APP_PID 2>/dev/null || true

if [ "$HEALTHY" = false ]; then
  echo "Health check failed on port 8081"
  cat /tmp/helpdesk-backend.log || true
  exit 1
fi

echo "Deployment readiness checks passed."
