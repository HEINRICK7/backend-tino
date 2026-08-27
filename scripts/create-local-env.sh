#!/usr/bin/env bash
set -euo pipefail

if [[ -e .env ]]; then
    echo ".env already exists; refusing to overwrite it." >&2
    exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl is required to generate local runtime credentials." >&2
    exit 1
fi

umask 077
temporary_file=$(mktemp ./.env.tmp.XXXXXX)
trap 'rm -f "$temporary_file"' EXIT

postgres_admin_credential=$(openssl rand -hex 32)
postgres_app_credential=$(openssl rand -hex 32)
postgres_migrator_credential=$(openssl rand -hex 32)
keycloak_admin_credential=$(openssl rand -hex 32)

printf '%s=%s\n' \
    TINO_POSTGRES_ADMIN_PASSWORD "$postgres_admin_credential" \
    TINO_POSTGRES_APP_PASSWORD "$postgres_app_credential" \
    TINO_POSTGRES_MIGRATOR_PASSWORD "$postgres_migrator_credential" \
    TINO_KEYCLOAK_ADMIN_PASSWORD "$keycloak_admin_credential" \
    SPRING_DATASOURCE_PASSWORD "$postgres_app_credential" \
    SPRING_FLYWAY_PASSWORD "$postgres_migrator_credential" \
    JOOQ_JDBC_PASSWORD "$postgres_migrator_credential" \
    > "$temporary_file"

chmod 600 "$temporary_file"
mv "$temporary_file" .env
trap - EXIT

echo "Generated ignored .env with runtime-only local credentials."
