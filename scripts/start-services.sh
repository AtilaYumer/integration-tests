#!/usr/bin/env bash
# Builds and starts catalog-service and order-service from local checkouts, then waits until both are healthy.
# Usage: scripts/start-services.sh [DIR]   (DIR holds catalog-service/ and order-service/, default .work/repos)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SERVICES_DIR="$(cd "${1:-$ROOT/.work/repos}" && pwd)"
RUN_DIR="$ROOT/.work/run"
CATALOG_PORT="${CATALOG_PORT:-8081}"
ORDER_PORT="${ORDER_PORT:-8082}"
mkdir -p "$RUN_DIR"

start() {
  local name="$1" port="$2"; shift 2
  if curl -s -o /dev/null "http://localhost:$port"; then
    echo "Port $port is already in use; stop whatever runs there (scripts/stop-services.sh) and retry" >&2
    exit 1
  fi
  echo "Building $name"
  mvn -q -B -f "$SERVICES_DIR/$name/pom.xml" -DskipTests package
  local jar
  jar="$(ls "$SERVICES_DIR/$name"/target/*.jar | grep -v plain | head -1)"
  echo "Starting $name on port $port"
  env PORT="$port" "$@" nohup java -jar "$jar" > "$RUN_DIR/$name.log" 2>&1 &
  echo $! > "$RUN_DIR/$name.pid"
}

wait_healthy() {
  local name="$1" port="$2"
  for _ in $(seq 1 60); do
    if curl -fs "http://localhost:$port/actuator/health" | grep -q '"UP"'; then
      echo "$name is up"
      return 0
    fi
    sleep 2
  done
  echo "$name did not become healthy, log follows" >&2
  cat "$RUN_DIR/$name.log" >&2
  return 1
}

start catalog-service "$CATALOG_PORT"
start order-service "$ORDER_PORT" CATALOG_BASE_URL="http://localhost:$CATALOG_PORT"
wait_healthy catalog-service "$CATALOG_PORT"
wait_healthy order-service "$ORDER_PORT"
