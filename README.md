# KELIOS

KELIOS is a Tenant-aware inventory application for school equipment. It provides Asset registration and lifecycle/history, Catalog and place management, administration, Audit, GLOBAL Email login and Tenant IServ/OIDC login. [Architecture](ARCHITECTURE.md) explains the technical boundaries; [Domain](DOMAIN.md) defines the business and authorization rules. The checked-in [OpenAPI](docs/api/openapi.yaml) is the HTTP reference.

## Run KELIOS locally

The supported guided local workflow is **[the operator deployment guide](docs/operations/local-deployment.md)**. Start in a fresh repository source directory and run `./kelios install` as that guide describes. It covers prerequisites, localhost TLS/certificates, GLOBAL ADMIN Email bootstrap, private `.kelios/` state, URL, status, logs, start/stop, recovery and uninstall/data retention. Do not reuse or overwrite another installation's `.kelios/` state. [Authentication operations](docs/operations/authentication.md) covers keys and Provider configuration; [runtime branding](docs/operations/runtime-branding.md) covers mounted visual configuration; [Asset CSV operations](docs/operations/asset-csv.md) covers filtered export and supervised legacy import. Real school IServ interoperability still needs the [operator-controlled manual check](docs/operations/iserv-interoperability.md).

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

The Maven `verify` gate includes PostgreSQL integration tests and needs Docker. The independent backend Docker builder runs unit verification with `-DskipITs`; it does not replace host PostgreSQL verification. Browser/isolated smoke workflows live under `frontend/tests/` and `scripts/`; their generated evidence belongs in ignored `.cache/verification/`. The [disposable restore drill](docs/development/isolated-restore.md) is historical development verification, not an operator backup policy. [Frontend localization maintenance](docs/development/frontend-localization.md) describes en/de resource checks.

## Documentation and current work

[AGENTS.md](AGENTS.md) governs repository work and documentation lifecycle. [ADRs](docs/adr/) record durable design decisions. [Remaining work](docs/plans/remaining-work.md) indexes genuine open, blocked and future items; its links identify the active Plans. Accepted implementation and limitations are summarized in compact [Phase 0–4](docs/history/phase0-4.md), [backend](docs/history/backend-domain.md), [frontend](docs/history/frontend-integration.md), [Deployment CLI](docs/history/deployment-cli.md) and [interaction](docs/history/frontend-interactions.md) closeouts. Dated Plans and audits are evidence, never substitutes for current Architecture, Domain, OpenAPI, source or separately approved work gates.
