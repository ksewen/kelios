# Step 8 — IServ/OIDC authorization and physical DELETE correction

Status: backend implementation and focused/full verification complete, pending human review. Current contract has no configuration-generation identity. [Original Step 8 evidence](STEP_8_ISERV_OIDC_CONFIGURATION.md) remains unchanged historical evidence, not proof of the revised scope/lifecycle.

## Authorization and API

Existing GET/PUT /api/v1/tenants/{id}/oidc now allow ACTIVE GLOBAL ADMIN for an authorized concrete Tenant and ACTIVE TENANT ADMIN for its signed own Tenant. USER/PENDING/DISABLED remain denied. administrativeTenant validates ADMIN and derives TA scope solely from signed identity, then intersects the locator with persisted Tenant scope. Foreign TA locators return 404 and cannot widen scope or create/update/delete a foreign singleton. No Tenant collection/switching or public provider management is introduced.

New DELETE /api/v1/tenants/{id}/oidc?version=<current-version> uses the same service scope, current nonnegative int64 version (strict query binding, no other query fields), and parent/configuration locks. Missing/invalid version 400; stale version 409. Under lock, it removes owned allowed-group rows first, then the current configuration row including encrypted secret, and records exactly one OIDC_CONFIG_DELETED AuditLog atomically. Target Tenant/UUID comes from loaded configuration ownership; actor UUID/displayName comes from authenticated identity. Payload contains only version/enabled, never secret/ciphertext/claims. Success 200 has no body. Repeated DELETE or absent configuration 404 creates no second successful audit. Audit failure restores all removed rows/material. No AuditService/AuditLog schema/writer changes.

GET after deletion is missing. Disabled configuration remains stored/readable/editable with retained secret and singleton slot, but cannot authenticate; it can also be physically deleted. Recreation follows existing initial PUT: null/omitted version and explicitly supplied secret; no implicit reuse of removed material. Existing PUT retains version 0 initial/version increment, stale 409, null-secret retention/re-encryption on updates, enabled/group validation, strict overposting protection, metadata preservation and atomic OIDC_CONFIG_CHANGED. A non-initial version supplied with no current config retains existing PUT version-conflict behavior; no mutation occurs. No reset/restore, soft delete/history, generation UUID, freshness discriminator, schema/index/migration, or compatibility machinery.

Deletion preserves Tenant, provisioned Users and exact issuer/subject mappings. JWT/access/refresh semantics remain unchanged; deletion does not disable Users or revoke tokens. Public routing directory automatically excludes missing/disabled configuration through its existing join/enabled condition.

## Current configuration and concurrency

OidcLogin.callback still reloads the current enabled configuration before using current credentials, verifies existing Tenant/version/issuer/client/redirect, protocol/state/nonce/PKCE/ID-token/UserInfo/group constraints, and forwards the verified flow clientId to final provisioning. OidcProvisioning.complete now also checks current clientId under lock, closing a current-configuration mismatch if client changes between callback and finalization; issuer/subject/JIT/User status rules remain unchanged. Trusted internal test callers now provide that clientId; no public identity/provisioning endpoint or compatibility overload was added.

configs.locked now acquires the same parent-Tenant lock first, then configuration FOR SHARE. PUT/DELETE likewise acquire parent then configuration write lock. Final provisioning and mutations serialize without network-long transactions or opposite lock order. If final provisioning commits first, deletion waits and existing tokens remain valid. If deletion commits first, finalization re-reads missing current configuration and fails before User/mapping/audit insertion. Current disabled/mismatched settings also fail. A valid recreated current configuration may accept a flow begun before deletion: tested through the HTTPS callback fixture, including a previously warmed cache. No rejection rule is based on deletion history.

Physical removal fits the existing Tenant-PK/FK schema; allowed groups are the owned child rows, external identities reference Tenant/User rather than configuration. No migration or historical-data compatibility/reset is required or performed. Only short final transaction lock sequencing was changed, not general resource/authentication locking.

## Verification

Commands from backend with repository-local Maven cache/temp directory:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B test-compile failsafe:integration-test failsafe:verify -Dit.test=OidcConfigurationResourceIT,IdentityAuthenticationIT
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

| Check | Final result |
|---|---|
| Focused suites | 120 passed; 0 failures/errors/skips; BUILD SUCCESS; 31.381s |
| Focused configuration resource | 56 passed |
| Focused identity/callback/security | 64 passed |
| Full Surefire unit | 74 passed; 0 failures/errors/skips; 5 suites |
| Full PostgreSQL/integration Failsafe | 690 passed; 0 failures/errors/skips; 13 suites |
| Full distinct total | 764 passed; 0 failures/errors/skips |
| Maven3/Java25 compile/package/verify | BUILD SUCCESS; 1m32s |
| OpenAPI YAML/internal reference checks | 219 resolved; scoped GET/PUT and versioned physical DELETE documented |
| git diff --check |PASS |

Focused tests repeat within full totals, not additional distinct tests. Earlier focused 116 passed before four additional mutation-race cases; final focused 120 covers the exact final sources. Logs are ignored at .cache/step8-correction/focused.log, focused-final.log and verify.log. All writes use fresh isolated PostgreSQL17 Testcontainers, never operator data. No tests excluded/disabled/weakened.

Coverage includes 12-case service status/scope matrix plus HTTP create/read/delete role matrix; foreign UUID/context mutation denial; existing safe Tenant/secret projection and PUT validation/metadata/encryption; exact physical removal/audit attribution; stale/malformed versions/repeated 404; disabled versus deleted; recreation/fresh-secret requirement; rollback restoring encrypted material/groups; concurrent DELETE one success/one audit; update-delete version races; recreate waiting for delete; both ordering outcomes of delete/final provisioning with actual PostgreSQL lock-wait proof; final client mismatch rejection; missing/current-mismatched callback failure; legitimate pre-delete flow completion after recreation; Users/mappings/active Refresh/access survival. Existing issuer/subject/JIT/group/egress/Email/Tenant/Asset/history/Audit regressions also ran.

Old GA-only/provider-no-delete assertions were updated to the revised policy. The old stale TA update now expects 409 (authorized but stale), preserving the version guard; strict actor overposting and all unchanged protocol checks remain. M1BusinessMetadataIT/Phase3InventoryIT changes only supply clientId to the internal verified completion contract.

## Exact delta relative to PRE-Step 8-correction worktree

- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcLogin.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcProvisioning.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/IdentityAdminController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/OidcConfigurationDtos.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/M1BusinessMetadataIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationResourceIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase3InventoryIT.java`
- `docs/AUTHENTICATION.md`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/STEP_8_OIDC_AUTHORIZATION_DELETE_CORRECTION.md`

## Preservation, limitations and stop

Hash comparison confirms frontend, deployment, schema/migration sources, business modules, Tenant correction, GLOBAL Email and Audit writers/reads unchanged. Removing only the new DELETE handler reconstructs the shared controller baseline, proving existing User/Tenant/GET/PUT handlers unchanged. Historical verification evidence and all prior dirty/untracked approved files remain preserved.

Frontend configuration/delete UI and any caller integration remain separately gated; no frontend suites/runtime manual claims. HTTPS fixture verifies protocol behavior, not real IServ interoperability. No operator configuration/business data or runtime/deployment was modified. No database reset, staging/commit/push/PR or unrelated cleanup. There is no known remaining backend gap in the requested correction established by these checks. No later module/Phase C/D/export/Email delivery was started. STOP for human review.
