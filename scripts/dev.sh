#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f .env ]]; then set -a; source .env; set +a; fi
if [[ $# -gt 0 ]]; then
  if [[ $# -ne 2 || "$1" != "--refresh-demo" ]]; then
    echo "Usage: scripts/dev.sh [--refresh-demo proverbs-demo|community-character|discover-personality]" >&2
    exit 1
  fi
  case "$2" in
    proverbs-demo|community-character|discover-personality) export DEMO_REFRESH="$2" ;;
    *) echo "Unknown demo source: $2" >&2; exit 1 ;;
  esac
fi
export DEMO_CONTENT="${DEMO_CONTENT:-true}"
export BASE_URL="${BASE_URL:-http://localhost:5173}"
if [[ "$BASE_URL" == http://localhost:8088 ]]; then export BASE_URL=http://localhost:5173; fi
if command -v lsof >/dev/null 2>&1; then
  for dev_port in 8080 5173; do
    if lsof -nP -iTCP:"$dev_port" -sTCP:LISTEN -t >/dev/null 2>&1; then
      echo "Port $dev_port is already in use. Stop the existing server, then run scripts/dev.sh again." >&2
      echo "To see the process: lsof -nP -iTCP:$dev_port -sTCP:LISTEN" >&2
      exit 1
    fi
  done
fi
mvn -f backend/pom.xml spring-boot:run &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true' EXIT INT TERM
npm --prefix frontend run dev -- --strictPort
