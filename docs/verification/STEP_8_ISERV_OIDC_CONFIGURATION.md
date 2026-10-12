# Step 8 — Tenant IServ/OIDC configuration

Status: backend implementation and verification complete, **pending human review**. Step 7 Tenant is HUMAN ACCEPTED / CLOSED. Applicable R1g work only; no Audit or later implementation.

## Exact resource behavior

- Existing GET/PUT `/api/v1/tenants/{id}/oidc` remain the Tenant-owned configuration singleton, GLOBAL ADMIN only. Other roles/scopes and PENDING/DISABLED are denied in the service before resource lookup. UUID/provider selection is never membership or authority.
- GET independently reads a safe application projection and explicit HTTP DTO, including persistent Tenant ID + readable name and M1 metadata. The public shape retains tenantId/issuer/clientId/secretConfigured/enabled/version/allowedGroupIds/callbackUri/metadata and adds tenant `{id,name}`. Missing configured resource/unknown Tenant remains the existing 404 behavior. Reads create no permanent audit/event.
- Administrative SQL explicitly selects safe fields and joins Tenant name without selecting ciphertext. Allowed group IDs use the existing bounded child-table read, not per-association frontend lookup. Application View/Tenant/Metadata records are independent of HTTP/persistence; ConfigurationRow and transport Detail/Metadata/Tenant mappings are explicit. The runtime secret-bearing Provider is never an API response.
- PUT uses explicit strict Configure DTO: issuer/clientId/clientSecret/enabled/version/allowedGroupIds only. Unknown/server-owned inputs including actorId, Tenant ownership, metadata, callback/secret status and identity claims are rejected. Enabled must be an explicit boolean, version null/nonnegative integral, group array bounded to 256 UUID strings. Configure.toString is redacted. Issuer/client/secret validation remains the existing service validation.
- Initial configure still requires an existing Tenant, null/omitted version and a client secret; version starts 0. Existing configuration requires its current version, increments on success and rejects obsolete state 409. Null/omitted secret retains/decrypts/re-encrypts through existing envelope context; supplying secret replaces it. Empty allowlist/disabled provider remains allowed configuration that blocks provider login, not User lifecycle modification.
- Parent/configuration locking, serialized first insert, optimistic versions, M1 actor/time ownership, creation preservation and atomic settings/secret/groups + OIDC_CONFIG_CHANGED AuditLog are unchanged. No duplicate AuditLog or AssetEvent was added.
- No configuration collection/pagination, DELETE/reset/removal, Tenant username, Email delivery, arbitrary identity/status command or schema change was added. Existing enabled setting is not deletion/reset.

## Preservation proof

PRE-Step 8 hashes capture existing dirty/untracked work, not just HEAD. Reversing only administrative projection edits restores the exact OidcConfigurationService baseline hash. Consequently organizations routing, provider/locked/secret reads, encryption, validation, parent/version locks and the writer/audit transaction are byte-identical apart from mapping the mutation result. Reversing only provider DTO edits restores IdentityAdminController's exact baseline, proving its User/Email/Tenant handlers unchanged.

OidcLogin/OidcProvisioning, exact issuer+subject mapping, JIT USER/PENDING, group eligibility, signed Tenant authority, JWT/refresh/access snapshot semantics and GLOBAL Email credential implementation are unchanged. The anonymous organizations directory remains authentication routing, not an administrative provider collection; its existing behavior was not modified.

## Verification

Full command from backend with repository-local cache/temp directory:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

| Check | Result |
|---|---|
| Maven3/Java25 compile/package/verify | BUILD SUCCESS; 1m32s |
| Surefire | 74 passed; 0 failures/errors/skips; 5 suites |
| PostgreSQL/integration Failsafe | 618 passed; 0 failures/errors/skips; 12 suites |
| Total | 692 passed; 0 failures/errors/skips |
| OidcConfigurationResourceIT | 37 passed |
| OpenAPI YAML/internal references | 218 resolved; strict configure and required safe Tenant projection checked |
| git diff --check |PASS |

New tests cover independent singleton/missing/unauthenticated reads, safe exact DTO shape, readable Tenant, secret/ciphertext non-exposure, 12 scope/role/status service combinations, actor/metadata/ownership overposting, malformed boolean/version/group input, first-secret requirement, creation metadata preservation, secret retention/re-encryption across context change, disabled runtime provider, stale version, settings/secret/groups/metadata rollback on audit failure for both initial/update, concurrent first configure with one winner, and retained no-delete/role guards. Existing full OIDC TLS protocol/JIT/claims/group/version/race tests and accepted resource/Email/Tenant suites also ran.

The first diagnostic full run had one failure: an existing provider update test deliberately submitted actorId and expected it to be silently ignored. Strict DTO binding correctly rejects that obsolete input. The test now asserts 400 and unchanged version, then sends the valid body and retains all original successful update/authorization/version/sanitization assertions. actorId is also included in the new overposting matrix. No test was disabled or weakened. Final result is green. Diagnostic/final logs stay ignored in `.cache/step8/verify-first.log` and `.cache/step8/verify.log`; fresh isolated PostgreSQL17 Testcontainers never target the manual environment.

## Exact Step 8 delta

- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/IdentityAdminController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/OidcConfigurationDtos.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationResourceIT.java`
- `docs/AUTHENTICATION.md`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/STEP_8_ISERV_OIDC_CONFIGURATION.md`

## Limitations and stop

Frontend remains unchanged and separately gated; no frontend suites or compatibility work were added. No deployment/manual runtime configuration was attempted. Tests prove isolated configuration and synthetic HTTPS/OIDC fixture behavior, not real IServ interoperability; actual provider interoperability remains manual/separate. No production/manual business data or configuration was created, mutated or reset. Existing M1/I1 deployment/cutover prerequisites remain unchanged.

All unrelated accepted files and migrations remain preserved. No migration/index/schema change, data reset, staging/commit/push/PR or unrelated cleanup occurred. There are no known remaining backend gaps in the approved Step 8 singleton surface; undefined DELETE/reset and future frontend/provider acceptance are not implemented by inference.

STOP for human review before Audit Step 9 or any later implementation.
