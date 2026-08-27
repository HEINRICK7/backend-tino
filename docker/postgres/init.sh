#!/usr/bin/env bash
set -euo pipefail

: "${TINO_POSTGRES_APP_PASSWORD:?missing runtime app-role credential}"
: "${TINO_POSTGRES_MIGRATOR_PASSWORD:?missing runtime migrator-role credential}"

psql \
    --set=ON_ERROR_STOP=1 \
    --username "$POSTGRES_USER" \
    --dbname "$POSTGRES_DB" \
    --set=app_credential="$TINO_POSTGRES_APP_PASSWORD" \
    --set=migrator_credential="$TINO_POSTGRES_MIGRATOR_PASSWORD" <<'SQL'
CREATE ROLE tino_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
CREATE ROLE tino_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS;
SELECT format('ALTER ROLE tino_migrator PASSWORD %L', :'migrator_credential') \gexec
SELECT format('ALTER ROLE tino_app PASSWORD %L', :'app_credential') \gexec
GRANT CONNECT ON DATABASE tino TO tino_migrator, tino_app;
GRANT CREATE, USAGE ON SCHEMA public TO tino_migrator;
GRANT USAGE ON SCHEMA public TO tino_app;
SQL
