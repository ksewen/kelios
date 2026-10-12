# Step 6 — User administration backend verification

**Implementation complete; awaiting human acceptance.** M1 and Steps 2–5 are HUMAN ACCEPTED / CLOSED. This is the separately authorized backend-only Q2/M2/R1e User administration refactor. I1 Email migration is explicitly excluded. Tenant Step 7 and later modules remain unstarted.

## Query/detail/command contract

| Capability | Implemented behavior |
|---|---|
| Collection | `GET /api/v1/users` returns Page: items/page/size/totalElements. Default 0/20, backend sizes 1–100. Omission never returns an unlimited collection. Beyond-end is empty with current scoped metadata. |
| Text search | q searches displayName only, stripped literal case-insensitive substring; blank means no restriction; maximum 128 UTF-16 units after trimming. %, underscore and backslash are literal escaped input. No username/login/Email/Tenant-name/provider-claim search. |
| Structured filters | Exact scope, role, status and authorized GLOBAL ADMIN tenantId narrowing, combined in the same SQL query with text, sort and paging. |
| Sort | Closed displayName/scope/role/status fields, asc/desc; selectable field defaults asc. Default absent sort is createdAt DESC; direction without selectable sort is rejected. Existing ordinary UUID identity is used as an internal SQL tie, not a new Domain sort or mandatory product rule. |
| Detail | New `GET /api/v1/users/{id}`, independently authorized by persistent UUID; safe User fields, nullable Tenant ID/name and authoritative metadata. Detail does not depend on collection results/page state. |
| GLOBAL creation | Existing `POST /users/global` remains GLOBAL ADMIN only, login/displayName/password input, GLOBAL/USER/ACTIVE/null Tenant, existing 200 scalar response. Role grant is separate. No Email or validity-schema change. |
| Approval | Existing PENDING -> ACTIVE command, GLOBAL ADMIN any permitted target or own-Tenant ordinary USER for TENANT ADMIN. Existing locking/recovery guard/AuditLog and metadata behavior preserved. |
| Role | Existing GLOBAL ADMIN-only explicit role command, unchanged-role rejection and destructive-self protection. No arbitrary role/profile/status PATCH. |
| Disable | Existing explicit command and scope/self guards; no reactivation or deletion introduced. |
| Credentials | Existing GLOBAL-only administrator login/password reset and authenticated own-password flow remain; no local TENANT credentials, Email, new password policy or second credential lifecycle. |

## Authorization and non-disclosure

AuthenticationService is the application-service authority. ACTIVE GLOBAL ADMIN retains the established administrative population and may narrow by validated Tenant UUID. ACTIVE TENANT ADMIN always gets a fixed SQL predicate `scope=TENANT AND tenant_id=signedTenant AND role=USER`, applied before count, selection, ordering and pagination.

Supplied tenantId is removed from effective TENANT criteria without using/comparing it as authority, including another or nonexistent Tenant UUID. Requested scope/role/status only intersect the forced population; GLOBAL/ADMIN requests return an empty result, never broader authority. USER callers and PENDING/DISABLED callers are denied. Administrative target PENDING/DISABLED states remain visible/manageable according to existing rules; they do not grant the caller access.

UUID detail constrains the resource in SQL before projection. A foreign Tenant USER, own-Tenant ADMIN, GLOBAL User and absent UUID outside TENANT ADMIN authority all return the same existing 404 message/error representation. GLOBAL ADMIN authorized detail remains available. Minimal self-authentication/status endpoints are unchanged and are not substitutes for administrative detail.

No live actor/status reload or JWT revocation semantics were added: the accepted signed Access snapshot and Refresh reload behavior remain unchanged. DISABLED identities cannot obtain login tokens; their application-service reads are independently denied.

## Explicit boundaries and persistence

- `UserQuery`: closed application criteria, no HTTP annotations or arbitrary query DSL.
- `UserModel`: safe administrative application projection, separate from the authentication Identity principal and JPA User entity; includes lightweight Tenant and metadata values.
- `IdentityStore`: explicit private persistence Row -> UserModel mapping. A bounded joined read provides safe Tenant names; no credential/external-identity/provider joins or full association graphs. Bound WHERE parameters and enum-selected SQL identifiers produce count, full-result sort and LIMIT/OFFSET.
- `UserDtos`: explicit HTTP query/command/list/detail/state DTOs and mappings. Existing command outputs preserve their safe scalar identity shape; reads expose only id/scope/tenantId/role/status/displayName/Tenant/metadata. No entity or principal is returned directly from the changed administrative endpoints.
- Global create, role and credential DTOs use the existing strict command parsing helper and reject unsupported/server-owned fields rather than blindly persisting them. Password-bearing DTO string representations remain redacted.
- AuthProblems retains the existing identity ProblemDetail/status/no-store representation, also handling strict malformed-JSON/type-binding failures consistently. No business authorization or credential policy change.

All preexisting AuthenticationService bootstrap/login/refresh/create/approve/role/disable/reset/own-password bodies and IdentityStore load/managed/insert/touch/recovery-guard bodies remain byte-identical outside the replaced unbounded list/new read sections, proven by reconstructing the PRE-Step-6 source and matching its baseline hash. Their locks, metadata writers, real actor/time attribution, sanitized audit snapshots and operation transactions are preserved. Creation/update metadata is projected safely; human display names are not invented through live actor lookups. Reserved system attribution retains its existing stable projection.

Existing mutation + required AuditLog atomicity remains. New reads write no permanent audit records. A focused trigger-induced USER_CREATED audit failure proves User/credential/metadata creation rolls back; a confirmed disable rolled back by the enclosing transaction leaves ACTIVE state and no USER_DISABLED audit.

## API consequences and preservation

The intended API change is Users array -> bounded Page, supported query parameters, plus new UUID detail. No legacy array escape hatch or compatibility endpoint exists. Five administrative projection schemas are added: UserTenant, UserMetadata, UserItem, UserDetail and UserPage. Existing Identity/authentication/credential schemas remain unchanged and continue to describe unchanged safe scalar command responses.

Parsed OpenAPI comparison against PRE-Step-6 shows only `/users` changes and `/users/{id}` is added; every other existing path and schema is equal, including Tenant, provider, authentication, Catalog, Place, Asset and Audit contracts. YAML parses and all 209 local references resolve.

**No migration, schema, index, backfill, deployment or data reset.** M1 already supplies User metadata; its pre-M1 clean-state operational prerequisites remain separately gated. No speculative query index was added. Frontend integration of the Page/detail API is separately pending; frontend files/tests were not changed or run, no compatibility code was added and no deployment occurred. This backend task does not authorize I1 or claim Email design implemented.

## Verification

Maven 3 wrapper/Java 25 and fresh isolated PostgreSQL 17 Testcontainers only. No tests targeted the manual environment.

New UserAdministrationIT cases cover defaults/minimum/approved selector sizes/maximum, empty/beyond-end scoped counts, chronological default, all four full-result server sorts in both directions across more than three pages with duplicate primary values, combined predicates, literal search/bounds, strict unsupported parameters, forged Tenant/scope/role filtering, independent foreign/absent/elevated detail, role/status security, safe metadata/association projections, read-only audit behavior, metadata overposting, existing commands/self guards, creation preservation/update attribution and rollback.

The sole changed preexisting test assertion uses the new bounded typed User query/model instead of the removed unbounded API while retaining its same-Tenant ordinary-USER authorization assertion. Existing identity/security/JIT/bootstrap/credential/concurrency suites remain executed.

Initial focused run: 74 unit cases passed; 47 new integration cases, 44 passed/1 failure/2 errors, 0 skips. Fixture errors were: expecting 404 rather than actual 405 for unsupported User DELETE; trying to mint DISABLED login tokens. Corrected to assert unsupported DELETE and both token-issuance denial/application-service denial. No production behavior was altered for those failures. A subsequent full run passed 74 unit + 532 integration cases. Final explicit persistence-row mapping and an additional audit-failure rollback case were then verified by the final full suite.

| Final command/check | Result |
|---|---|
| `./mvnw -B verify` | **BUILD SUCCESS**, 01:15 min |
| Unit tests | **74 passed**, 0 failures/errors/skips |
| PostgreSQL integration tests | **533 passed**, 0 failures/errors/skips |
| Total | **607 passed**, including **48 UserAdministrationIT cases** |
| OpenAPI YAML/all 209 local references | Passed |
| Other endpoint/schema preservation | Passed |
| PRE-Step-6 source hashes/delta and documentation links | Passed |
| `git diff --check` and new-file whitespace checks | Passed |

Integration matrix: UserAdministrationIT 48, AssetResourceIT 50, CatalogLabelIT 62, ProductIT 41, PlaceIT 65, Phase3InventoryIT 97, Phase4MetadataIT 23, IdentityAuthenticationIT 49, DomainFoundationIT 72, M1BusinessMetadataIT 26. No excluded/unexecuted tests are counted.

Reproducible command from backend/: `MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify`. No frontend suite was run.

## Exact Step 6 delta

Modified relative to PRE-Step-6:

1. `backend/src/main/java/com/github/ksewen/kelios/identity/auth/AuthenticationService.java`
2. `backend/src/main/java/com/github/ksewen/kelios/identity/auth/IdentityStore.java`
3. `backend/src/main/java/com/github/ksewen/kelios/platform/security/IdentityAdminController.java`
4. `backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthProblems.java`
5. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
6. `docs/api/openapi.yaml`
7. `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`

Added:

8. `backend/src/main/java/com/github/ksewen/kelios/identity/UserQuery.java`
9. `backend/src/main/java/com/github/ksewen/kelios/identity/UserModel.java`
10. `backend/src/main/java/com/github/ksewen/kelios/platform/security/UserDtos.java`
11. `backend/src/test/java/com/github/ksewen/kelios/identity/auth/UserAdministrationIT.java`
12. `docs/verification/STEP_6_USER_ADMINISTRATION.md`

## Limitations and review checkpoint

No known remaining Step-6 backend defect. The approved future Email/validity/12-character target remains deliberately unimplemented: existing login-name and 15–128-character password policy/Argon2id remain. I1 requires explicit subsequent authorization, not implicit progression from User query completion. Tenant, provider, Audit, frontend details/pagers, real IServ interoperability, export and other deferred lifecycle work remain separately gated.

No manual business-data/runtime verification or performance/index claim. Read transactions retain existing READ COMMITTED behavior, without a new cross-request collection snapshot guarantee. Future performance work requires isolated representative SQL evidence.

PRE-Step-6 hashes, exact delta, Git status and Maven/OpenAPI checks are retained under ignored `.cache/step6/`. All accepted Phase A/B and Steps 1–5 work remains preserved. No staging, commit, push, PR, deployment, database reset or persistent business test data. **STOP for human review before I1 or Step 7.**
