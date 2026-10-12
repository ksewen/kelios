# Tenant GLOBAL USER read-authority correction

Date: 2026-10-12. **HUMAN ACCEPTED / CLOSED, acceptance date 2026-10-12.** U1 is HUMAN RESOLVED / CLOSED; F1b/D05 is dependency-ready with respect to U1 but NOT AUTHORIZED/not implemented. New correction follows accepted baseline `939c7373552d676822d0f9d0fe92798fa8618100`; historical Step7/F0/F1a reports unchanged.

## Implementation and matrix

Only production file changed: `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantOperations.java`. Resource-local reader requires ACTIVE; TENANT reader additionally requires ADMIN. Collection additionally requires GLOBAL. No change to shared Authorization.administrator/globalAdmin, TenantReads, controllers, DTOs, writers, transactions, schema or query.

| Principal | Collection | Detail | Create |
|---|---|---|---|
| ACTIVE GLOBAL ADMIN | allowed | authorized UUID | unchanged allowed |
| ACTIVE GLOBAL USER | allowed | authorized UUID |403 |
| ACTIVE TENANT ADMIN |403 | own signed Tenant; foreign/absent same404 |403 |
| ACTIVE TENANT USER |403 |403 |403 |
| PENDING/DISABLED | denied | denied | denied |
| Anonymous |401 |401 | unchanged denied |

Unknown Tenant edit/PATCH/delete remain405. Full GET projection unchanged: id/name/metadata(createdBy/createdAt/updatedBy/updatedAt/createdByDisplayName/updatedByDisplayName); no additional security fields, no live actor lookup or read audit. Existing scoped UUID locator remains non-authoritative. OIDC/User/Audit guards unchanged; new GLOBAL USER HTTP tests explicitly check their list/detail denial.

## Tests and commands

Changed `backend/src/test/java/com/github/ksewen/kelios/identity/auth/TenantResourceIT.java`: revised GU success rows/service assertions; new exact full projection parity, collection/detail no-audit, denied create/undefined mutations and unrelated admin GET denial; pending service/HTTP and disabled service/token-issuance denial; anonymous reads. Existing TA foreign/absent, overposting and create/audit rollback tests retained.

Focused command, from backend:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B -Dtest=TenantResourceIT -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=TenantResourceIT verify
```

Final result: **45 distinct cases, zero failures/errors/skips**. Command executes same45 in Surefire and Failsafe (90 executions, not90 distinct cases); BUILD SUCCESS. Log `.cache/tenant-read-correction/focused.log`. Initial attempt failed Maven cache sandbox access before tests. First runnable attempt had45 cases/0 failures/1 error because fixture tried issuing DISABLED token, correctly rejected before HTTP; corrected to assert issuance/service denial, with PENDING still HTTP tested. A premature rerun used the old fixture and failed identically; final corrected run passed. No assertions weakened to allow disabled access.

Broader command, from backend:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

First broader run:74 unit +701 PostgreSQL integration executed; unit suite passed, integration had1 failure/0 errors/0 skips due to stale GU Tenant403 expectation in IdentityAuthenticationIT.administrationHttpMatrixAndAuthoritativeTargets. Revised only its two Tenant GET assertions; create/OIDC assertions unchanged. Final rerun result below. Isolated PostgreSQL/Testcontainers only; no manual database credentials or operator data used. `IdentityAuthenticationIT.java` is the second changed test file. OpenAPI YAML parse/expected route checks passed. OpenAPI change consists solely of Tenant GET authorization descriptions; no new endpoint/response schema.

## Documentation and preservation

Synchronized ARCHITECTURE.md, DOMAIN.md, docs/AUTHENTICATION.md, backend collection plan, frontend integration plan, U1/U2 decision preparation and read-authority audit. U1 closed; complete existing projection disclosure approved, mutation/read separated. Previous audit guard observations marked historical; no historical verification files rewritten. No B1/D29 or B2/D28 change. No F1b implementation, schema/data reset, frontend edit, deployment, commit/stage/push/PR.

Exact changed files, final verification totals and git status are recorded in the final section. Static relative-link/whitespace, git diff --check and pre-gate hash preservation checks required. Live backend/operator verification not performed; isolated tests provide backend evidence, not frontend/live interoperability.

## Final verification and exact delta

Final full Maven verify: **74 unit +701 PostgreSQL integration =775 passed,0 failures/errors/skips; BUILD SUCCESS**. Log `.cache/tenant-read-correction/verify.log`; counts taken from this run summaries, excluding leftover focused Surefire XML artifacts. PostgreSQL migration-failure log lines in intentional negative migration tests are expected and those tests passed. No frontend suite run. No unexpected contract or implementation dependency; only stale test assertion updated.

Exact task files:

- `ARCHITECTURE.md`
- `DOMAIN.md`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantOperations.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/TenantResourceIT.java`
- `docs/AUTHENTICATION.md`
- `docs/analysis/FRONTEND_U1_U2_DECISION_PREPARATION.md`
- `docs/analysis/GLOBAL_USER_READ_AUTHORITY_AUDIT.md`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md`
- `docs/verification/TENANT_GLOBAL_USER_READ_CORRECTION.md`

OpenAPI YAML parse and route checks passed; relative documentation links passed; git diff --check passed. Pre-gate hashes confirm these12 files only; all pre-existing unrelated work and prior evidence unchanged. Staged set empty. Exact final git status --short retained at `.cache/tenant-read-correction/final-status.txt`; pre-existing frontend/deployment/IDE/evidence work remains intentionally uncommitted. No schema/projection/query change, no operator-data changes or reset. STOP for backend correction acceptance; do not start F1b.

Human acceptance recorded2026-10-12. Verification counts/commands/limitations above remain unchanged historical evidence. F1b was subsequently separately authorized; no live evidence added to this record.
