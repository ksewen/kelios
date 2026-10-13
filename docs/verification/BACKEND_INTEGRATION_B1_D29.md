# B1 / D29 — anonymous OIDC organization-routing lookup alignment

Date: 2026-10-13. Backend-only correction, separately authorized after accepted frontend integration commit e09a9e5a6b20055c0b301b4fa6555388523e8abe. [Authoritative integration plan](../plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md). Status: IMPLEMENTED / PENDING HUMAN ACCEPTANCE; full results below.

## Exact backend contract delta

GET /api/v1/auth/oidc/organizations now supplies page=0,size=20 when omitted (previous size=25). Existing page bounds 0–100000 inclusive and size bounds 1–100 inclusive remain. Explicit size=25 remains valid; it is not a second compatibility path. Response remains items/totalElements/page/size.

OidcConfigurationService.organizations strips surrounding Java whitespace before search validation. Empty/all-whitespace text becomes the existing unfiltered literal pattern. Normalized text is bounded to128 Java String.length UTF-16 units, consistent with ordinary resource criteria; over-bound/null service input returns the existing AuthFailure400 error. No Unicode normalization/search framework introduced. The query still binds the escaped literal text and uses PostgreSQL case-insensitive substring matching; %, _ and backslash remain literal. Filtering/counting precede LIMIT/OFFSET; readable lower(name),id ordering and long offset arithmetic are unchanged. Beyond-end requests return an empty items array with current full count and requested page/size.

Only enabled configured Tenants appear. Public items remain exactly routingId/name; no Tenant metadata, memberships, issuer/client/allowlist/secret/credential/User fields. DTOs are unchanged. Anonymous lookup neither authenticates nor provisions nor grants authority; existing explicit selected routing UUID start/callback and exact verified issuer/subject identity remain unchanged. No inferred/default Provider.

OpenAPI changes are confined to this operation: size default20 and normalized/literal query semantics. The raw q maxLength assertion is replaced by the explicit normalized128-unit validation description because a raw length constraint would incorrectly reject surrounding whitespace on a valid128-unit normalized search. No other endpoint, response projection or error schema changes.

## Exact task manifest

- backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthController.java
- backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java
- backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java
- docs/api/openapi.yaml
- docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
- docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md
- docs/verification/BACKEND_INTEGRATION_B1_D29.md

No frontend production/test files, backend schema/migrations, provisioning, authorization helpers or administrative commands changed. Restoring only the controller default and service normalization/length edits exactly reproduces their pre-gate SHA-256 hashes; all other behavior in these files is byte-identical.

## Focused regression and isolated verification

Two existing discovery tests retained and strengthened (default size/page assertion). Nineteen added parameterized/test cases cover omitted default, blank ASCII/Unicode whitespace, trim/case-insensitive service+HTTP lookup,128/129 bounds and padded valid128 search, literal wildcard characters with enabled decoys, invalid page/size endpoints, explicit sizes1/10/20/30/40/50/100 and maximum page, default20 first slice plus later5/beyond-end0 slices over25 matched fixtures, full scoped count, enabled/unconfigured exclusions, exact minimal projection and unchanged User/external identity/audit row counts on discovery. Existing explicit routing, CSRF, JIT PENDING and mismatched provisioning assertions remain and run in focused coverage.

All database/authentication fixtures are isolated PostgreSQL17 Testcontainers with the existing ephemeral HTTPS OIDC test server. No manual/operator environment or credentials are used. Fixture Tenant/configuration creation is isolated automated-test setup, not operator business data.

Commands run from backend, with MAVEN_USER_HOME="$PWD/../.cache/maven-home" and TMPDIR="$PWD/../.cache/tmp":

```sh
./mvnw -B '-Dit.test=IdentityAuthenticationIT#organizationDiscovery*' test-compile failsafe:integration-test failsafe:verify
./mvnw -B verify
```

Focused result: **21 PostgreSQL integration tests passed; zero failures/errors/skips; BUILD SUCCESS**. Focused cases are included in the full suite, not extra unique passed tests. Repository-local ignored logs: .cache/backend-b1/focused.log and verify.log. Full verification results and final checks below.

OpenAPI YAML parse/operation checks pass (anonymous security,0/20 defaults, unchanged bounds and minimal response);219 internal references resolve. No frontend suite run is required or claimed for this backend-only gate.

## Boundaries and remaining dependencies

B1/D29 must remain IMPLEMENTED / PENDING HUMAN ACCEPTANCE after successful verification, never self-accepted. Anonymous F2/D20/D22 rollout remains open and NOT AUTHORIZED; OrganizationPicker/auth client continuation/defaults/normalization remain unchanged. Backend correction alone does not close that frontend subset. The old explicitly requested frontend25 batch still works within the preserved bounds.

B2/D28 remains BLOCKED / NOT AUTHORIZED. Product conflict mapping,390px wrapping, Phase C/D, export/delivery, deployment and other residual work remain untouched. Existing accepted frontend evidence remains historical and unmodified. No schema/DTO change, private material, operator writes, environment changes, reset, stage, commit, push or PR. STOP for human review.

## Final verification and preservation

Full ./mvnw -B verify: **74 unit +720 PostgreSQL integration =794 passed**, zero failures/errors/skips; BUILD SUCCESS,1m41s. Full suites include IdentityAuthenticationIT and OidcConfigurationResourceIT alongside all accepted resource/query/metadata/locking/transaction/security coverage. Focused21 is a subset, not added to794. Maven compile/package and isolated migration verification pass; no migration source changed.

OpenAPI219 internal references resolve and B1 defaults/bounds/security/projection assertions pass. git diff --check and new-file whitespace/relative documentation links pass. Pre-gate snapshot .cache/backend-b1/before.json verifies exactly the seven-file manifest; every other tracked/untracked file, all frontend production/tests, prior evidence, deployment documentation, IDE and historical artifact content is unchanged. Index empty. Final status below includes unrelated preserved work and is not the task manifest.

Counts above are from the executed Maven log (5 current Surefire suites,14 Failsafe suites), not indiscriminate report-directory sums: an old TenantResourceIT XML remains in surefire-reports from an earlier command and is not a current unit-suite execution. It was not removed or counted.57 relative documentation links checked.

Final git status --short:

```text
 M backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java
 M backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthController.java
 M backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java
 M docs/DEPLOYMENT_CLI.md
 M docs/INVENTORY.md
 M docs/api/openapi.yaml
 M docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md
 M docs/plans/DEPLOYMENT_CLI_PLAN.md
 M docs/plans/FRONTEND_BACKEND_INTEGRATION_PLAN.md
?? .idea/
?? docs/verification/BACKEND_INTEGRATION_B1_D29.md
?? docs/verification/merge-combobox-phase-b/
?? docs/verification/phase-b1/
?? docs/verification/phase-b2/
?? docs/verification/phase-b3/
?? docs/verification/phase-b4/
?? docs/verification/sidebar-phase-a/
?? docs/verification/sidebar-phase-a1/
?? docs/verification/sidebar-phase-a2/
?? docs/verification/sidebar-phase-a3/
?? docs/verification/sidebar-phase-a4/
```

## Human acceptance — 2026-10-13

B1/D29 HUMAN ACCEPTED / CLOSED by explicit human decision. All original verification results and limitations above remain historical and unchanged. The dependent anonymous F2/D20/D22 frontend subset was separately authorized; this adds no new backend verification, production change or stronger live evidence.
