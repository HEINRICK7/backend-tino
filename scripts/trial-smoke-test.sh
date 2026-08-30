#!/usr/bin/env bash
set -euo pipefail

if [[ -f .env ]]; then
    set -a
    . ./.env
    set +a
fi

command -v curl >/dev/null 2>&1 || { echo "curl is required." >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "jq is required." >&2; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker is required." >&2; exit 1; }
command -v openssl >/dev/null 2>&1 || { echo "openssl is required." >&2; exit 1; }

app_port="${TINO_APP_PORT:-8080}"
keycloak_port="${TINO_KEYCLOAK_PORT:-8081}"
keycloak_admin_user="${KEYCLOAK_ADMIN:-admin}"
smoke_user="${TINO_SMOKE_USER:-trial-smoke}"
smoke_credential="${TINO_SMOKE_USER_PASSWORD-}"
if [[ -z "$smoke_credential" ]]; then
    smoke_credential=$(openssl rand -hex 24)
fi
access_key="53160911510448000171550010000106771000187760"
app_url="http://localhost:${app_port}"
keycloak_url="http://localhost:${keycloak_port}"

: "${TINO_KEYCLOAK_ADMIN_PASSWORD:?TINO_KEYCLOAK_ADMIN_PASSWORD is required (load .env first).}"

kc_admin_token=$(curl -fsS -X POST "${keycloak_url}/realms/master/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "client_id=admin-cli" \
    --data-urlencode "grant_type=password" \
    --data-urlencode "username=${keycloak_admin_user}" \
    --data-urlencode "password=${TINO_KEYCLOAK_ADMIN_PASSWORD}" | jq -er '.access_token')

kc_json_request() {
    local method="$1" url="$2" expected="$3" payload="${4:-}"
    local response status body
    if [[ -n "$payload" ]]; then
        response=$(curl -sS -X "$method" "$url" -H "Authorization: Bearer ${kc_admin_token}" \
            -H 'Content-Type: application/json' -d "$payload" -w $'\n%{http_code}')
    else
        response=$(curl -sS -X "$method" "$url" -H "Authorization: Bearer ${kc_admin_token}" -w $'\n%{http_code}')
    fi
    status="${response##*$'\n'}"
    body="${response%$'\n'*}"
    if [[ "$status" != "$expected" ]]; then
        echo "Keycloak request failed: ${method} ${url} -> ${status}" >&2
        [[ -n "$body" ]] && echo "$body" >&2
        exit 1
    fi
    printf '%s' "$body"
}

user_query=$(curl -fsS -G "${keycloak_url}/admin/realms/tino/users" \
    -H "Authorization: Bearer ${kc_admin_token}" --data-urlencode "username=${smoke_user}")
user_id=$(jq -r --arg username "$smoke_user" '.[] | select(.username == $username) | .id' <<<"$user_query" | head -n 1)
user_payload=$(jq -n --arg username "$smoke_user" '{username:$username, enabled:true, email:($username + "@local.test"), emailVerified:true, firstName:"TINO", lastName:"Trial"}')
user_update_payload=$(jq -n --arg username "$smoke_user" '{enabled:true, email:($username + "@local.test"), emailVerified:true, firstName:"TINO", lastName:"Trial"}')

if [[ -z "$user_id" ]]; then
    kc_json_request POST "${keycloak_url}/admin/realms/tino/users" 201 "$user_payload" >/dev/null
    user_query=$(curl -fsS -G "${keycloak_url}/admin/realms/tino/users" \
        -H "Authorization: Bearer ${kc_admin_token}" --data-urlencode "username=${smoke_user}")
    user_id=$(jq -er --arg username "$smoke_user" '.[] | select(.username == $username) | .id' <<<"$user_query" | head -n 1)
else
    kc_json_request PUT "${keycloak_url}/admin/realms/tino/users/${user_id}" 204 "$user_update_payload" >/dev/null
fi

credential_payload=$(jq -n --arg credential "$smoke_credential" '{type:"password", value:$credential, temporary:false}')
kc_json_request PUT "${keycloak_url}/admin/realms/tino/users/${user_id}/reset-password" 204 "$credential_payload" >/dev/null

access_token=$(curl -fsS -X POST "${keycloak_url}/realms/tino/protocol/openid-connect/token" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode 'client_id=tino-android' \
    --data-urlencode 'grant_type=password' \
    --data-urlencode "username=${smoke_user}" \
    --data-urlencode "password=${smoke_credential}" | jq -er '.access_token')

api_json_request() {
    local method="$1" url="$2" expected="$3" payload="${4:-}" idem="${5:-}"
    local response status body
    local -a args=(-sS -X "$method" "$url" -H "Authorization: Bearer ${access_token}" -H 'Accept: application/json')
    [[ -n "$idem" ]] && args+=(-H "Idempotency-Key: ${idem}")
    if [[ -n "$payload" ]]; then
        args+=(-H 'Content-Type: application/json' -d "$payload")
    fi
    response=$(curl "${args[@]}" -w $'\n%{http_code}')
    status="${response##*$'\n'}"
    body="${response%$'\n'*}"
    if [[ "$status" != "$expected" ]]; then
        echo "API request failed: ${method} ${url} -> ${status}" >&2
        [[ -n "$body" ]] && echo "$body" >&2
        exit 1
    fi
    printf '%s' "$body"
}

db_query() {
    docker compose exec -T postgres psql -U tino_admin -d tino -X -At -c "$1"
}

business_name="${TINO_SMOKE_BUSINESS:-TINO Trial Smoke $(date +%s)}"
business_payload=$(jq -n --arg trade_name "$business_name" '{trade_name:$trade_name, vertical:"RETAIL"}')
business=$(api_json_request POST "${app_url}/api/v1/businesses" 201 "$business_payload")
business_id=$(jq -er '.id' <<<"$business")

nfe_payload=$(jq -n --arg access_key "$access_key" '{access_key:$access_key}')
nfe=$(api_json_request POST "${app_url}/api/v1/businesses/${business_id}/nfe-documents" 200 "$nfe_payload" "trial-nfe-${business_id}")
document_id=$(jq -er '.id' <<<"$nfe")
preview_id=$(jq -er '.preview.id' <<<"$nfe")
preview_version=$(jq -er '.preview.version' <<<"$nfe")
[[ "$(jq -r '.fiscal_status' <<<"$nfe")" == "AUTHORIZED" ]] || { echo "Trial fixture is not AUTHORIZED." >&2; exit 1; }

preview=$(api_json_request GET "${app_url}/api/v1/businesses/${business_id}/nfe-documents/${document_id}/preview" 200)
[[ "$(jq -r '.id' <<<"$preview")" == "$preview_id" ]] || { echo "Preview identity mismatch." >&2; exit 1; }

before_movements=$(db_query "SELECT count(*) FROM inventory_movements WHERE business_id = '${business_id}';")
before_balances=$(db_query "SELECT count(*) FROM inventory_balances WHERE business_id = '${business_id}';")
[[ "$before_movements" == "0" && "$before_balances" == "0" ]] || { echo "Preview mutated inventory." >&2; exit 1; }

confirm_payload=$(jq -n --argjson version "$preview_version" '{preview_version:$version, items:[{line_number:1, action:"CREATE_PRODUCT", base_unit:"RS", conversion_factor:1}]}')
receipt=$(api_json_request POST "${app_url}/api/v1/businesses/${business_id}/goods-receipts/${preview_id}/confirm" 200 "$confirm_payload" "trial-confirm-${business_id}")
receipt_id=$(jq -er '.receipt_id' <<<"$receipt")
repeat_receipt=$(api_json_request POST "${app_url}/api/v1/businesses/${business_id}/goods-receipts/${preview_id}/confirm" 200 "$confirm_payload" "trial-confirm-repeat-${business_id}")
[[ "$(jq -er '.receipt_id' <<<"$repeat_receipt")" == "$receipt_id" ]] || { echo "Confirmation is not idempotent." >&2; exit 1; }

reprocess=$(api_json_request POST "${app_url}/api/v1/businesses/${business_id}/nfe-documents/${document_id}/reprocess" 200 '{}' "trial-reprocess-${business_id}")
[[ "$(jq -r '.id' <<<"$reprocess")" == "$document_id" ]] || { echo "Reprocess identity mismatch." >&2; exit 1; }

movements=$(db_query "SELECT count(*) FROM inventory_movements WHERE business_id = '${business_id}' AND receipt_id = '${receipt_id}';")
balances=$(db_query "SELECT count(*) FROM inventory_balances WHERE business_id = '${business_id}';")
stock_quantity=$(db_query "SELECT quantity FROM inventory_balances WHERE business_id = '${business_id}' LIMIT 1;")
receipts=$(db_query "SELECT count(*) FROM goods_receipts WHERE business_id = '${business_id}' AND id = '${receipt_id}';")
versions=$(db_query "SELECT count(*) FROM nfe_document_versions WHERE document_id = '${document_id}';")
schema_version=$(db_query "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;")

[[ "$movements" == "1" && "$balances" == "1" && "$stock_quantity" == "5.000000000" && "$receipts" == "1" && "$versions" -ge 2 ]] || {
    echo "Unexpected Trial E2E database state: movements=${movements}, balances=${balances}, quantity=${stock_quantity}, receipts=${receipts}, versions=${versions}" >&2
    exit 1
}
[[ "$schema_version" == "13" || "$schema_version" == "V13" ]] || { echo "Unexpected schema version: ${schema_version}" >&2; exit 1; }

echo "Trial smoke PASS: business=${business_id} document=${document_id} preview=${preview_id} receipt=${receipt_id} movements=${movements} stock=${stock_quantity} versions=${versions} schema=${schema_version}"
