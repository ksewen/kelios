# KELIOS — Architecture Specification

**Status:** Approved architectural baseline; implementation details marked *Open* require a decision before implementation.  
**Language:** English only.  
**Priority:** Establish and validate this architecture **before implementing business features**.

## 1. System boundaries

KELIOS is an open-source, single-district IT asset-management application built as a **modular monolith with a strictly separated frontend and backend**.

- **Backend:** Java, Spring Boot, REST API, PostgreSQL; **Apache Maven 3** is the required backend build system. Use a committed Maven Wrapper (`mvnw`, `mvnw.cmd`, `.mvn/wrapper`) with a Maven 3 distribution to make builds repeatable. Do not use Gradle.
- **Frontend:** React, TypeScript, Vite, shadcn/ui, Tailwind CSS, TanStack Table.
- **Deployment artifacts:** **two separate Docker images**, one for the backend and one for the frontend. Do not bundle the React build into the Spring Boot JAR.
- **Build:** use **multi-stage Docker builds** for both images. The backend **builder stage must use Maven 3** (prefer `./mvnw` with a pinned Maven 3 version) to produce the Spring Boot artifact; its separate runtime stage runs that artifact without Maven. The frontend uses its own Node/Vite build stage and separate static-file-serving runtime stage. Build tools and source files must not be included unnecessarily in final runtime images.
- **Runtime:** configuration via environment variables, secrets, and mounted configuration files; no district-specific branding or secrets baked into public images.
- **Infrastructure:** PostgreSQL; **no Redis**. Do not introduce microservices.

## 2. Architecture-first gate

Before implementing domain functionality:

1. Define repository layout, frontend/backend boundaries, REST contract conventions, database migration strategy, and configuration loading.
2. Define backend module boundaries and dependency direction; use one deployable Spring Boot application, **Maven 3** and the committed Maven Wrapper; avoid unnecessary Maven submodules. Confirm JDK/Spring Boot compatibility with the selected Maven 3 release.
3. Design and demonstrate independent multi-stage Docker builds producing a frontend image and a backend image.
4. Define deployment composition (including PostgreSQL), startup/health checks, and configuration injection.
5. Define **runtime frontend branding** so the **same frontend image** can change theme colors and logo via injected file(s) or parameters **without rebuilding**. Document schema, validation, defaults, and fallback behavior. No organization-specific brand assets in the public repository/image.
6. Document decisions and open questions in an architecture decision log or clearly labeled sections. Stop for decisions that would alter domain invariants.
7. Only then proceed to the implementation tasks in `CODEX_IMPLEMENTATION_TASK.md`.

### Runtime frontend branding requirement

A deployment must be able to supply a runtime configuration file (for example a JSON file mounted into the frontend container and served at a stable path) or supported runtime parameters. It must support at least:

- Logo asset URL/path (a deployment-supplied file may be mounted alongside configuration).
- Theme colors / CSS design tokens suitable for the React UI.

The React app must load and validate runtime branding **before or during initialization**, apply safe defaults if absent, and avoid build-time-only Vite variables for these settings. Ensure frontend assets and logo URLs work under the documented deployment path. Treat runtime configuration as public client-side data; **never put secrets in it**.

The backend and frontend images must be independently versionable and deployable. Specify how frontend API requests are routed (e.g., configurable base URL or reverse proxy) and document CORS/cookie implications; do not assume one unexamined deployment topology.

## 3. Backend structure

Logical modules (package boundaries, not separate services):

- `tenancy`
- `identity`
- `catalog`
- `inventory`
- `audit`
- `engine.statemachine`

Base Java package: `com.github.ksewen.kelios`.

The reusable state-machine engine at `com.github.ksewen.kelios.engine.statemachine` must be generic and independent of Asset, JPA, IServ, and authorization. Application/domain services own authorization, transactions, persistence, validation, `AssetEvent`, and `AuditLog` creation. Use optimistic locking for Asset.

## 4. Security and authentication architecture

The user-approved Phase 2 D1–D5 and D7–D9 clarification (2026-10-10) is recorded here and in the [revised Phase 2 plan](docs/plans/PHASE_2_PLAN.md). The user subsequently explicitly approved Phase 2 implementation. [ADR 0003](docs/adr/0003-phase-2-authentication-and-authorization.md) records implementation decisions; ADR 0001/0002 retain their historical scope.

- GLOBAL-only local credentials use supported Spring Security Argon2id, with approved initial parameters benchmarked against bounded resources and final costs recorded in verification evidence. Credential data lives in `local_global_credential`, separate from the minimal Phase 1 User. Domain specifies account/password behavior; no custom password cryptography or forced first-login password-change state.
- A deployment-side bootstrap/recovery command atomically creates GLOBAL ADMIN only at zero usable GLOBAL administrators, as defined in Domain. Lock a singleton database guard row, freshly check usable administrators, then create User/credential/AuditLog together. The guard is a narrow mutex, with no permanent completion flag/timestamp or recovery secret. No browser endpoint or automatic startup bootstrap. Normal administration rejects destructive self disable/demotion/removal; no permanent last-active-admin invariant.
- TENANT users authenticate with their Tenant's database-configured IServ OIDC provider, administered by GLOBAL ADMIN and dynamically resolved by OIDC Discovery. `tenant_oidc_config`, `tenant_oidc_allowed_group` and `external_identity` preserve exact `(issuer,subject)` mapping and Tenant constraints. Do not match email/display name or generalize into a provider platform.
- Encrypt OIDC client secrets at rest using AES-256-GCM envelopes and externally supplied versioned encryption keys. No credentials, secrets or signing/encryption keys in migrations/source/images/public frontend runtime configuration. Secret reads are sanitized; provider tokens are request-local and discarded.
- OIDC uses state, nonce, PKCE S256 and a bounded authenticated-encrypted HttpOnly flow cookie. No server-side authorization-request session/table. Validate Discovery, exact issuer, endpoints/JWKS, signatures, audience, callback binding and redirects, with SSRF/egress defenses. Trusted deployment origin supplies fixed redirect URIs, not unvalidated forwarded headers.
- IServ group integration implements the official documented contract cited in Phase 2 plan §8. Prefer documented group UUID identifiers over names. Documentation establishes an adapter contract; deterministic tests and real-IServ interoperability are separate evidence. Real provider verification remains mandatory before claiming full IServ interoperability. The user clarified that unavailable real-provider access is an acceptance-evidence gap, not unfinished production code: implementation and deterministic documented-contract verification may pass separately. Any unverified group condition fails closed.
- JIT always creates `TENANT / USER / PENDING`; mandatory approval cannot be bypassed and IServ groups never grant ADMIN. PENDING JWTs allow minimal authentication/status interactions but no business data. Group allowlisting occurs at OIDC login, not during KELIOS Refresh. No continuous synchronization or stored IServ token/session. KELIOS DISABLED is the manual removal mechanism.
- **Pure JWT v1:** RS256; private signing key mounted outside source/image/database/frontend config; trusted verification keys selected by `kid`; explicit issuer/audience and separate Access/Refresh purpose/type. Access approximately 15 minutes; Refresh approximately 30 days absolute, never extended by refresh; clock skew 30 seconds. Refresh reloads current KELIOS User and refuses DISABLED. Access expires naturally after permission/status changes; password changes do not independently revoke JWTs. No Redis, blacklist, server-side token/session table, rotation store, `authVersion`, `tokensValidAfter` or per-device sessions.
- **Approved browser Option A:** Access JWT only in frontend memory, Authorization Bearer; Refresh JWT in Secure, HttpOnly, host-only cookie. Never use localStorage/sessionStorage for tokens. Reload obtains access through refresh; logout clears cookie and memory with no server revocation state. Supported Phase 2 browser baseline is same-origin HTTPS; CORS disabled by default. Cross-origin/cross-site authentication remains unsupported/deferred.
- Unsafe endpoints carrying ambient refresh credentials require explicit stateless signed double-submit CSRF protection and exact trusted-Origin validation. Separate OIDC flow binding protects the callback; do not introduce an HTTP session/BFF. Cookie path/SameSite and local TLS verification are specified in the plan; XSS protections remain necessary despite HttpOnly.
- Backend enforces explicit scope, role, status and same-Tenant authorization at HTTP and supported application-service boundaries, loading authoritative targets by ID. Entities and the generic state-machine engine contain no security infrastructure. PostgreSQL uses existing READ COMMITTED and operation-specific locking, never a universal concurrency framework.
- Successful administrative/security mutations create sanitized AuditLog atomically, including GLOBAL_ADMIN_BOOTSTRAPPED for installation/recovery. No permanent per-login AuditLog or security-log domain; failures/provider errors/throttling use bounded redacted operational logs without introducing a retention policy.

## 5. Persistence, audit, and lifecycle

- PostgreSQL is the source of truth. Use version-controlled schema migrations.
- Domain operations are transactional; Asset changes and corresponding AssetEvents commit together.
- Business entities use soft deletion (except separate User/Identity privacy requirements). Historical references remain interpretable.
- `AssetEvent` is immutable business history; `AuditLog` records system/administrative actions. They are not interchangeable.
- Catalog publish promotes the **same entity ID**. Explicit Category/Manufacturer Merge redirects current Product/Asset references, soft-deletes source, and records AuditLog plus UPDATED AssetEvents for redirected Assets in one transaction. Product, Owner, and Location Merge are excluded from v1 by the approved 2026-10-10 Domain revision; edit/deletion guards are defined in `DOMAIN.md`.

## 6. Build and deployment acceptance criteria

Phase 0 build/deployment checks passed on 2026-10-10; runnable commands, logs, image IDs, screenshots, and verification limits are recorded in [Phase 0 evidence](docs/verification/PHASE_0.md). The checklist below remains the architectural contract. Maven Wrapper files are supplied in the repository but intentionally uncommitted under the user's explicit Git policy; no commit or push was authorized. No business domain or authentication implementation followed the gate.

- [ ] Backend uses **Maven 3 only**, with `pom.xml` and committed Maven Wrapper files; no Gradle build.
- [ ] `./mvnw -B verify` succeeds from the backend directory using the pinned Maven 3 distribution.
- [ ] Backend multi-stage Docker builder uses Maven 3 / `mvnw`; final backend runtime image contains neither Maven nor the build toolchain.
- [ ] `frontend` and `backend` have independent multi-stage Docker builds.
- [ ] Build produces **two distinct runtime images**; frontend is not packaged into backend.
- [ ] Final runtime images exclude build-time dependencies where practical.
- [ ] Deployment can start frontend, backend, and PostgreSQL using documented configuration.
- [ ] Same frontend image runs with at least two different injected logos/color palettes without rebuilding.
- [ ] Branding configuration contains no secrets and has documented validation/fallback.
- [ ] Backend secrets/OIDC credentials are not embedded in images or frontend files.
- [ ] API routing, security, health checks, migrations, and environment configuration are documented.
- [ ] No Redis or other unapproved infrastructure is required.

## 7. Phase 0 technical decisions

The user approved the technical baseline on 2026-10-10, with refinements recorded in [ADR 0001](docs/adr/0001-phase-0-foundation.md). Use Java 25 LTS, Spring Boot 4.1.1 (artifact availability and compatibility must be verified), a pinned Maven 3.9.x Wrapper, and Node 24 LTS. Use unprivileged nginx, Docker Compose, Flyway SQL migrations, `/api/v1` JSON REST with RFC 9457 errors, and a checked-in OpenAPI specification.

Both independent multi-stage Docker builds use BuildKit caches where appropriate. The backend builder runs Maven verification; its non-root JRE runtime contains no Maven toolchain. Runtime branding uses mounted JSON and logo assets; `KELIOS_CONFIG_FILE` selects the frontend container's source file, served at `/runtime/config.json`. See [branding contract](docs/RUNTIME_BRANDING.md). No image rebuild is required for branding changes.

Use same-origin API proxying for root-path deployment and independent frontend/backend image versions. The in-memory access JWT / Secure HttpOnly refresh-cookie strategy is an accepted **future** decision; authentication is not implemented in Phase 0. This future transport decision is now refined by approved Phase 2 decisions in §4 and §9. CI provider selection, real-IServ interoperability verification, cross-origin cookie support, and subpath support remain deferred. These Phase 0 technical decisions did not change business invariants; the subsequent approved Domain revision is recorded separately in `DOMAIN.md`.

### Original decision gate

Technical choices must be explicitly recorded before implementation; do not turn proposals into hidden requirements. The selections above supersede the original open choices only within their documented scope.

## 8. Phase 1 technical decisions

The user approved the revised [Phase 1 plan](docs/plans/PHASE_1_PLAN.md) on 2026-10-10. [ADR 0002](docs/adr/0002-phase-1-domain-persistence.md) records Flyway-validated JPA mappings, Asset optimistic locking, composite/deferred structural constraints, normal READ COMMITTED transactions with operation-specific parent locks, indexed immutable Product usage history, atomic UTC tag allocation, and only the scoped AssetEvent/UNKNOWN trigger protections. No universal SERIALIZABLE/locking framework, Product usage flag, or generic purge trigger is introduced.

Business services remain internal. HTTP authentication/authorization remains Phase 2; business REST/UI and lifecycle/loan workflows remain Phase 3. Full Maven verification runs PostgreSQL Testcontainers on the host. The isolated Docker builder explicitly runs unit verification with `-DskipITs`; this does not replace the required PostgreSQL gate. Independent images, non-root toolchain-free runtime, and runtime branding remain the Phase 0 baseline. Actual Phase 1 results are recorded in [Phase 1 evidence](docs/verification/PHASE_1.md).

## 9. Phase 2 decision traceability and implementation gate

D1, D2, revised D3, D4, D5, D7, D8 and D9 are approved and no longer open decisions. Revised D3 supersedes the proposed permanent bootstrap-completion marker and last-active-admin protection; recovery instead uses a zero-usable-admin guard plus normal administrator self-protection. D2 replaces the proposed no-composition password policy. D7 fixes administrator-created GLOBAL users at USER/ACTIVE; Tenant JIT remains USER/PENDING. D8 fixes login-only IServ eligibility checks and current KELIOS User reload on Refresh.

D6 official-documentation research and its exact findings/limits are recorded in [Phase 2 plan §8](docs/plans/PHASE_2_PLAN.md#8-iserv-group-contract-and-real-provider-verification-gate). The approved implementation includes the documented adapter and deterministic fixtures. No real IServ compatibility is claimed by documentation or fixtures alone. No real provider is available this cycle; do not block implementation or request credentials. Record implementation/contract results separately from pending manual interoperability evidence.

Preserve historical ADR 0001/0002, V1–V3, Phase 0/1 implementation and evidence. The user approved implementation after the documentation review. [ADR 0003](docs/adr/0003-phase-2-authentication-and-authorization.md), [authentication operations](docs/AUTHENTICATION.md) and [Phase 2 evidence](docs/verification/PHASE_2.md) document the implemented design, actual checks and remaining manual interoperability gate. Phases 0–2 are accepted. Phase 3 specification/plan decisions are approved, and Phase 3 implementation is now explicitly approved; no commits or pushes are authorized.

## 10. Approved Phase 3 audit architecture

The user approved P1–P8 and the DEREGISTERED event convention in the [revised Phase 3 plan](docs/plans/PHASE_3_PLAN.md). Domain defines observable commands and audit ownership; this section records the necessary persistence/query changes. Preserve ADR 0001–0003 as historical records. The user subsequently approved implementation; [ADR 0004](docs/adr/0004-phase-3-inventory-and-target-owned-audit.md) records Asset command/API boundaries, explicit Tenant URL context, bounded pagination/search, audit attribution/read authorization and no user-facing Asset soft deletion.

- Add immutable nullable UUID `AuditLog.tenantId` / `audit_log.tenant_id`. Non-null means a known Tenant-owned target; null means known installation/global ownership. No AuditScope enum or third attribution state. Every supported writer explicitly derives ownership from the authoritative created target or loaded pre-operation target inside the mutation transaction; publication uses pre-publication Tenant and Merge uses pre-operation source. No actor/current-target/JSON inference or implicit unknown-to-null fallback.
- Keep actor snapshots separate. Store historical Tenant UUID **without a live Tenant FK**, consistent with historical actors not requiring live User FKs. CASCADE would erase history, SET NULL would mislabel it global, and RESTRICT would prematurely impose a future deletion policy. This introduces no Tenant deletion/privacy workflow.
- ACTIVE TENANT ADMIN audit selection, detail and count queries include `tenant_id = authenticatedTenantId` **in the database before selection/count/pagination**. ACTIVE GLOBAL ADMIN may read all rows, with optional narrowing filters. USER/PENDING are denied; absent/out-of-scope detail has the same response. No fetch-all/Java filtering, global-row fallback for Tenant Admin, live ownership joins or new account-revocation model.
- An additive Phase 3 Flyway migration is required for the Tenant column and scoped read index; never edit V1–V4. KELIOS has not been deployed and pre-Phase-3 development/test AuditLog rows are disposable. The plan specifies a clean pre-deployment cutover without production historical reconstruction/backfill, legacy markers or compatibility state. Other accepted business/history data remains intact. Fresh V1-to-latest and structural V4 upgrade are PostgreSQL verification requirements after approval.
- Reuse existing READ COMMITTED transactions, target locks, mandatory transactional AuditService and immutable mapping/service boundary. Capture attribution before ownership-changing SQL; audit commits atomically with the operation. No new generic trigger, global concurrency framework, AuditLog fan-out or duplication of AssetEvent is proposed.

This is specification/planning only. No Phase 3 production code, migration, OpenAPI, test or deployment change is authorized until explicit implementation approval. Real IServ interoperability remains a separately pending manual evidence item and does not block Phase 3.


## 11. Approved Phase 4 specification — stabilization and frontend localization

Phases 0–3 are accepted by the user. The approved [Phase 4 plan](docs/plans/PHASE_4_PLAN.md) adds the narrow Domain localIdentifier extension, original stabilization work and a frontend internationalization foundation with complete **English (`en`) and German (`de`)** UI coverage. Implementation is **explicitly approved**, recorded in [ADR 0005](docs/adr/0005-phase-4-stabilization-and-localization.md); user acceptance remains pending review. ADR 0001–0004 retain their historical scope/status.

- Keep Domain/API values, property names, enum codes, errors and UTC timestamps language-independent. Frontend-owned text and stable-code display labels are localized; user-entered data and stored history/snapshots are never translated. No backend Accept-Language business behavior, User/Tenant locale column, language API/JWT/session or translated master data.
- Static version-controlled frontend translation bundles, one shared language context, unauthenticated English/Deutsch switch, browser-preference first-use selection (regional German maps to `de`, otherwise fallback `en`). Explicit preference alone may persist as validated `en`/`de` in localStorage; this is public presentation state. Access tokens remain memory-only; Refresh remains HttpOnly cookie; no authentication tokens/credentials in localStorage/sessionStorage.
- Known safe machine problem codes map to localized UI messages; unknown safe errors get localized generic fallback. Backend errors do not become locale-dependent. React renders data as text, never translation markup/unsafe HTML. Locale formatting affects display date/time/numbers only, not API/storage/identifier values.
- Translation completeness is verified deterministically for matching English/German keys and interpolation parameters, plus representative bilingual UI/browser tests. Runtime branding and localization are independent: both languages and both accepted brand variants use the same frontend image without runtime rebuilding. No remote translation service/platform or per-Tenant translation administration.
- Additive V6 is planned for one nullable Asset local_identifier string; no uniqueness or dedicated index/filter/sort, new tables or identifier locking. Preserve V1–V5 and history, with no value inference/backfill. Field validation belongs to the existing supported registration/metadata transaction boundaries, not a generic validation framework or trigger.
- Stabilization includes invariant coverage mapping, full Phase 0–3 regressions, independent images/HTTPS/branding/documentation and a disposable isolated PostgreSQL backup/restore drill with externally supplied encryption-key availability. No backup subsystem, retention/RPO/RTO/HA policy, live-data destructive restore or new privacy policy.

Concrete i18n dependency/resources, detection order, migration validation and test organization are documented in the Phase 4 plan. The user approved i18next/react-i18next implementation; compatible pinned versions and actual verification are recorded in ADR 0005 and Phase 4 evidence.
