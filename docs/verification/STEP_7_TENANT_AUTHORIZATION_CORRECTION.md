# Step 7 — Tenant administration authorization correction

Status: backend implementation and focused/full verification complete, pending human review. Revised administration documentation is accepted/closed. Step 8 remains reopened and unauthorized for implementation. Prior [Step 7 evidence](STEP_7_TENANT.md) is unchanged historical evidence of the previous authorization model.

## Exact behavior and production delta

Only TenantOperations production code changed, in two policy expressions:

- Detail reader uses Authorization.administrator instead of active-only validation.
- Collection uses CurrentIdentity + Authorization.globalAdmin before reads.list(null), rather than all active readers with optional own-Tenant filtering.

| Principal | GET /tenants | GET /tenants/{id} | POST /tenants |
|---|---|---|---|
| ACTIVE GLOBAL ADMIN | Complete authorized unpaged list | Authorized Tenant UUID | Existing create |
| ACTIVE TENANT ADMIN | 403 | Own signed Tenant only; foreign/absent 404 | 403 |
| ACTIVE GLOBAL/TENANT USER | 403 | 403, no resource projection | 403 |
| PENDING/DISABLED | Denied | Denied | Denied |

Detail retains independently scoped SQL: caller UUID cannot widen signed TENANT authority; foreign and absent share existing missing-resource behavior. Knowing a UUID or supplying tenantId as query context grants nothing. No own-Tenant collection fallback or switching/discovery alias was added. Malformed/unsupported input retains existing validation behavior; authorization protects valid resource operations. Minimal authentication/self interactions and approved business reads are unchanged.

Unpaged createdAt DESC list, explicit transport/application/persistence projections, ID/name/safe metadata, creation normalization/UUID response, authoritative actor/time ownership and atomic TENANT_CREATED audit remain unchanged. No Tenant edit/delete or provider changes. Reversing those two policy expressions reconstructs the exact PRE-correction TenantOperations hash, proving creation/scope/transaction definitions otherwise identical. TenantReads/Model/DTO/Service and shared Authorization/internal Tenant helpers are byte-identical. This remained primarily an administrative service-policy correction; no controller production change was necessary.

OpenAPI Tenant list/detail authorization descriptions now match the revised policy. Paths/projections/create response/schema remain unchanged; provider OpenAPI was not changed. Plan/authentication status records completion pending review without falsely closing Step 8 or editing historical evidence.

## Tests and verification

TenantResourceIT changes only obsolete role/list expectations and adds eight HTTP matrix cases. Existing 12-case direct-service matrix verifies ACTIVE roles plus PENDING/DISABLED. Own-detail safe projection/read-no-audit, GA unpaged list, foreign/absent/forged context, creation/overposting/server metadata/audit rollback and undefined mutation checks remain active. User creation denial also proves no inserted Tenant.

The first full run exposed an additional old assertion in IdentityAuthenticationIT.administrationHttpMatrixAndAuthoritativeTargets expecting Tenant list 200 for all active roles. It now checks GA-only list and ADMIN-only detail; original creation and provider assertions are retained. No Step 8 authorization behavior or I1 logic changed, and no test was disabled/weakened.

Commands from backend, using isolated PostgreSQL17 Testcontainers:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B test-compile failsafe:integration-test failsafe:verify -Dit.test=TenantResourceIT
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

| Check | Result |
|---|---|
| Focused TenantResourceIT | 43 passed; 0 failures/errors/skips; BUILD SUCCESS; 12.368s |
| Full unit/Surefire | 74 passed; 0 failures/errors/skips; 5 suites |
| Full PostgreSQL/integration Failsafe | 669 passed; 0 failures/errors/skips; 13 suites |
| Full total | 743 passed; 0 failures/errors/skips |
| Maven3/Java25 compile/package/verify | BUILD SUCCESS; 1m28s |
| OpenAPI YAML/internal reference checks | 218 resolved; Tenant descriptions corrected only |
| git diff --check |PASS |

Focused 43 repeat inside full results and are not added to the distinct full total. Logs/XML remain ignored at .cache/step7-correction/focused.log, verify.log, verify-first.log and backend target test reports. All accepted business/Email/OIDC/User/Audit suites ran. No automated tests used the manual environment.

## Exact delta relative to PRE-correction worktree

- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantOperations.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/TenantResourceIT.java`
- `docs/AUTHENTICATION.md`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/STEP_7_TENANT_AUTHORIZATION_CORRECTION.md`

## Preservation and limitations

PRE-correction hashes confirm frontend, all migrations/schema, controllers/DTO/projections, Tenant writer, business authorization helpers, identity/Email/OIDC/provider/Audit implementation and historical verification reports unchanged. Existing dirty/untracked approved work is preserved; comparison is not against HEAD.

Frontend sidebar/direct own-detail navigation and obsolete Tenant-selector coupling remain separate backlog, not restored through backend compatibility access. No frontend suites/manual UI claim is made. Step 8 provider authority/DELETE/freshness remains separately pending; this green regression run does not claim that reopened contract is implemented. There are no known remaining backend gaps in this Tenant policy correction; human acceptance remains pending.

No frontend, Step 8, Tenant switching/edit/delete, deployment/runtime, migration/schema, development-data reset or persistent business-data creation occurred. No staging/commit/push/PR or unrelated cleanup. STOP for human review before any later implementation.
