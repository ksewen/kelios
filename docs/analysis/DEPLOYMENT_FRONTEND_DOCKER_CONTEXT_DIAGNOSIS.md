# DCLI-B1 — Frontend Docker unit-fixture context diagnosis

Status: diagnosis HUMAN ACCEPTED / CLOSED. The subsequent bounded correction and isolated installer retry were explicitly authorized; correction and successful real supported install verification are HUMAN ACCEPTED / CLOSED (2026-10-15). See [correction evidence](../verification/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_CORRECTION.md). Historical diagnosis and proposed-verification text below retains its original scope.
Date: 2026-10-14. Baseline: `4d9af4ef52ef3ffdcdbe27fce2a5506473f60754`.
This bounded deployment gate is separate from the completed integration B1/D29 gate and from O3/backlog work.

## Confirmed failure and dependency chain

The supported `./kelios install` was invoked in an isolated committed-source copy at `.cache/operator-local-20261014/source`, using fresh private state and loopback ports. It failed at image build, before administrator recovery or runtime startup. No business data or administrator was created. Retained private diagnostic output: `.cache/operator-local-20261014/source/.kelios/temporary/build-output` (local evidence, not a committed artifact).

- [Compose](../../deploy/compose.yaml) selects `../frontend` as the frontend build context.
- [Dockerfile](../../frontend/Dockerfile): Node build stage installs locked dependencies using `npm ci`, copies the filtered context, then explicitly runs `npm test && npm run build`. Unit verification is an existing image-build prerequisite, not a CLI-added test or a runtime operation.
- [Package scripts](../../frontend/package.json): `test` runs `vitest run`; `build` runs TypeScript checking followed by Vite.
- [Vitest configuration](../../frontend/vitest.config.ts) includes `src/**/*.test.ts`, not browser specs.
- [Docker ignore](../../frontend/.dockerignore) excludes the entire `tests` directory. `COPY . .` cannot copy excluded inputs.
- [Fixture contract unit](../../frontend/src/fixture-contracts.test.ts) imports `../tests/backend-fixtures` at line 2. The file therefore exists on the host but not in `/workspace/tests` in the builder.
- [Shared fixture](../../frontend/tests/backend-fixtures.ts) contains synthetic data/factory functions and a type-only import from `../src/api-contracts`. It imports no Playwright runner, browser suite, private data or runtime configuration.

Actual build output: `Cannot find module '../tests/backend-fixtures' imported from /workspace/src/fixture-contracts.test.ts`. Ten test files / 218 tests passed; one suite failed to load. Its four fixture-contract tests did not execute. Frontend build exited 1; the parallel backend build was cancelled. This is not evidence of a backend failure.

## Extent and classification

Static inspection covered all eleven unit files and their explicit imports/filesystem reads. The other unit inputs are local source modules/locales or installed dependencies. The localization scanner reads within `src`; the timezone test child process loads `src/local-time.ts`. No second excluded unit dependency was found. This is a static finding, not a claim that a corrected Docker build has already passed.

The fixture is shared with six browser suites: form-lifecycle, dialog-toolbar, merge-combobox, search-stability, product-conflict-feedback and product-table-readability. Its location under `tests` is reasonable test-support organization; no application entry imports it. The confirmed defect is packaging/build-context debt, not evidence of an incorrect Domain fixture or production module boundary. Moving it would require unnecessary import edits in those consumers.

## Smallest proposed correction — approval required

Executable change: **only `frontend/.dockerignore`**. Replace the directory-wide `tests` exclusion with:

```text
tests/**
!tests/backend-fixtures.ts
```

This keeps the parent directory traversable, excludes its contents by default, and includes only the shared fixture. Keep node_modules, dist, test-results, Playwright configuration and logs excluded. Do not include the complete browser test tree, skip the unit suite, duplicate fixtures, move modules, change package scripts or alter Dockerfile stages.

No deployment, authorization, TLS, bootstrap, secret-handling, database or runtime semantics should change. The runtime image continues copying only built static assets plus its existing nginx configuration/script; test sources and Node dependencies remain builder-only. Verify that boundary rather than assuming inclusion in the context means inclusion in runtime assets.

## Focused regression and acceptance plan

1. Inspect the one-file ignore delta and unchanged unit/browser fixture consumers.
2. Run an isolated frontend Docker build using the actual context/Dockerfile, with plain retained output and a gate-specific local image tag, e.g. `docker build --progress=plain -t kelios-frontend:context-regression frontend`. Do not use the shared deployment image tag or start services. A changed ignore rule must produce a fresh relevant COPY/test layer; if logs show cached unit execution, require a fresh build for evidence.
3. Require all eleven unit suites / current 222 tests, including the four fixture-contract cases, to execute successfully, followed by TypeScript/Vite build and final runtime-stage completion. Record actual counts; do not manufacture or overwrite the original failed result.
4. Inspect an isolated builder image (`--target build`, same context) to assert `/workspace/tests/backend-fixtures.ts` exists, its dependency resolves, and no browser specs/other test-tree files were admitted. This is a context regression check, not backend/Playwright verification.
5. Inspect the final image filesystem without launching the application: no `/workspace`, test sources, node_modules or private operator inputs; inspect generated asset/module inputs to confirm fixtures are not application bundle dependencies. No broad browser or backend rerun is required by this context-only change.
6. Run diff/whitespace/ownership checks, record results and stop for human acceptance. A later explicitly approved retry of supported `./kelios` is separate from this diagnosis; do not manually assemble Compose services or reset the retained prepared installation.

The real Docker build is the essential regression: host `npm test` alone cannot detect omitted context inputs. Preserve the existing fixture-contract test; no new business assertion or generic test framework is needed. Implementation/evidence may update this bounded document; production scope stays the single ignore file unless inspection reveals a new blocker requiring review.

## Separate operator-documentation follow-up — not implemented

[README](../../README.md) already links the authoritative [deployment guide](../DEPLOYMENT_CLI.md), supplies `./kelios install`, and explains TLS/private-state requirements. Discoverability could improve by placing a short guided-install link before developer/manual Compose instructions. The guide lacks a logs procedure, and the CLI has no `logs` command; a future documentation correction must describe a verified supported inspection procedure, not invent a CLI subcommand. Do not duplicate a second manual in README.

Existing dirty guide hunks add a general future-contract preamble and change a password-policy introductory phrase; the dirty historical CLI plan adds a historical-authority preamble. These are pre-existing, not adopted by this diagnosis. `docs/INVENTORY.md`, IDE files and historical evidence are also outside scope. No operator guide, README, existing plan or historical evidence was edited.

## Diagnosis boundaries

No patch, suite rerun, image rebuild, runtime action, recovery/bootstrap, staging or commit was performed during this diagnosis. The preceding supported-install failure supplies runtime evidence; this gate adds source inspection only. Any broader build failure found during later authorized verification must be reported before expanding scope.
