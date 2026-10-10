# Phase 0 architecture plan

Status: Approved by the user on 2026-10-10 with refinements in `docs/adr/0001-phase-0-foundation.md`; Phase 0 executable acceptance checks passed. No domain implementation authorized by this plan. The ADR supersedes proposal language below; actual evidence and verification limits are tracked in `docs/verification/PHASE_0.md`.

## Repository inspection

The initial directory contained `AGENTS.md`, `ARCHITECTURE.md`, `DOMAIN.md`, and `CODEX_IMPLEMENTATION_TASK.md`, plus `.DS_Store`. No application, build, deployment, tests, or Git metadata existed. There is no working code to preserve. At the user's explicit request, a local Git repository has now been initialized, with no commits or configured remote. Sandbox restrictions remain in place; initialization required command-scoped approval because `.git/config` writes were blocked. The required wrapper will be supplied as repository files, without creating a commit.

The host provides Java 25.0.2, Maven 3.9.1, Node 25.9.0, and Docker CLI 29.8.2. Docker daemon inspection was denied by sandbox permissions. Build and container verification remain pending.

## Proposed baseline and alternatives

| Choice | Proposed baseline | Minimal alternative |
| --- | --- | --- |
| Backend toolchain | Java 25 LTS, Spring Boot 4.1.1, pinned Maven 3.9.x wrapper with distribution checksum | Java 21 LTS with compatible Spring Boot |
| Frontend toolchain | React, TypeScript, Vite, Node 24 LTS; exact dependencies and npm lockfile | Another supported Node LTS |
| Static HTTP runtime | Unprivileged nginx on port 8080 | Caddy |
| Local orchestration | Docker Compose: frontend, backend, PostgreSQL | Document standalone container commands |
| Database migrations | Flyway SQL migrations, run during backend startup | Liquibase |
| REST conventions | `/api/v1`, JSON, UUID identifiers, UTC timestamps, RFC 9457 problem responses | Unversioned `/api` |
| API schema | Checked-in OpenAPI contract for scaffold endpoint; expand with later phases | Generated OpenAPI |
| Image versioning | Separate frontend/backend release tags and deployment digest pins | Shared release tag with separate images |
| Future token transport | Access JWT held in memory and sent as Bearer; refresh JWT in Secure, HttpOnly cookie | Both JWTs in HttpOnly cookies with CSRF protection |
| CI | Defer provider choice; document local verification commands | Select a provider separately |

These are implementation proposals, not changes to business invariants. Architecture section 7 explicitly leaves these choices unapproved. Record selected decisions in an ADR before implementing them. Resolve exact Maven and dependency patch releases from official repositories during scaffolding.

Compatibility references: [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html) supports Java 25 and Maven 3.6.3 or later; [Vite requirements](https://vite.dev/guide/) supports Node 22.12 or later; [Maven Wrapper](https://maven.apache.org/tools/wrapper/maven-wrapper-plugin/wrapper-mojo.html) supports a pinned distribution and checksum.

## Application and module boundaries

Use `backend/` for one Maven project, `frontend/` for one npm project, `deploy/` for Compose and generic branding fixtures, and `docs/` for decisions, contracts, operations, and verification evidence. Each application has its own Dockerfile and build context. Never copy frontend output into the backend JAR.

The backend base package is `com.github.ksewen.kelios`. Future modules are `tenancy`, `identity`, `catalog`, `inventory`, `audit`, and `engine.statemachine`. Inventory may depend on tenancy/catalog interfaces and audit interfaces; identity may depend on tenancy interfaces; catalog may depend on tenancy/audit interfaces. Expose narrow application interfaces rather than sharing repositories across modules. Infrastructure wires dependencies in one deployable application. The engine has no dependencies on domain entities, JPA, OIDC, or authorization. Phase 0 creates only application bootstrap, configuration, health, and a technical API endpoint; no speculative domain packages or entities.

Flyway owns schema history. Phase 0 verifies migration startup without introducing business tables. Later phases add reviewed, version-controlled migrations. Environment variables or mounted backend configuration supply database settings; secret mounts supply credentials. No secrets, bootstrap accounts, district assets, or OIDC credentials enter public images.

## Independent container builds and deployment

Backend: Maven 3 builder runs `./mvnw -B verify`; a separate JRE runtime runs only the packaged application as a non-root user. Exclude Maven, source, and build caches from runtime. Frontend: Node builder runs `npm ci` and the production build; nginx runtime contains static output and HTTP configuration, without Node or npm.

Compose runs PostgreSQL with persistent storage and `pg_isready`, backend with database-dependent readiness and independent liveness, and frontend with an HTTP health check. Expose the frontend locally; route `/api/` through nginx to the backend. Keep database and management endpoints off the public proxy. Support graceful shutdown and document required configuration, startup order, image tags, and port mappings.

The default deployment is at the origin root. Frontend SPA fallback must not intercept missing runtime config, logo, API, or static asset paths. Root-relative `/api/v1` requests work with the same-origin proxy. Subpath deployment is deferred and must not be claimed as supported by the scaffold.

## Runtime branding contract

Serve a public, uncached `/runtime/config.json`, loaded before rendering React. Proposed schema: `schemaVersion: 1`, optional `logoUrl`, and an allowlisted `theme` object containing hexadecimal CSS colors for background, foreground, primary, primaryForeground, accent, and accentForeground. Apply colors through CSS variables compatible with Tailwind/shadcn tokens. Ship neutral defaults and a text KELIOS logo fallback.

Validate object shape, schema version, value types, color syntax, and URL scheme. Restrict logo URLs to same-origin HTTP(S) paths/URLs; reject executable schemes and arbitrary CSS. Invalid individual fields fall back to defaults; missing, unreadable, malformed, or unsupported-version config uses all defaults. Logo load failure shows the text fallback. Never interpret config as HTML or include secrets. Bound config loading with a timeout so initialization cannot hang indefinitely.

Mount a deployment-owned config file and logo directory read-only in the frontend runtime. Use two generic, non-organization-specific sample palettes and logos. Run the exact same image ID with each mount; verify returned config, logo accessibility, rendered CSS variables, and fallback behavior. Record image identity and results as smoke-test evidence.

## Security implications reserved for later implementation

Phase 0 exposes only technical status and health; it does not issue JWTs, authenticate users, or expose business data. Future access JWTs in memory reduce persistent token exposure but remain vulnerable to XSS. Refresh cookies require TLS, a narrowly scoped path, SameSite policy, origin validation, and CSRF protection for refresh/logout interactions. Client logout discards the access token and clears the refresh cookie without a revocation subsystem; already issued access tokens remain valid until expiry.

Same-origin proxying avoids browser CORS requirements. Separate-origin deployment requires an explicit origin allowlist, credential-aware CORS for cookies, carefully selected cookie SameSite/domain policy, HTTPS, and CSRF controls; never use wildcard credentialed CORS. Public runtime config must not choose arbitrary credential destinations. Cross-site authentication topology needs explicit review before authentication implementation.

OIDC configuration will reside in PostgreSQL, with secrets encrypted at rest and encryption keys supplied externally. Verify discovery issuer and `(issuer, subject)` identities. Actual IServ group claims remain unresolved until verified with a real provider. Do not invent mappings or allowlist bypasses. New users remain PENDING; no Phase 0 decision changes domain authorization.

## Verification and traceability

After decisions are selected, implement and record runnable checks for:

- Backend `./mvnw -B verify`, wrapper Maven 3 version, and technical endpoint/health smoke checks.
- Frontend type checking, production build, branding validation/fallback, and rendered runtime branding.
- Independent backend and frontend Docker builds, runtime image inspection, and Compose validation/startup.
- PostgreSQL readiness, backend migration startup, frontend API proxy, and health endpoints.
- Two branding deployments using the same frontend image ID with distinct mounted palettes/logos.

These checks cover Architecture sections 1, 2, and 6 and Implementation Task Phase 0 items 1–6. Domain sections 1–10 remain constraints for subsequent phases, not scaffold implementation tasks. Phase 0 is incomplete until all required build/deployment evidence exists. No domain work follows automatically; the user requested Phase 0 only.
