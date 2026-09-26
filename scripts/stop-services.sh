#!/usr/bin/env bash
# Stops the services started by start-services.sh.
set -uo pipefail

RUN_DIR="$(cd "$(dirname "$0")/.." && pwd)/.work/run"
for pidfile in "$RUN_DIR"/*.pid; do
  [ -e "$pidfile" ] || continue
  kill "$(cat "$pidfile")" 2>/dev/null && echo "Stopped $(basename "$pidfile" .pid)"
  rm -f "$pidfile"
done
