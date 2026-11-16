#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 ]]; then echo 'Usage: scripts/restore.sh backup.dump NEW_EMPTY_DATABASE' >&2; exit 1; fi
: "${PGHOST:=127.0.0.1}" "${PGPORT:=5433}" "${PGUSER:=zomi}"
export PGHOST PGPORT PGUSER
createdb "$2"
pg_restore --exit-on-error --no-owner --dbname="$2" "$1"
