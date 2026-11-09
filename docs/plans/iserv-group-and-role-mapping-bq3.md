# IServ group and role mapping BQ3

Status: **IN PROGRESS**. HUMAN approved this BQ3 plan and explicitly authorized implementation on 2026-11-09 on the existing `feature/iserv-config-global-admin` branch. BQ2 remains pending HUMAN acceptance. Review: pending HUMAN acceptance after verification and local operator testing.

## Scope and ownership

The same Connector rule table accepts either IServ Gruppe account `act` or IServ Rolle `displayName`, each mapped to KELIOS USER or ADMIN. Both values match exactly and case-sensitively. One valid match permits OIDC login; any ADMIN match wins at first User creation. No match denies login with 403. First-created Users remain PENDING; mapped ADMIN requires GLOBAL ADMIN approval. Later OIDC logins require a current match but do not rewrite KELIOS role/status. Refresh behavior is unchanged.

The HTTP singleton retains `groupMappings` and adds `roleMappings: [{displayName,role}]`. Omission of roleMappings on create means empty; omission on update retains existing rules; explicit `[]` clears them. The new UI always submits both complete lists. V11 adds a separate role table without rewriting existing group rows. Configuration version, transaction, audit, secret handling and GLOBAL ADMIN write permission remain in force.

Authorization requests use `openid profile` plus `iserv:groups` when group rules exist and `iserv:roles` when role rules exist. After ID Token and UserInfo `sub` binding, each configured claim is parsed independently. A missing or malformed claim grants nothing from that source and does not cancel a valid match from the other. The role parser accepts only the documented list shape; role `uuid` and deprecated `id` never grant authority. The group parser continues to accept the documented list and observed object-of-records shape.

## Implementation order and exclusions

1. Add V11, service/DTO changes and strict role rule validation, preserving old PUT behavior.
2. Add conditional scope requests, independent claim parsing and combined ADMIN-first decision under the current configuration lock.
3. Update the single Connector rule table and read-only view, English/German labels, tests and fixtures.
4. Synchronize DOMAIN, ARCHITECTURE, OpenAPI and IServ operator instructions.
5. Run Maven verification, frontend tests/build and Connector browser regression. After a scoped local commit, back up and update the managed 8443 instance; preserve the original PostgreSQL volume and check HTTPS/service health. The human uses controlled accounts for live tests; no password is needed by the agent.

No additional identity provider framework, automatic role sync, approval bypass, provider token persistence, WebUntis integration or undocumented role claim shape is part of BQ3. The temporary default-off raw UserInfo diagnostic switch remains governed by the later interoperability closeout gate and must not be enabled for production.

## Acceptance and evidence

Automated acceptance covers group-only, role-only, mixed ADMIN priority, no-match 403, missing/malformed source combinations, duplicate configured and provider role names, PENDING/ADMIN and Global Admin approval, stable existing role, old PUT preservation, and V11 group-row preservation. Local fixtures prove implementation behavior, not the test IServ Client's `iserv:roles` permission or real role payload. The human must verify those in the controlled school environment. A renamed or non-unique role displayName can affect authorization; administrators must maintain exact Connector rules.

Verification and local rollout results are recorded below. HUMAN acceptance, push and PR merge remain outside this implementation authorization.

## Verification on 2026-11-09

- Baseline: clean `feature/iserv-config-global-admin` at `9dbaecb` (confirm current HEAD in closeout); existing BQ2 scoped commit and other worktrees were preserved. No remote fetch, push or merge was part of BQ3.
- First `backend/./mvnw -B -o -q -Dmaven.repo.local=<repo>/.cache/maven-repository verify` run exposed stale test projections: five tests still expected ten migrations or the old configuration response, and two fixture authorization flows rejected the new scope selection when no rule was configured. The tests and fixture were corrected; no production permission check was weakened.
- Full rerun of the same Maven command: exit 0, 141 unit and 801 PostgreSQL/HTTPS integration tests, zero failures/errors/skips. PostgreSQL containers were disposable; the local operator database was not used. A focused `-Dtest=AuthPolicyTest -Dit.test=OidcConfigurationResourceIT,IdentityAuthenticationIT verify` passed after adding missing-claim coverage and retaining the existing audit shape. A final focused `-Dtest=AuthPolicyTest -Dit.test=IdentityAuthenticationIT verify` after improving source-specific diagnostic counts exited 0.
- `frontend/npm test`: exit 0, 269 tests. `frontend/npm run build`: exit 0. Browser fixtures via Playwright: 72 Connector/administration tests passed; after adding the Tenant Admin role read-only assertion, two focused tests passed. These use intercepted HTTP responses, not the real IServ environment.
- `git diff --check`: exit 0. OpenAPI YAML parsed and both `ProviderInput` and `OidcConfiguration` expose `roleMappings`. English and German locale JSON parsed. No secret or raw provider claims are part of the change.
- Before rollout, local frontend/backend/PostgreSQL containers were healthy on BQ2 image `3b825cb51fdf`, and original volume `kelios-local-7e424355e8a4_postgres-data` was identified. A managed backup completed to ignored private repository scratch `.cache/operator-backups/iserv-bq3-20261109`. The human explicitly authorized the scoped local BQ3 commit and 8443 update. Commit `c93b35b01577` contains only the inspected 27-file BQ3 manifest; staged whitespace, link and private-material exclusion checks passed. No push or merge occurred. The managed `./kelios --state-dir <private saved state> update` returned success for release `c93b35b01577` using the prior backup; no restore was needed. Both application containers run images tagged `c93b35b01577`, all three services are healthy, the original PostgreSQL volume is unchanged, Flyway latest successful version is 11, and `curl --cacert <managed certificate> https://localhost:8443/api/v1/status` returned `{"application":"KELIOS","status":"ready"}`. The root main worktree and its pre-existing dirty `AGENTS.md` were untouched.
- Remaining real-provider checks: the dedicated IServ Client's `iserv:roles` scope permission, actual role-list shape and exact `displayName`, role-only first login, mixed group/role ADMIN precedence, unmapped 403, PENDING/approval, and existing-role continuity. No real account login was performed by the agent.
