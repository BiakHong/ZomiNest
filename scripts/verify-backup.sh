#!/usr/bin/env bash
set -euo pipefail
: "${PGHOST:=127.0.0.1}" "${PGPORT:=5433}" "${PGUSER:=zomi}" "${PGDATABASE:=zomi}"
export PGHOST PGPORT PGUSER PGDATABASE
archive=$(mktemp /tmp/zomi-backup.XXXXXX)
testdb="zomi_restore_${RANDOM}_$$"
trap 'rm -f "$archive"; dropdb --if-exists "$testdb"' EXIT
scripts/backup.sh "$archive"
scripts/restore.sh "$archive" "$testdb"
for table in users quiz edition attempt submission profile; do
 source_count=$(psql -Atc "select count(*) from $table")
 restored_count=$(PGDATABASE="$testdb" psql -Atc "select count(*) from $table")
 [[ "$source_count" == "$restored_count" ]] || { echo "$table count mismatch"; exit 1; }
done
echo 'Backup restored into a temporary PostgreSQL database; core table counts match.'
