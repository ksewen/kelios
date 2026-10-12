# I1 — GLOBAL Email identity and credentials

Status: backend implementation verified; **pending human review**. Step 6 User administration is HUMAN ACCEPTED / CLOSED. No Step 7, frontend integration or real Email delivery was started.

## Scope and schema

The existing GLOBAL credential lifecycle now uses Email exclusively. `local_global_credential` retains `user_id` (PK), `user_scope='GLOBAL'`, composite FK `(user_id,user_scope)` to `app_user`, and `password_hash`. Additive V8 drops `login_name`, adds non-null `email` and nullable `email_verified_at` (`timestamptz`). Null verification is a real unverified validity state, not a compatibility concession. A unique `lower(email)` index is the case-insensitive account-identity integrity constraint. The SQL shape constraint requires canonical lowercase/trimmed Email length 3–254 with `@`; complete practical syntax validation is application-owned. User scope/Tenant/role/status/displayName and M1 actor/time fields remain unchanged. No universal User Email or TENANT username is introduced.

Email is outer-whitespace stripped and lowercased with Locale.ROOT. The practical ASCII contract accepts dot-atom local parts up to 64 characters and dotted DNS labels up to 63 characters, at most 254 overall. Invalid/non-string/missing Email fails the normal identity validation contract. Quoted mailboxes, internationalized mailbox/domain input and local-only domains are intentionally outside this implementation. No provider-specific alias rewriting or claimed deliverability validation occurs.

V8 refuses any populated legacy credential table **before** changing it. PostgreSQL/Flyway rolls back the refused migration; the legacy identifier and schema remain intact. Clean isolated databases migrate through V8 successfully. No historical Email/verification backfill, invented provenance, dual schema/lookup, compatibility columns or data deletion is present. V1–V7 source hashes are unchanged.

**Operational gate:** an environment containing legacy local credentials cannot apply V8 directly. Separately authorize clearing/resetting disposable development credential/data state and use fresh Email bootstrap. No such operation was performed, no manual database was queried or modified, and no deployment was attempted. Existing M1 cutover prerequisites remain applicable independently.

## Authentication and commands

| Operation | I1 behavior |
|---|---|
| Local login | POST `/api/v1/auth/local/login`, strict `{email,password}`; case-insensitive lookup, password verification and locked current Email/hash recheck before issuance |
| Bootstrap/recovery | Same non-web/private password-file command; identifier property is now `kelios.recovery.email`; guarded zero-usable-GLOBAL-ADMIN transaction; real User/credential/metadata/audit created atomically |
| Admin creation | POST `/users/global`, strict `{email,displayName,password}`; GLOBAL/USER/ACTIVE, null Tenant, verified at actual authoritative Clock time; ADMIN grant remains separate |
| Email edit | PUT `/users/{id}/email`, strict `{email}`, GLOBAL ADMIN and GLOBAL target only; password hash unchanged, Email manually verified, User updated metadata and sanitized `GLOBAL_EMAIL_CHANGED` audit atomic |
| Admin reset | Existing PUT `/users/{id}/local-credential`, strict `{email,password}`; same credential table and guarded reset transaction, manual verification time, metadata and audit |
| Own password | Existing current-password-verified PUT `/auth/local/password`; strict input, same hash update/CAS/metadata/audit; no independent JWT revocation |
| User detail | Existing independently authorized UUID read, safe `globalAccount:{email,emailVerifiedAt}` for GLOBAL; null for TENANT or missing account; list projection stays concise and does not expose account data |

All paths enforce authoritative service scope. No caller can assign verification, role/status, Tenant ownership or server-owned metadata through credential/create/Email DTOs. Retired `login` input is rejected with 400; there is no alias authentication. Secrets, hashes, tokens, raw external identity and claims are not added to User projections. Audit actor remains stable UUID with existing displayName snapshot; mutable Email is neither actor key nor audit payload/snapshot requirement.

## Validity and password policy

GLOBAL login/Refresh require an existing usable credential, `email_verified_at != null` and ACTIVE. Existing status semantics encode approval/active state: PENDING is not approved; DISABLED is not active. No parallel approval/status lifecycle is created. Manual bootstrap/create/reset/edit stamps actual UTC Clock verification time; approval alone never fabricates verification. Usable-admin recovery checks include valid canonical Email, verification, ACTIVE/ADMIN/GLOBAL and supported hash. Access snapshots continue to expire naturally; no revocation subsystem is added.

TENANT OIDC retains exact issuer/subject mapping, signed Tenant scope, JIT USER/PENDING, group rules and existing minimal PENDING authentication. No local TENANT credential path is introduced. Provider routing/claims, UUID detail isolation, self-disable/demotion guards and forced TENANT ADMIN ordinary-user population remain unchanged.

Every password writer enforces 12–128 Unicode code points with uppercase, lowercase, digit and non-alphanumeric character; passwords remain exact/untrimmed. Argon2id remains 19,456 KiB/t2/p1, 16-byte salt, 32-byte hash. Hashing and bounded dummy verification remain the existing implementation, not a second credential system.

## Transactions and concurrency

Creation/bootstrap/reset/Email edit/own password preserve existing transaction ownership and transaction-local M1 metadata/AuditLog. Creation metadata is preserved; edit/reset changes only update metadata. Email uniqueness collision or audit failure rolls back credential/verification/metadata and leaves no audit for a failed operation.

Email-only editing retains the current password, so login now rechecks both Email and hash after acquiring the User lock. A login that saw old Email before a concurrent edit cannot issue tokens after the edit commits. Existing credential-reset/hash and recovery/role/approval locking protections remain intact; no broad concurrency redesign.

## Verification

Final command, run from `backend` using repository-local Maven cache and temporary directory:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

| Check | Result |
|---|---|
| Maven3 / Java25 compile/package/verify | BUILD SUCCESS, 1m28s |
| Surefire unit tests | 74 passed; 0 failures, 0 errors, 0 skipped; 5 suites |
| Failsafe PostgreSQL/integration tests | 546 passed; 0 failures, 0 errors, 0 skipped; 10 suites |
| Total | 620 passed; 0 failures/errors/skips |
| OpenAPI YAML + internal `$ref` traversal | 215 references resolved; strict Email-only request shapes and detail-only account projection checked |
| `git diff --check` |PASS |
| PRE-I1 hash comparison |No frontend, deployment/scripts, historical migrations or unrelated production files changed |

All integration writes use isolated PostgreSQL17 Testcontainers, never the manual environment. Relevant suites: AuthPolicyTest (16), IdentityAuthenticationIT (62), UserAdministrationIT (48), M1BusinessMetadataIT (26); all accepted Catalog/Place/Product/Asset/history/authorization suites also ran. New tests cover canonical/case-insensitive create/edit collision, safe account detail, six verified/status login+Refresh combinations, approval versus verification, reset/manual verification, unverified-admin recovery, admin/Tenant/overposting boundaries, retired login rejection, Email edit audit rollback, legacy migration refusal and old-Email login/edit lock race. Existing bootstrap concurrency, reset/login race, self protection, OIDC/security, actor metadata and history tests remain active.

Initial diagnostic runs exposed an Instant/JDBC binding error and historical migration fixtures still copying the retired column/expecting seven migrations. These were corrected using the existing UTC Timestamp binding convention and explicit test-only historical fixture construction; migration-history preservation assertions were retained. No test was disabled or weakened. Final per-suite XML totals and log are in ignored `.cache/i1/verify.log` and `backend/target/{surefire,failsafe}-reports`; earlier diagnostic logs remain local only. Evidence contains no passwords/tokens/keys.

## Exact I1 delta (relative to PRE-I1 worktree)

- `backend/src/main/java/com/github/ksewen/kelios/identity/UserModel.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/AdministratorRecoveryCommand.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/AuthenticationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/Passwords.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/IdentityAdminController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/UserDtos.java`
- `backend/src/main/resources/db/migration/V8__global_email_identity.sql`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/AuthPolicyTest.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/M1BusinessMetadataIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase3InventoryIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase4MetadataIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/UserAdministrationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/inventory/DomainFoundationIT.java`
- `docs/AUTHENTICATION.md`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/I1_GLOBAL_EMAIL_IDENTITY.md`

## Limitations and stop

Frontend and deployment CLI remain untouched: their old login/create/reset payloads/prompts require separate integration approval. Do not deploy mismatched callers. Backend recovery now requires Email property; no login-property alias was added. Real Email delivery, token verification, Email reset/self-registration and internationalized/quoted mailbox support are not implemented. No manual authentication/business-data or real IServ interoperability claim is made.

No commit/stage/push/PR, environment change, deployment, database reset or persistent business-data creation occurred. Existing dirty/untracked approved work is preserved. I1 is ready for human review, not automatically accepted; STOP before Tenant Step 7 and all later modules.
