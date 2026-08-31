#!/bin/sh
set -eu

TINO_ADMIN_USERNAME="${TINO_KEYCLOAK_ADMIN_USERNAME:-admin}"
TINO_ADMIN_CREDENTIAL="$(printenv TINO_KEYCLOAK_ADMIN_PASSWORD || true)"
if [ -z "$TINO_ADMIN_CREDENTIAL" ]; then
  echo "missing Keycloak administrator runtime credential" >&2
  exit 1
fi

KCADM=/opt/keycloak/bin/kcadm.sh
KCADM_SERVER="${TINO_KEYCLOAK_SERVER:-http://keycloak:8080}"
KCADM_CONFIG=/tmp/kcadm.config
kcadm() {
  command="$1"
  subcommand="$2"
  shift 2
  "$KCADM" "$command" "$subcommand" --config "$KCADM_CONFIG" "$@"
}
FLOW_ALIAS=tino-otp-browser

until kcadm config credentials \
    --server "$KCADM_SERVER" \
    --realm master \
    --user "$TINO_ADMIN_USERNAME" \
    --password "$TINO_ADMIN_CREDENTIAL" >/dev/null 2>&1; do
  sleep 2
done

flows="$(kcadm get authentication/flows -r tino)"
if ! printf '%s' "$flows" | grep -Eq '"alias"[[:space:]]*:[[:space:]]*"tino-otp-browser"'; then
  kcadm create authentication/flows -r tino \
    -s alias="$FLOW_ALIAS" \
    -s description="Passwordless browser flow for the TINO Android client" \
    -s providerId=basic-flow \
    -s topLevel=true \
    -s builtIn=false >/dev/null
fi

executions="$(kcadm get "authentication/flows/$FLOW_ALIAS/executions" -r tino)"
if ! printf '%s' "$executions" | grep -Eq '"providerId"[[:space:]]*:[[:space:]]*"tino-otp-ticket"'; then
  kcadm create "authentication/flows/$FLOW_ALIAS/executions/execution" -r tino \
    -s provider=tino-otp-ticket >/dev/null
fi

# This flow must never fall back to the generic username/password form.
if printf '%s' "$executions" | grep -Eq '"providerId"[[:space:]]*:[[:space:]]*"(auth-username-form|auth-password-form)"'; then
  echo "Refusing to bind TINO OTP flow: generic credential form is present" >&2
  exit 20
fi

kcadm update realms/tino -r tino -s browserFlow="$FLOW_ALIAS" >/dev/null
kcadm update authentication/required-actions/VERIFY_PROFILE -r tino -s enabled=false -s defaultAction=false >/dev/null
echo "TINO OTP browser flow configured"
