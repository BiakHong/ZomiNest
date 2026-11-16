#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:=127.0.0.1}" "${PGPORT:=5433}" "${PGUSER:=zomi}" "${PGDATABASE:=zomi}"
export PGHOST PGPORT PGUSER PGDATABASE
if [[ $# != 1 ]]; then echo 'Usage: scripts/backup.sh output.dump' >&2; exit 1; fi
umask 077
pg_dump --format=custom --file="$1"
