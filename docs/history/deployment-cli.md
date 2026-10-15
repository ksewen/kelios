# Accepted Deployment CLI and Email recovery closeout

The guided seven-command local CLI, O1 Email recovery handoff and DCLI-B1 Docker-context correction are **HUMAN ACCEPTED / CLOSED**. Current commands and retention semantics live in the [operator guide](../operations/local-deployment.md) and [authentication operations](../operations/authentication.md); this is dated evidence, not another operator manual. Accepted commits include O1 `5f8eb776729219eae58c6d6da0551922ea00f724` and DCLI-B1 `9a0332b435079453dc585e9b98bab65a633eecdf`.

## CLI v1 and O1

CLI v1 kept project state/keys/certificates/password files under ignored `.kelios`, bounded input/prompts, non-echoed password handling, prepared-versus-initialized state and sanitized recovery refusal. Its isolated control suite passed 23 cases; backend unit 74, frontend unit 51, HTTPS Chromium green 8/blue 6 passed. These were local fixture results, not real school Provider interoperability or production deployment certification. Historical CLI v1 design used Login because it predated I1; that identifier is **retired**.

O1 found shared install/recover-admin recovery helper still sent `kelios.recovery.login` while accepted backend required `kelios.recovery.email`. The bounded fix requires explicit GLOBAL ADMIN Email, no account default, only local nonblank checking, a single safely quoted `--kelios.recovery.email=<operator input>` element and no fallback. Backend remains authoritative for normalization, validation and eligibility; existing display-name, exact password bytes/file/privacy/cleanup/state/failure behavior was preserved. O1.1–O1.7 were separately reviewed and human accepted. Focused install/recover-admin PTY fake-Docker tests checked exact argv, whitespace, shell-splitting protection, refusal/no retry and state. Final maintained control suite **35 passed** (original 23 plus six install and six recovery controls); focused isolated `IdentityAuthenticationIT` PostgreSQL/Email recovery **15 passed**, each with zero failures/errors/skips. They are separate evidence categories. The first sandbox PTY attempt had two passes/five failures from `stty` TIOCGETD permission denial; an authorized isolated rerun passed, without a production change. Fake Docker proves CLI control flow only, and no live operator recovery was performed for O1. Operational documentation was synchronized after verification, not retroactively rewritten into old evidence.

## DCLI-B1 — accepted Docker-context and real local lifecycle

Diagnosis/implementation: 2026-10-14. **HUMAN ACCEPTED / CLOSED, 2026-10-15.** This is DCLI-B1, not integration B1/D29. No O3 or subsequent backlog gate was authorized by this closeout.

## Scope and decision

Baseline: `4d9af4ef52ef3ffdcdbe27fce2a5506473f60754`. The supported local CLI exposed a packaging/build-context defect, not an authentication, Domain or fixture-model defect. The only executable correction was [frontend/.dockerignore](../../frontend/.dockerignore): replace `tests` with:

```text
tests/**
!tests/backend-fixtures.ts
```

The parent remains traversable, browser-test content remains excluded, and only the shared fixture is admitted. Existing exclusions for node_modules, dist, test-results, Playwright configuration and logs remain. No fixture relocation/duplication, browser-consumer edit, full test-tree inclusion, skipped test, generic framework/retry, Dockerfile change or runtime/authentication/schema/transaction policy change was introduced. Runtime still copies only dist and existing nginx configuration/scripts from the independent build stages.

[backend-fixtures.ts](../../frontend/tests/backend-fixtures.ts) contains synthetic factories and a type-only import from `src/api-contracts`; it has no Playwright/runtime dependency or application-entry import. Its six browser consumers are form-lifecycle, dialog-toolbar, merge-combobox, search-stability, product-conflict-feedback and product-table-readability. Moving it would unnecessarily change these accepted consumers.

## Original failure and diagnosis

The first supported `./kelios install` ran in an isolated committed-source copy, `.cache/operator-local-20261014/source`, with fresh private state and loopback ports. It failed before runtime startup or administrator recovery; no administrator or business data was created.

Dependency chain:

- [Compose](../../deploy/compose.yaml) selects `../frontend` as build context.
- [Dockerfile](../../frontend/Dockerfile) installs locked dependencies with `npm ci`, copies the filtered context, then runs `npm test && npm run build` as an existing builder prerequisite, not a CLI-added/runtime test.
- [Package scripts](../../frontend/package.json) run Vitest and then TypeScript/Vite; [Vitest configuration](../../frontend/vitest.config.ts) includes `src/**/*.test.ts` rather than browser specs.
- The original ignore rule removed `tests/` before COPY. [fixture-contracts.test.ts](../../frontend/src/fixture-contracts.test.ts) line2 imports `../tests/backend-fixtures`, which then was absent from `/workspace/tests`.

Exact failure:

```text
Cannot find module '../tests/backend-fixtures' imported from /workspace/src/fixture-contracts.test.ts
```

Ten test files / 218 tests passed; one suite failed to load and its four fixture-contract cases did not execute. Frontend build exited1; the parallel backend build was cancelled, not proven defective. Private failure output remained at `.cache/operator-local-20261014/source/.kelios/temporary/build-output`.

Static diagnosis covered all eleven unit files and explicit imports/filesystem reads: other inputs were local source/locales or installed dependencies, the localization scanner read within src, and the timezone child process loaded `src/local-time.ts`. No second excluded unit dependency was found. This was static scope, not a broad runtime/security guarantee. The diagnosis itself performed no patch, suite rerun, image rebuild, recovery/bootstrap or commit; the original install supplied its failure evidence. The narrow correction and later isolated retry were separately authorized.

## Docker verification

The essential regression was the real Docker context: host-only `npm test` cannot detect an excluded fixture. Actual commands from repository root:

```sh
docker build --progress=plain -t kelios-frontend:dcli-b1 frontend
docker build --progress=plain --target build -t kelios-frontend:dcli-b1-builder frontend
```

Both exited0. The first build freshly executed COPY and unit/build steps, not cached: **11 suites / 222 tests passed**, including all four fixture-contract cases, with zero reported failures/skips. `tsc --noEmit && vite build` and final nginx runtime-stage/export passed. Runtime image: `sha256:59e6a48155677188aed89de63de52483c95efe83e4521625ab643c88e67e3bb5`.

`python3 .cache/dcli-b1/inspect-images.py` exited0. It created never-started inspection containers, exported their filesystems and removed only those containers. Builder test files were exactly `/workspace/tests/backend-fixtures.ts`, with no browser specs or Playwright configuration. Runtime inspection found no workspace, node_modules, test fixture/spec/unit sources, `.kelios` state or private-key/AES files. Web files comprised index/default error page, built assets and public branding config; built assets contained neither `Fixture Tenant` nor `assetDetailFixture` markers. This was bounded artifact inspection, not exhaustive secret scanning or browser interoperability proof.

No backend integration or browser suites were rerun for this packaging-only correction. Closeout changed no executable bytes, so expensive builds/install/tests were not rerun then either.

## Supported install and removal

A new ignored directory, `.cache/operator-local-dcli-b1/source`, contained an archive of the full baseline commit plus exactly the verified ignore correction. Comparison of every tracked source file confirmed that boundary; unrelated dirty source/documentation was not adopted and the original failed installation was untouched.

Exact supported workflow:

```sh
cd /Users/ksewen/IdeaProjects/github/kelios/.cache/operator-local-dcli-b1/source
./kelios install
```

Inputs were loopback `127.0.0.1`, HTTP technical port18080, HTTPS18443, hostname localhost, a fresh self-signed hostname-matching certificate/key, managed PostgreSQL, green/default logo and new authentication keys. Explicit administrator Email/display name and a generated compliant password were entered through existing prompts. Values were retained only privately and provided directly to the operator, not copied into evidence.

CLI exited0, reported protected GLOBAL ADMIN creation and `KELIOS installation ready: https://localhost:18443`. Its own independent image builds/startup/recovery were used; no manual Compose assembly, bootstrap bypass, reset or automatic retry. Project: `kelios-local-052ef101e6a4`.

`./kelios status` exited0: backend/frontend/postgres all `running (healthy)`, managed PostgreSQL and that URL. Read-only `curl --insecure --silent --show-error --fail` requests to `/` and `/api/v1/status` exited0; frontend HTML and `{"application":"KELIOS","status":"ready"}` were returned. Insecure certificate checking was limited to those local requests; application TLS behavior did not change. Browser trust/exception is needed for the self-signed certificate; HTTP is not a login fallback.

The environment was left running at that checkpoint, then removed on the operator's explicit instruction. `./kelios uninstall` from the same source directory received yes for runtime removal, yes for managed-database deletion, and exact `DELETE kelios-local-052ef101e6a4` confirmation. It exited0: database deleted, configuration/keys preserved. No manual Docker deletion was used.

Post-uninstall `./kelios status` exited0: stopped-or-degraded, all three services absent, managed database permanently deleted. Before/after identity checks found exactly three project containers, one network and one managed volume removed; zero images removed and no unrelated resource identities changed. `.kelios` remains marked data-deleted with keys/certificates; supplied TLS originals and locally retained private credential material also remain. The removed database no longer contains the bootstrap account. The CLI does not purge host files/images and refuses silent restart of data-deleted state; a new clean source/state directory is the documented fresh-install path. No restart followed. Only supported infrastructure/minimum-admin bootstrap occurred; no business test data or existing operator data was changed.

Current procedures belong to [the operator guide](../operations/local-deployment.md), not this historical record. `./kelios stop` was the documented stop command, but **no standalone stop/start cycle** was verified in this sequence.

## Limits and provenance

No browser login/UI walkthrough, real OIDC interoperability, real Email delivery or exhaustive private-material scan was claimed. Administrator evidence is protected recovery success; runtime evidence is CLI health and read-only HTTP, distinct from isolated unit/image checks. Original failure evidence is not replaced by later success.

Local evidence pointers (ignored scratch, not committed artifacts or guaranteed archival storage):

- `.cache/operator-local-20261014/source/.kelios/temporary/build-output`: original failure.
- `.cache/dcli-b1/docker-build.log`, `.cache/dcli-b1/builder-build.log`: actual builds.
- `.cache/dcli-b1/image-inspection.json`: bounded filesystem inspection.
- `.cache/operator-doc-closeout/{containers,volumes,networks,images}-{before,after}.txt`: uninstall preservation checks.

A label-scoped `docker logs --tail 5` frontend inspection was verified before uninstall. No scratch/private material is moved/deleted by this consolidation. During implementation, pre-existing content hashes were preserved except the authorized ignore/status edits; doc links/whitespace/diff checks passed and the index remained empty at that checkpoint. Commit permission came later.

## Human acceptance and ownership

Diagnosis, correction, successful real supported install/status/uninstall verification and operator-documentation closeout are HUMAN ACCEPTED / CLOSED,2026-10-15. Commit: `9a0332b435079453dc585e9b98bab65a633eecdf`, `fix(deployment): include unit fixtures and close local deployment verification`.

The original diagnosis found README already linked the guide but discoverability/log instructions needed work. A separately authorized follow-up added the early README link and guide instructions for source/TLS/prompts/Email/password, URL/status/start/stop, label-scoped Docker logs, deletion/retention, recovery and failure output. There is no CLI logs subcommand. Shell/link checks were performed; the temporary uncommitted-correction copy instruction was removed when preparing the correction commit. No fresh install was claimed for documentation-only closeout.

Exact original accepted five-path manifest (old report names are historical provenance, not current navigation):

- `frontend/.dockerignore`: fixture allowlist only.
- `README.md`: early guide link.
- `docs/DEPLOYMENT_CLI.md`: this wave's scoped instructions/status/evidence link only.
- `docs/analysis/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_DIAGNOSIS.md`: bounded diagnosis/acceptance.
- `docs/verification/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_CORRECTION.md`: original results plus lifecycle/acceptance.

Unrelated guide preamble/password wording, INVENTORY, historical deployment-plan edits, IDE files, Phase A/B directories, remaining-plan audit and private/generated artifacts were excluded. Exact staged ownership/private-material/whitespace checks passed; unrelated bytes remained and the index was empty after commit. No push, PR, new data, runtime restart or O3–O7 gate followed. The governing history remains unchanged; this consolidated record retains the original failure, results, limitations and acceptance rather than making historical verification stronger.
