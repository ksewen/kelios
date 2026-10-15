# ADR 0001: Independent applications and runtime branding

Status: Accepted by the user on 2026-10-10. Scope: Phase 0 only.

## Context

`ARCHITECTURE.md` sections 1–2 mandate a Maven 3 modular monolith, independently deployable frontend/backend images, PostgreSQL, and runtime branding. Section 7 reserved implementation choices. The user approved [Phase 0 plan](../history/phase0-4.md) with refinements recorded here. No business invariants in `DOMAIN.md` change.

## Decisions

- Java 25 LTS and Spring Boot 4.1.1; verify compatibility with an actual build. Spring Boot parent artifact returned HTTP 200 from Maven Central on 2026-10-10. Pin Maven 3.9.16 through the Apache Maven Wrapper with checksum verification; never use Maven 4 or Gradle.
- Node 24 LTS, React/TypeScript/Vite, Tailwind, shadcn/ui-compatible components, and TanStack Table. Lock npm dependencies. No business table UI in Phase 0.
- `backend/`, `frontend/`, `deploy/`, and `docs/` separate build, deployment, and documentation concerns. One backend Maven project; package boundaries follow Architecture section 3.
- Multi-stage Docker builds use BuildKit caches and verification. Backend runtime uses a non-root JRE, without Maven/compiler/source. Frontend runtime uses unprivileged nginx, without Node/npm/source.
- Pin Docker base images by digest. The backend builder includes `unzip` so the Wrapper validates the pinned ZIP checksum rather than substituting a tar distribution; builder packages never enter the runtime stage.
- Docker Compose runs PostgreSQL and both applications. Flyway SQL migrations execute at startup; Phase 0 migration has no business tables. PostgreSQL is the only persistence service.
- REST uses `/api/v1`, JSON, UUID identifiers when later needed, UTC timestamps, RFC 9457 problems, and checked-in OpenAPI. Technical status is the only Phase 0 API.
- Frontend proxy provides same-origin `/api/` routing. Root-path deployment is supported. Separate origins/subpaths require later deployment review; no permissive CORS configuration.
- Mount public JSON and logo assets read-only. `KELIOS_CONFIG_FILE` chooses the container source file; nginx serves it at `/runtime/config.json`. The environment variable is consumed by the container, not Vite. React validates config before rendering; safe defaults and a text logo are always available. See `docs/operations/runtime-branding.md`.
- Independently tag frontend/backend releases and pin deployment digests for production. Local Phase 0 tags use `kelios-backend:phase0` and `kelios-frontend:phase0`; no CI provider selected.
- Health checks distinguish backend liveness from database-dependent readiness. Management endpoints remain internal. Local Compose uses file-mounted credentials; public images contain none.

## Future authentication decision

Use an in-memory Bearer access JWT and a Secure, HttpOnly refresh JWT cookie. Before implementation specify narrowly scoped cookie path, SameSite, TLS, origin validation, CSRF and XSS protections. Cross-origin deployment requires explicit credential-aware CORS allowlists and cookie policy. No server token/session store or revocation is introduced. OIDC identity remains verified `(issuer, subject)`; real IServ claims still require verification. Authentication and authorization are not implemented in Phase 0.

## Dependency direction

Future inventory depends on tenancy/catalog application interfaces and audit interfaces; identity depends on tenancy interfaces; catalog depends on tenancy/audit interfaces. Do not access another module's repositories directly. `engine.statemachine` remains independent of domain, persistence, OIDC, and authorization. Phase 0 contains only bootstrap and `platform` technical endpoints. No speculative domain classes are created.

## Consequences and verification

Images can release independently; branding can change with no rebuild. Startup configuration is public and cannot hold secrets. Docker verification requires narrowly scoped daemon approval under the existing sandbox. Builds/cache files stay within the repository. Phase 0 acceptance required actual Maven/npm builds, both Docker builds, healthy Compose startup, migration evidence, and the same frontend image rendered with two mounted brands. The [accepted closeout](../history/phase0-4.md) retains results and limitations; failed or unavailable checks were not marked complete.
