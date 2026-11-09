# IServ configuration authority A1

Status: **IN PROGRESS**. The HUMAN approved the Global Admin write / Tenant Admin signed-own read boundary and explicitly authorized this gate on 2026-11-08. Review: **pending human acceptance**. On 2026-11-08 the HUMAN separately authorized a gate-only local commit and local 8443 update; push, PR, merge and human acceptance remain separate.

## Scope and ownership

Base: freshly fetched `origin/main` at `8c5583c` in isolated `feature/iserv-config-global-admin`. The root `main` dirty `AGENTS.md`, the separate `feature/iserv-login` gate and other worktrees remain outside this gate.

Global Admin alone may create, update, enable/disable or delete a concrete Tenant IServ configuration. Active Tenant Admin may read only the sanitized signed-own singleton, including the callback URI. Tenant Admin writes receive 403 independent of target existence. Preserve OIDC login, identity, sessions, data, provider credentials, singleton versioning and audit behavior. No schema or dependency change.

The HUMAN decision supersedes the earlier Tenant Admin mutation permission in [Domain](../../DOMAIN.md) and [Architecture](../../ARCHITECTURE.md). Update those owners, the [HTTP contract](../api/openapi.yaml), [authentication operations](../operations/authentication.md) and [IServ interoperability procedure](../operations/iserv-interoperability.md) with the implementation. The earlier login-baseline gate is not authorization for this policy correction.

## Steps and acceptance

1. Enforce Global Admin at both service mutation entries before target lookup; keep existing scoped GET.
2. Render the Tenant Admin configuration page read-only, with safe fields and no save/delete controls; preserve Global Admin controls.
3. Update backend authorization and frontend role/browser tests, then run Maven verification, frontend tests/build and affected intercepted browser cases.
4. Record exact commands, results and limits here. Human review decides acceptance. The separately authorized local 8443 update follows a clean gate-only commit and a verified managed backup.

Acceptance requires 403 for Tenant Admin PUT/DELETE on own, foreign and absent targets, signed-own GET without secret, Global Admin CRUD, no Tenant Admin frontend mutation, and passing affected checks. Real IServ interoperability remains a separate manual evidence item.

## Verification

All checks ran from this isolated worktree on 2026-11-08. PostgreSQL coverage used disposable Testcontainers databases, never the operator's local 8443 data.

| Check | Result and boundary |
|---|---|
| `cd backend && MAVEN_USER_HOME="$PWD/../../maven-home" TMPDIR="$PWD/../../tmp" ./mvnw -B -o -Dmaven.repo.local="$PWD/../../maven-repository" verify` | Exit 0: 79 unit and 785 PostgreSQL integration tests passed, zero failures/errors/skips. |
| `cd backend && MAVEN_USER_HOME="$PWD/../../maven-home" TMPDIR="$PWD/../../tmp" ./mvnw -B -o -Dmaven.repo.local="$PWD/../../maven-repository" -Dtest=OidcConfigurationResourceIT test` | Exit 0 after the final absent-target HTTP assertions: 58 tests passed. |
| `cd frontend && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test` and `cd frontend && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run build` | Both exit 0: 12 test files / 269 tests; TypeScript and Vite production build passed. Matching lockfile dependencies were reused from an ignored sibling worktree link during the checks; that temporary link was removed afterward. |
| `cd frontend && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm run dev -- --port 18081 --strictPort`, then `PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH=/Users/ksewen/IdeaProjects/github/kelios/.cache/iserv-login/.cache/playwright KELIOS_BASE_URL=http://127.0.0.1:18081 ./node_modules/.bin/playwright test tests/oidc-configuration-collection.spec.ts tests/f4-resource-detail.spec.ts --grep 'IServ collection\|TENANT ADMIN sees only own singleton\|iServ Save and Delete\|OIDC 409 deletion\|OIDC 404 deletion'` | Exit 0: 11 intercepted Chromium cases passed, including Tenant Admin read-only and Global Admin mutation UX. Temporary Vite server stopped. These are browser fixtures, not real IServ evidence. |

The first sandboxed Maven attempt could not access Docker; the first complete unrestricted run exposed one stale Tenant Admin 409 expectation and failed only that case. After correcting it, full verification passed. A sandboxed Chromium launch and offline npm install also failed for environment/cache reasons before the passing checks above. Raw logs remain only under ignored `.cache/verification/iserv-configuration-authority-a1/` during review.

`git diff --check`, local Plan-link checks and `ruby -ryaml -e 'YAML.parse_file(ARGV[0]); puts "OpenAPI YAML syntax parsed"' docs/api/openapi.yaml` passed. No real provider login occurred.

## Local 8443 update

Before the update, the CLI-managed installation at `.cache/manual-acceptance.5DG4O9Wb/.kelios` was running release `298a07c64deb` with healthy backend, frontend and managed PostgreSQL at `https://localhost:8443`. `./kelios --state-dir <saved state> doctor` passed. A new private managed recovery bundle was successfully created at `.cache/operator-backups/iserv-authority-8443-20261108-298a07c`; its manifest records release `298a07c64deb`, and its directory/file permissions are 0700/0600. The existing PostgreSQL volume and saved secrets are preserved.

The gate-only 16-file commit `387629460ae7` was created on `feature/iserv-config-global-admin` after staged diff, whitespace, link and private-material checks. `./kelios --state-dir /Users/ksewen/IdeaProjects/github/kelios/.cache/manual-acceptance.5DG4O9Wb/.kelios update` exited 0 from that clean source. The CLI now reports release `387629460ae7`; backend, frontend and postgres are running healthy. Docker reports backend/frontend images tagged `387629460ae7` and the same managed volume `kelios-local-7e424355e8a4_postgres-data`. Certificate-verified `https://localhost:8443/` returned HTTP 200, and `/api/v1/status` returned HTTP 200 with `{"application":"KELIOS","status":"ready"}`. The saved TLS certificate covers `localhost` and `127.0.0.1` and expires on 2026-11-19; the configured public origin remains `https://localhost:8443`.

This local health check does not establish a real IServ login, validate group UUIDs or constitute HUMAN ACCEPTANCE. No Tenant provider configuration or account was intentionally changed by this gate; runtime startup effects were not separately audited. This post-deployment evidence changes no application code and is recorded separately from the installed release. No push or PR is authorized.

## Local review correction: configuration entry

On 2026-11-08 the HUMAN reported that a signed-in Global Admin could not add an iServ Connector on local 8443 and clarified that adding one must explicitly choose a Tenant, as other Global Admin resource flows do. Review of release `387629460ae7` found that the Global Admin iServ navigation opened a collection of existing configurations with no create entry when the collection was empty. The Tenant-specific form and backend write permission already existed. The scoped correction adds a Tenant selector to the Global Admin iServ collection, linking to that Tenant's existing form; when no Tenant exists, it links to Tenant creation. The collection remains the edit entry for existing configurations. No backend, API or database contract changes.

After this UI correction, `cd frontend && PATH=/opt/homebrew/opt/node@24/bin:$PATH npm test` passed 12 files / 269 tests, and `npm run build` passed TypeScript and Vite. With `PATH=/opt/homebrew/opt/node@24/bin:$PATH PLAYWRIGHT_BROWSERS_PATH=/Users/ksewen/IdeaProjects/github/kelios/.cache/iserv-login/.cache/playwright KELIOS_BASE_URL=http://127.0.0.1:18081`, `./node_modules/.bin/playwright test tests/administration-context.spec.ts tests/oidc-configuration-collection.spec.ts` passed 71 intercepted Chromium cases, including an empty iServ collection, explicit Tenant selection before initial PUT, zero-Tenant navigation, Tenant Admin read-only behavior and role demotion. A follow-up `--grep 'GLOBAL ADMIN opens initial provider|empty GLOBAL collection|GLOBAL ADMIN without a Tenant|Tenant list failure'` passed four focused cases after the final error-display correction. The first focused run failed one assertion because two identical error alerts were rendered; removing the duplicate resolved it. The final production build passed. These fixtures do not prove real iServ interoperability. Backend verification above remains applicable because no backend code changed. Review remains pending human acceptance; the local 8443 update for this correction is recorded below after deployment.

The eight-file correction was committed locally as `129a2935927a` after exact staged-manifest and whitespace checks. A macOS-host `./kelios backup` attempt failed safely because the bundle publisher requires Linux `renameat2`; it did not publish or overwrite a bundle. The same backup command in the repository's existing `kelios-gate8-linux:local` CLI container, with the ordinary operator UID and authorized Docker socket group, exited 0. The private bundle is `.cache/operator-backups/iserv-entry-8443-20261108-3876294`; its manifest records source release `387629460ae7` and private file/directory permissions were checked. From the clean feature worktree, `./kelios --state-dir /Users/ksewen/IdeaProjects/github/kelios/.cache/manual-acceptance.5DG4O9Wb/.kelios update` exited 0. Status now reports release `129a2935927a` with healthy backend, frontend and PostgreSQL. Docker reports both application images tagged `129a2935927a`; the managed volume remains `kelios-local-7e424355e8a4_postgres-data`. With the installation's certificate supplied as the trust anchor, HTTPS `/` and `/api/v1/status` both returned 200, and status reported `ready`. No real iServ login or Global Admin browser interaction against the updated 8443 was performed; HUMAN ACCEPTANCE, push and PR remain pending.

Exact successful backup invocation from the feature worktree:

```sh
docker run --rm --platform linux/amd64 --network none --user 502:20 --group-add 0 -v /Users/ksewen/IdeaProjects/github/kelios:/Users/ksewen/IdeaProjects/github/kelios -v /Users/ksewen/.docker/run/docker.sock:/var/run/docker.sock -w /Users/ksewen/IdeaProjects/github/kelios/.cache/iserv-config-global-admin -e GIT_CONFIG_COUNT=1 -e GIT_CONFIG_KEY_0=safe.directory -e GIT_CONFIG_VALUE_0=/Users/ksewen/IdeaProjects/github/kelios/.cache/iserv-config-global-admin kelios-gate8-linux:local ./kelios --state-dir /Users/ksewen/IdeaProjects/github/kelios/.cache/manual-acceptance.5DG4O9Wb/.kelios --output /Users/ksewen/IdeaProjects/github/kelios/.cache/operator-backups/iserv-entry-8443-20261108-3876294 backup
```
