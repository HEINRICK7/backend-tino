# TINO — Hostinger VPS deployment

Status: **PREPARED; activation requires VPS and GitHub environment secrets**

This deployment is isolated from the existing Docker Compose projects on the
Hostinger VPS. It does not run `docker compose down`, does not restart unrelated
projects, and aborts when ports `80` or `443` are occupied by another service.

## Architecture

```text
api.tino.otimizanegocio.com  ─┐
                              ├─ nginx-proxy + ACME/Let's Encrypt
auth.tino.otimizanegocio.com ─┘
                                      │
                         ┌────────────┴────────────┐
                         │                         │
                    TINO app                  Keycloak
                         │                         │
                    PostgreSQL              Keycloak PostgreSQL
```

Only Nginx publishes ports `80` and `443`. PostgreSQL, Keycloak and the Spring
application are not published directly on the VPS host. The two PostgreSQL
instances use separate named volumes.

The deployment definition is [compose.vps.yaml](../deploy/compose.vps.yaml).
The CI/CD workflow is [deploy-vps.yml](../.github/workflows/deploy-vps.yml).

## Current Hostinger audit

The VPS found through the Hostinger tool is:

- virtual machine: `1491267`;
- hostname: `srv1491267.hstgr.cloud`;
- IPv4: `187.77.240.172`;
- OS: Debian 13;
- plan: KVM 4, 4 CPU, 16 GB RAM;
- existing Compose projects: cloudflared, evolution-api, hermes-agent,
  jornada-nutricionista, litellm and ollama;
- no TINO Compose project was present at audit time.

Existing firewall groups were inspected and were not changed. Existing
applications and their published ports were not stopped or restarted.

## DNS required

Recommended hostnames:

```text
api.tino.otimizanegocio.com  A  187.77.240.172
auth.tino.otimizanegocio.com A  187.77.240.172
```

Before the first deploy, confirm that these names resolve to the VPS. Do not
replace the root zone or existing records; add only the two new `A` records.

## GitHub environment

Create an environment named `tino-vps` in the repository and configure:

### Secrets

- `VPS_HOST`: VPS hostname or IPv4;
- `VPS_SSH_USER`: dedicated non-root deployment user;
- `VPS_SSH_PRIVATE_KEY`: private key used only by this repository;
- `VPS_SSH_KNOWN_HOSTS`: pinned SSH host key, obtained independently and
  reviewed before saving;
- `TINO_VPS_ENV`: multiline runtime environment file described below.

The workflow uses the repository `GITHUB_TOKEN` only for GHCR and passes no
secret into Git. The image is tagged both with the commit SHA and `main`; the
VPS deploy always uses the immutable SHA tag.

### `TINO_VPS_ENV` template

Store values, not this template, as the GitHub environment secret:

```dotenv
TINO_API_HOST=api.tino.otimizanegocio.com
TINO_AUTH_HOST=auth.tino.otimizanegocio.com
TINO_VPS_IPV4=187.77.240.172
TINO_LETSENCRYPT_EMAIL=seu-email-de-operacao@example.com
TINO_POSTGRES_ADMIN_PASSWORD=generate-a-long-random-value
TINO_POSTGRES_APP_PASSWORD=generate-a-long-random-value
TINO_POSTGRES_MIGRATOR_PASSWORD=generate-a-long-random-value
KEYCLOAK_DB_PASSWORD=generate-a-long-random-value
TINO_KEYCLOAK_ADMIN_USERNAME=admin
TINO_KEYCLOAK_ADMIN_PASSWORD=generate-a-long-random-value
TINO_FISCAL_MODE=fixture
OTEL_SDK_DISABLED=true
```

Do not use the local development passwords. Generate unique values for this
environment. The file is written on the VPS with permission `0600` and is not
committed.

## SSH user

Use a dedicated deployment user with permission to operate only the TINO
deployment directory and Docker, if the Hostinger policy supports it. Do not
put the root password in GitHub. If Docker administration requires a Docker
group membership, treat that user as equivalent to root and restrict the key
to this repository/environment.

## First deployment sequence

1. Create the two DNS `A` records and wait for propagation.
2. Confirm SSH key access and pin the host key in `VPS_SSH_KNOWN_HOSTS`.
3. Create the `tino-vps` environment and all secrets.
4. Merge the approved backend version into `main`.
5. Let `build-and-deploy-vps` pass the gates, publish the image and deploy.
6. Confirm `https://api.tino.otimizanegocio.com/actuator/health/readiness`.
7. Confirm `https://api.tino.otimizanegocio.com/swagger-ui.html`.
8. Confirm Keycloak at `https://auth.tino.otimizanegocio.com/realms/tino`.
9. Configure the Android build variant with the API and Keycloak HTTPS URLs.

The workflow performs a DNS check, an ownership check for ports `80/443`, a
compose config validation, an image pull, `up -d` for the TINO project and an
HTTPS readiness check. It never calls `down`, `stop` or `restart` on existing
projects.

## Security decisions

- Production Compose has no host-published database or Keycloak ports.
- The app container is read-only, drops Linux capabilities and disallows
  privilege escalation.
- Nginx terminates HTTPS and ACME renews certificates automatically.
- Runtime credentials are supplied only through the GitHub environment/VPS
  `.env`; none are in the repository or image.
- The deployment uses immutable image tags based on the commit SHA.
- GitHub Actions runs the secret scan, build, tests and architecture gates
  before deployment.
- `TINO_FISCAL_MODE=fixture` remains the safe Trial default. SERPRO Production
  is not enabled by this deployment.
- The existing Hostinger firewall groups were not modified by this preparation.

## Explicit non-goals

- replacing or migrating any existing Hostinger project;
- changing existing DNS records;
- changing existing firewall groups;
- exposing PostgreSQL or Keycloak directly;
- deploying SERPRO Production;
- storing secrets in Git, Dockerfile, fixture or Android;
- claiming that the first deploy is complete before the HTTPS smoke passes.

