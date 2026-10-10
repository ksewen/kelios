# Phase 2 verification — 2026-10-10

| Verification level | Result |
|---|---|
| **Phase 2 implementation** | **Complete / passed** for the approved implementation scope and local acceptance checks below. |
| **Documented IServ contract verification** | **Complete / passed** through deterministic local HTTPS protocol/contract tests using production validation. |
| **Real IServ interoperability** | **Pending manual verification**: no real environment is available this cycle. This is an external acceptance-evidence gap, not unfinished production code or an implementation failure. No real compatibility claim is made. |

Implementation was explicitly approved after the D1–D9 documentation review. The latest user clarification permits the first two results independently of the third. [ADR 0003](../adr/0003-phase-2-authentication-and-authorization.md), [approved plan](../plans/PHASE_2_PLAN.md), [authentication operations](../AUTHENTICATION.md) and [later manual IServ procedure](PHASE_2_ISERV_MANUAL.md) provide traceability. No Phase 3, commit or push was performed.

## Implemented scope and schema

- GLOBAL-only canonical credentials with supported Spring Security Argon2id and uniform approved Unicode password policy; own change/current-password verification and administrator create/reset. GLOBAL administration creates USER/ACTIVE; ADMIN grant is separate.
- Explicit deployment non-web bootstrap/recovery at zero usable GLOBAL administrators, with atomic mutex/check/User/credential/AuditLog. Successful initial CLI and repeated refusal were exercised. No completion marker/default password/browser recovery/startup rebootstrap; self disable/demotion rejected, no last-admin invariant.
- RS256 trusted mounted keys, issuer/audiences/purpose/types/time validation, 15-minute Access and original absolute 30-day Refresh, current database User reload. No token/session/revocation persistence. Memory-only Bearer frontend + Secure/HttpOnly/host-only Refresh cookie, signed CSRF/exact trusted Origin and same-origin HTTPS, logout clearing only.
- Dynamic per-Tenant database OIDC, Discovery, bounded configuration-keyed metadata/JWKS caches, verified TLS/SSRF restrictions, state/nonce/PKCE, encrypted flow cookie, callback validation, standard Spring OIDC token validation and JOSE checks, UserInfo subject binding, exact issuer/subject mapping.
- Documented `openid profile iserv:groups`, UserInfo group objects with UUID `id`, UUID allowlist intersection, fail-closed malformed/missing/unavailable/empty membership, mandatory TENANT/USER/PENDING JIT, existing-user continuity and current KELIOS disabled behavior. IServ roles never grant ADMIN; refresh performs no provider lookup or synchronization.
- GLOBAL ADMIN sanitized GET/PUT OIDC configuration API with encrypted secret retention/re-encryption, UUID allowlist and atomic version conflict behavior. No deployment provider/client/secret/group/callback is baked into source/images; later provider setup uses configuration only.
- Explicit authenticated scope/role/status/Tenant rules at HTTP and secured Phase 1 application boundaries; fresh locked Catalog visibility, scoped user administration and server-derived actors. No inventory HTTP/UI or transition workflows.
- Administrative AuditLog actions, bounded status-only operational failure logging/process-local throttling, no permanent per-login audit domain. Auth callback access logging excluded; request JSON bounded to 32 KiB before credential work. No authentication AssetEvents.
- Minimal frontend GLOBAL/Tenant initiation, completion/failure, pending message, reload/expiry refresh and logout. Runtime branding/technical shell remains intact.

Additive [V4](../../backend/src/main/resources/db/migration/V4__phase_2_identity_authentication.sql) adds five tables: `local_global_credential`, `tenant_oidc_config`, `tenant_oidc_allowed_group`, `external_identity`, `global_admin_recovery_guard`; two composite User unique keys and scoped indexes. GLOBAL credential and TENANT identity composite FKs, deferred identity-to-User FK, canonical login uniqueness, exact issuer/subject PK, group UUID PK/FK, checks/version enforce structure. Core User stays minimal. **No new PostgreSQL triggers; V1–V3 are unchanged.**

Key services: `AuthenticationService`, `AdministratorRecoveryCommand`, `OidcConfigurationService`, `OidcLogin`, `OidcProvisioning`, `IServGroups`, `JwtTokens`, `SecretEnvelope`, `ProviderHttp`, explicit `Authorization` and `SecuredDomainOperations`. Spring Security is stateless; no default password/form-login/session route. Dedicated identity/auth administration controllers expose only the checked-in [OpenAPI contract](../api/openapi.yaml), 17 paths / 19 operations.

## Commands actually executed

All caches/output were repository-local. From `backend/`:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -DskipTests -DskipITs compile > ../.cache/phase2-compile.log 2>&1
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -DskipITs test > ../.cache/phase2-unit.log 2>&1
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify > ../.cache/phase2-verify.log 2>&1
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw --version > ../docs/verification/phase2/maven.txt
```

Full verification was repeated after actual compilation/integration failures and material fixes; final results below come from the passing final source run, not an earlier failed attempt. Initial unit-only execution is not PostgreSQL evidence.

From `frontend/`:

```sh
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test > ../.cache/phase2-frontend-test.log 2>&1
PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build > ../.cache/phase2-frontend-build.log 2>&1
```

From repository root:

```sh
sh -n scripts/smoke-phase2.sh
./scripts/smoke-phase2.sh > .cache/phase2-smoke.log 2>&1
/opt/homebrew/opt/node@24/bin/node --version > docs/verification/phase2/node.txt
docker version > docs/verification/phase2/docker-version.txt
docker image inspect kelios-backend:phase0 kelios-frontend:phase0 --format '{{.RepoTags}} {{.Id}} user={{.Config.User}}' > docs/verification/phase2/images.txt
```

The new script **actually invokes** unchanged `./scripts/smoke-phase0.sh`, building independent images with BuildKit, starting healthy Compose, checking non-root runtime/no Maven/compiler/Node tools, database migrations, technical routing and both-brand no-rebuild behavior. It then applies `deploy/compose.auth.yaml`, uses disposable local external TLS/crypto/password files, runs the deployment recovery command when needed, verifies repeated refusal, and runs Playwright over HTTPS:

```sh
# Inside the script, with repo-local browser/npm/temp caches and configured test password file:
(cd frontend && KELIOS_EXPECTED_BRAND=blue npm run test:smoke)
(cd frontend && KELIOS_EXPECTED_BRAND=green npm run test:smoke -- runtime-branding.spec.ts)
```

The successful first deployment command was:

```sh
docker compose -f deploy/compose.yaml -f deploy/compose.auth.yaml run --rm --no-deps backend \
  --spring.main.web-application-type=none --kelios.recovery.enabled=true \
  --kelios.recovery.login=phase2-smoke-admin \
  '--kelios.recovery.display-name=Phase 2 smoke administrator' \
  --kelios.recovery.password-file=/run/secrets/kelios-auth/password
```

The input was a random fixture file behind a host-only mode-0700 parent directory (only its readable child is mounted for the non-root runtime); no plaintext password was supplied as a CLI argument or placed in evidence. Every smoke rerun tested refusal with another login while a usable admin existed. Tests use disposable known fixture credentials internally, never production defaults.

Python checks parsed Surefire/Failsafe XML counts, OpenAPI YAML/reference consistency, local documentation links and SHA-256 preservation against the pre-Phase-2 workspace snapshot; `sh -n` checked the smoke script. Final logs were scanned before copying for raw JWTs, private-key blocks and password-hash output. Screenshots were visually inspected. These static/documentation checks are distinct from executed application verification.

## Results and test counts

| Executed check | Result |
|---|---|
| Maven Surefire | **68 passed**, zero failures/errors/skips: 34 generic state-machine + 2 technical endpoint + 16 policy + 16 JWT/crypto/browser-cookie unit cases. |
| Maven Failsafe / real PostgreSQL | **118 passed**, zero failures/errors/skips: 71 preserved Phase 1 cases + 47 Phase 2 identity integration cases. |
| **Total backend** | **186 passed**; full `verify` BUILD SUCCESS. PostgreSQL **17.11** via Testcontainers **2.0.5**, no H2 or silent Docker skip. |
| Frontend Vitest | **19 passed**: 11 retained branding + 8 authentication/session cases, including refresh without expired Access Bearer. |
| TypeScript/Vite production build | **Passed**. |
| Docker isolated builder | **68 unit tests passed** under explicit `-DskipITs verify`; independent of the required host PostgreSQL gate. |
| Actual HTTPS Chromium | **17 passed**: blue 11 (5 auth + 6 branding), green 6 branding; no skipped checks. |
| Container/Phase 0–1 deployment regression | **Passed**: healthy services, V1–V4 migration history, technical status/missing paths, management isolation, non-root/toolchain-free images and identical frontend image under both runtime brands. |
| Historical preservation | **28 pre-existing specification/task/Phase 0–1 plan/ADR/evidence/migration files unchanged** by content hash; accepted evidence not overwritten. |

Integration covers GLOBAL credential schema/scope/uniqueness, fresh V1–V4 plus populated V1–V3 upgrade, password/admin policy and audits, bootstrap/refusal/later recovery/atomic rollback, self-protection, current-User refresh/expiry/role/status/no password-revocation promise, four-role HTTP/service boundaries, same-Tenant/cross-Tenant/privilege denials, PENDING no-business-data, configuration ciphertext/version/sanitized API, JIT uniqueness/continuity/issuer/subject distinctions, actual HTTPS Discovery/code/PKCE/JWKS/UserInfo, state/nonce/callback Tenant/replay/expiry, wrong issuer/audience/expired ID token, unavailable provider/UserInfo, missing/malformed/outside/empty groups, subject mismatch, SSRF/TLS endpoint policy, throttling and body bounds. Unsupported merges and engine independence remain covered by the retained domain suite.

Independent PostgreSQL connections and controlled locks verify: two recovery commands create one admin; recovery waits for usability-increasing promotion and then refuses; old-hash local login waits for credential reset then fails; refresh waits for disable then fails; JIT waits for changed provider version then fails; simultaneous issuer/subject reservation yields one User/no orphan; Tenant approval waits for an ADMIN grant then denies activation; Tenant Catalog edit waits for publication then denies GLOBAL write. Selected waiting cases explicitly observe database lock waits, preserving normal READ COMMITTED and existing targeted locks.

Actual browser authentication cases verify real local HTTPS login, Secure/HttpOnly/path/SameSite cookies, no JavaScript-visible token cookie or local/sessionStorage, Bearer self endpoint, reload refresh, logout cookie removal, wrong/missing CSRF/Origin, disabled refresh and naturally valid issued Access. The Tenant initiation/completion/pending and safe error **browser contract cases use labeled HTTP fixtures**; provider wire behavior is separately verified by the real local HTTPS server in backend integration. No test-only provider shortcuts exist in production.

## Argon2id benchmark and environment

Selected supported encoder: salt **16 bytes**, hash **32 bytes**, memory **19,456 KiB**, iterations **2**, parallelism **1**. Five encode+verify measurements in the final host run: **median 45 ms, maximum 48 ms**, JVM max heap **512 MiB**, reported processors **2** (`-Xmx512m -XX:ActiveProcessorCount=2` for verification). Earlier concurrent-build runs measured up to 205 ms; variability is expected. Salt, verification/wrong-password, encoding and Unicode policy cases pass. This is a bounded local benchmark, not a production hardware/load guarantee; remeasure on deployment resources before raising cost.

Host: macOS ARM64, Java **25.0.2**, Maven Wrapper **3.9.16**, Node **24.15.0**, Spring Boot **4.1.1**, Boot-managed Spring Security **7.1.1**, Bouncy Castle **1.84**, Apache HttpClient **5.6.4**. See [toolchain](phase2/maven.txt), [Node](phase2/node.txt) and [Docker versions](phase2/docker-version.txt). Images retain local `:phase0` tags for regression compatibility; this tag does not restrict implemented phase. Current IDs/runtime users are in [image evidence](phase2/images.txt). The same frontend ID is verified under both brands.

## Failures encountered and resolution

- A first Maven invocation from repository root conflicted with its backend-relative local-cache configuration; sandbox prevented outside-repository writes. Subsequent builds ran from `backend/` with repository-local caches.
- Restricted initial dependency networking and Docker/local HTTPS socket access failed under the retained sandbox. Narrow command-specific approval enabled required dependency/Testcontainers/Docker/browser execution. No sudo, sandbox disabling or unrestricted filesystem request was used.
- Compile errors from Spring 7 response API usage, ambiguous wildcard imports/test exceptions, and a local metadata-cache variable collision were corrected before passing verification.
- First integration run exposed immutable-set null validation (`Set.of(...).contains(null)`); replaced with safe explicit membership validation. An old Phase 1 migration-count assertion was increased from three to four; the original domain constraints/assertions remain.
- Test HTTP helper rejected zero cookies; fixed optional-cookie setup. Schema-inspection assertion was scoped to `public` after an upgrade-test schema also existed.
- Review found refresh sent an old Access Bearer, which would prevent post-expiry refresh. Removed Bearer from cookie-only auth exchanges; meaningful frontend regression now verifies automatic refresh and fresh Bearer use.
- Review added bounded request bodies, scoped user-administration lookups/early permission checks, and standard OIDC/cached-key validation; appropriate final verification passed. No ignored acceptance failure remains.

## Evidence and remaining limits

[Full Maven log](phase2/maven-verify.txt), [frontend tests](phase2/frontend-test.txt), [frontend build](phase2/frontend-build.txt), [container build/regression](phase2/docker-smoke.txt), [HTTPS/browser log](phase2/https-browser-smoke.txt), [startup](phase2/backend-startup.txt), [migration rows](phase2/migrations.txt), [preservation](phase2/preservation.txt), [documentation checks](phase2/documentation-check.txt), [blue screenshot](phase2/blue.png), [green screenshot](phase2/green.png).

**Actual real IServ was not tested.** No credentials were requested; no environment was available. Official contract sources are recorded in plan §8 and ADR 0003. The production adapter and configuration paths are ready for configuration-only manual testing. Follow [PHASE_2_ISERV_MANUAL.md](PHASE_2_ISERV_MANUAL.md) to verify actual version/consent/scopes, Discovery/PKCE/client/redirect behavior, real UserInfo group UUID serialization/subject binding, positive/negative/JIT/approval/disable/continuity/D8 and sanitized evidence. Do not mark real compatibility passed from these fixtures.

Other unverified environments: production ingress/public CA/private-CA installations, deployment-scale brute-force/load and key-rotation/disaster-backup operations, multiple backend replicas and browsers other than local Chromium. Cross-origin/cross-site/subpath auth is explicitly unsupported. App throttles are bounded process-local and proxy-address based; production requires trusted ingress per-client/global controls. Local self-signed Playwright trust is test-only; production provider TLS validation stays enabled.

No server-side session/revocation database, Redis, automatic IServ synchronization or ADMIN mapping, guessed groups, local TENANT credentials, Phase 3 inventory/workflow/UI, commits or pushes were added. Accepted Phase 0/1 evidence and ADR history remain intact. The local green-brand HTTPS stack is left running; stopping Compose preserves PostgreSQL data.
