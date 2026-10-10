# KELIOS — Codex Implementation Task

**Language:** English only.  
**Authority:** Read `ARCHITECTURE.md` and `DOMAIN.md` before changing code. These are approved specifications, not optional inspiration.

## Objective

Implement KELIOS incrementally, **starting with architecture and deployable frontend/backend separation**, then the smallest working vertical slices of the approved domain. Do not begin by scaffolding every feature or creating speculative abstractions.

## Mandatory execution order

### Phase 0 — Architecture first (hard gate)

1. Inspect the existing repository and report what exists; do not overwrite working code blindly.
2. Produce a concise architecture plan covering:
   - Java Spring Boot modular-monolith backend **built with Apache Maven 3** (`pom.xml` + committed Maven Wrapper, no Gradle), and React/TypeScript/Vite frontend as independent applications.
   - REST API boundary, module/package boundaries, PostgreSQL migrations, environment/secret configuration.
   - Two separate, independently built **multi-stage Docker images** (`backend` and `frontend`), with minimal runtime stages; backend build stage must run **Maven 3**, while its runtime stage must not contain Maven.
   - Runtime-injected frontend **logo and theme colors**, configurable without rebuilding the frontend image. Explain injection via mounted file(s) and/or runtime parameters, defaults, validation, and static asset handling.
   - Local deployment composition with PostgreSQL; API routing, authentication transport implications, health checks.
   - Security implications of OIDC, JWT storage/refresh and cross-origin deployment.
3. Identify unresolved implementation choices and propose minimal options. Do **not** silently convert proposals into domain requirements.
4. Record approved decisions in `ARCHITECTURE.md` or an ADR; maintain spec traceability.
5. Scaffold and verify both applications and both Docker builds. Commit Maven Wrapper configured for Maven 3 and verify `./mvnw -B verify` in the backend directory. Demonstrate the **same frontend image** with two different runtime themes/logos.
6. Run and document smoke tests. **Do not start domain implementation until Phase 0 acceptance criteria pass.**

### Phase 1 — Domain foundation

The user-approved 2026-10-10 Domain revision supersedes Product Merge and resolves the mutation/tag/input/actor decisions. The revised `docs/plans/PHASE_1_PLAN.md` was explicitly approved by the user on 2026-10-10 for Phase 1 implementation and verification only. Phase 2 remains outside that approval.

1. Implement migrations and entities for Tenant, User, Category, Manufacturer, Product, Location, Owner, Asset, AssetEvent, AuditLog according to `DOMAIN.md`. Tenant contains UUID/name; User contains UUID/scope/tenantId/role/status/displayName only. Do not implement authentication fields. Retain actor UUID/display snapshots without mandatory live User records; privacy policy remains deferred.
2. Add explicit foreign-key/composite-key, CHECK/UNIQUE, transactional validation, and concurrency protections for Tenant isolation, visibility, Product-derived references, and mutation guards. Document actual enforcement responsibilities. Seed protected GLOBAL `UNKNOWN` Category/Manufacturer entries.
3. Implement generic state-machine engine and tests independent of persistence/domain code. Reject self-transitions and all edges absent from the approved graphs.
4. Add transactional Asset registration with generated UUID and UTC `yyyyMMdd-NNN` tag, application-wide atomic daily sequence, expanding digits, and database uniqueness (gaps allowed). Load Product by ID and derive Category/Manufacturer; without Product require their IDs. Require explicit loanable, no initial active loan, optional blank-text normalization, immutable AssetEvent, and optimistic locking.
5. Implement/test independent edit, soft deletion, publication, and explicit Category/Manufacturer Merge operations with Domain reference guards. Merge redirects Product/Asset references, creates UPDATED events for affected Assets, soft-deletes source, and creates AuditLog atomically. Test Location editing by stable ID without LOCATION_CHANGED events; reject referenced Location deletion. Product editing freezes after any Asset registration reference; its deletion requires no current Asset references. **Do not implement Product, Owner, or Location Merge**, automatic duplicate detection/merge, or name-based uniqueness. Commands use reference IDs and server-derived facts.

### Phase 2 — Authentication and authorization

1. Implement secure local GLOBAL authentication and first-admin bootstrap.
2. Implement database-configured per-Tenant IServ OIDC discovery and verified issuer/subject mapping.
3. Verify actual IServ group claim availability before coding allowlist mapping; fail closed if unavailable.
4. Implement JIT `PENDING` user creation, approval by GLOBAL ADMIN / same-Tenant TENANT ADMIN within authorized limits, and read-only roles.
5. Implement pure short-lived access JWT + long-lived refresh JWT, refresh with current User reloading, frontend logout by discarding tokens, and no token revocation subsystem.
6. Enforce PENDING/DISABLED and Tenant boundaries on backend endpoints. Test privilege escalation and cross-Tenant attacks.

### Phase 3 — Inventory workflows and UI

1. Implement Asset browse/search/detail/registration/edit with scope-aware filtering.
2. Implement lifecycle and condition transitions, independent Location/Owner changes, and lightweight embedded loan/return behavior.
3. Display immutable AssetEvent timeline and administrative AuditLog where authorized.
4. Provide minimal usable Catalog and Tenant admin UIs, including separate publication and Category/Manufacturer Merge with confirmation and impact preview, and entity-specific edit/deletion guards. No Product/Owner/Location Merge.
5. Use TanStack Table and shadcn/ui without building a custom design system; theme through runtime branding configuration.

### Phase 4 — Stabilization

1. Add automated tests for all invariants listed in `DOMAIN.md`.
2. Verify production multi-stage images and reproducible documented local deployment.
3. Verify runtime logo/color changes require **no image rebuild**.
4. Document API, configuration, bootstrap, OIDC setup, and operational procedures.
5. List intentionally deferred features rather than implementing them.

## Deliverables

- `ARCHITECTURE.md` maintained as the architecture contract.
- `DOMAIN.md` maintained as the business contract.
- Working independent frontend and backend applications; backend has `pom.xml`, committed Maven 3 Wrapper, and a documented `./mvnw -B verify` command.
- Independent multi-stage frontend and backend Dockerfiles/images.
- Documented runtime frontend branding injection, including sample config and two-theme smoke test.
- PostgreSQL schema migrations, tests, and minimal deployment example.
- Incremental implementation notes identifying completed acceptance criteria and unresolved decisions.

## Non-goals / constraints

Do not add Redis, microservices, token blacklist/session database, organization hierarchy, multiple Kreise, PhysicalDevice, automatic duplicate detection or Catalog merge, name-based uniqueness, Product/Owner/Location Merge, QR code, printer integration, RepairOrder, procurement, or a PIM. Do not invent IServ group claims or User deletion/anonymization policy. Do not bypass mandatory approval. Do not put secrets in frontend runtime config or images.

## Definition of done for each phase

A phase is complete only when its acceptance criteria are demonstrably met with runnable commands/tests, documentation is updated, and no known violation of `DOMAIN.md` or `ARCHITECTURE.md` remains. If a requirement is ambiguous, ask a precise question before implementing irreversible behavior.

## First instruction to Codex

**Start with Phase 0 only.** Inspect the repository, present the architecture plan and open decisions, then implement and verify independent multi-stage frontend/backend builds and runtime-injected branding. Do not implement business domains until the architecture gate passes.
