# KELIOS

KELIOS is a Tenant-aware inventory application for school equipment. It provides Asset registration and lifecycle/history, Catalog and place management, administration, Audit, GLOBAL Email login and Tenant IServ/OIDC login. [Architecture](ARCHITECTURE.md) explains the technical boundaries; [Domain](DOMAIN.md) defines the business and authorization rules. The checked-in [OpenAPI](docs/api/openapi.yaml) is the HTTP reference.

## Deploy KELIOS

Use the [KELIOS deployment guide](docs/operations/deployment.md) for prerequisites, installation, configuration and routine operation. The [backup and disaster-recovery guide](docs/operations/backup-restore.md) covers managed database recovery. The CLI resolves its clean source checkout and stores persistent state outside it; no manual `.cache` preparation is needed. [Certificate operations](docs/operations/certificates.md), [Docker host access](docs/operations/docker-host-access.md), [authentication operations](docs/operations/authentication.md) and [runtime branding](docs/operations/runtime-branding.md) cover those boundaries. Linux AMD64 container verification passed; real Linux server production acceptance is still outstanding. Real school IServ interoperability needs the [operator-controlled manual check](docs/operations/iserv-interoperability.md).

## Develop and verify

Requirements for local builds are Java 25, Docker with Compose and BuildKit, and Node 24. The backend uses the committed Maven 3 Wrapper and PostgreSQL Testcontainers; Gradle and an H2 substitute are unsupported. From the repository root:

```sh
mkdir -p .cache/maven-home .cache/maven-repository .cache/npm .cache/tmp
cd backend
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -Dmaven.repo.local="$PWD/../.cache/maven-repository" verify
cd ../frontend
npm ci --cache ../.cache/npm
npm test
npm run build
```

The Maven `verify` gate includes PostgreSQL integration tests and needs Docker. The independent backend Docker builder runs unit verification with `-DskipITs`; it does not replace host PostgreSQL verification. Browser/isolated smoke workflows live under `frontend/tests/` and `scripts/`; put Node 24 and npm on `PATH` before running them. Their generated evidence belongs in ignored `.cache/verification/`. Run `./scripts/check-portable-paths.sh` to reject developer-local paths in tracked active source and documentation. `scripts/verify-deployment-cli.py` is a historical local fixture, not Linux server acceptance evidence. The [disposable restore drill](docs/development/isolated-restore.md) is historical development verification; operators use the [backup guide](docs/operations/backup-restore.md). [Frontend localization maintenance](docs/development/frontend-localization.md) describes en/de resource checks.

## Documentation and current work

[AGENTS.md](AGENTS.md) governs repository work and documentation lifecycle. [ADRs](docs/adr/) record durable design decisions. [Remaining work](docs/plans/remaining-work.md) indexes genuine open, blocked and future items; its links identify the active Plans. Accepted implementation and limitations are summarized in compact [Phase 0–4](docs/history/phase0-4.md), [backend](docs/history/backend-domain.md), [frontend](docs/history/frontend-integration.md), [Deployment CLI](docs/history/deployment-cli.md) and [interaction](docs/history/frontend-interactions.md) closeouts. Dated Plans and audits are evidence, never substitutes for current Architecture, Domain, OpenAPI, source or separately approved work gates.
