# DCLI-B1 — Frontend Docker context correction and supported install

Execution date: 2026-10-14. Current status: HUMAN ACCEPTED / CLOSED, 2026-10-15.

The following original execution record preserves its results and execution-time state. The subsequent uninstall and final acceptance are appended below; the installation is now down.
[Diagnosis](../analysis/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_DIAGNOSIS.md) is HUMAN ACCEPTED / CLOSED. This is separate from the closed integration B1/D29 gate.

## Exact implementation and ownership

Only executable change: [frontend/.dockerignore](../../frontend/.dockerignore) replaces `tests` with `tests/**` and `!tests/backend-fixtures.ts`. The shared fixture, its six browser consumers, Dockerfile, frontend production modules and all backend/runtime policies are unchanged.

Task-owned documentation: diagnosis current-status paragraph and this new evidence file. README, deployment guide, historical deployment plan, INVENTORY, IDE files and historical evidence remain untouched. No staging or commit.

## Actual Docker verification

Commands from repository root:

```sh
docker build --progress=plain -t kelios-frontend:dcli-b1 frontend
docker build --progress=plain --target build -t kelios-frontend:dcli-b1-builder frontend
```

Both exited 0. The first command freshly executed COPY and unit/build steps (not cached): **11 unit suites, 222 tests passed**, including all four fixture-contract cases; zero reported failures/skips. `tsc --noEmit && vite build` passed; final nginx runtime stage/export passed. Runtime image: `sha256:59e6a48155677188aed89de63de52483c95efe83e4521625ab643c88e67e3bb5`. Local retained logs: `.cache/dcli-b1/docker-build.log` and `builder-build.log`.

`python3 .cache/dcli-b1/inspect-images.py` exited 0. It created never-started inspection containers, exported their filesystems, and removed only those containers. Builder test files: exactly `/workspace/tests/backend-fixtures.ts`; no browser specs or Playwright configuration. Runtime checks found no workspace, node_modules, test fixture/spec/unit sources, `.kelios` state or private-key/AES files. Web files comprise index/default error page, built assets and public branding config. Built web assets contain neither `Fixture Tenant` nor `assetDetailFixture` markers. The Dockerfile copies only dist and existing nginx/runtime scripts into the runtime stage. This is bounded artifact inspection, not an exhaustive secret scanner or browser interoperability test. Inspection output is retained at `.cache/dcli-b1/image-inspection.json`.

No host-only test result is substituted for Docker verification. No backend integration or browser suites were rerun for this packaging-only change.

## Supported fresh isolated install — succeeded

A new ignored directory `.cache/operator-local-dcli-b1/source` contains an archive of commit `4d9af4ef52ef3ffdcdbe27fce2a5506473f60754` plus exactly the verified `.dockerignore` correction. Byte comparison against every tracked HEAD file confirmed that boundary. No unrelated dirty source/documentation was adopted. The prior failed `.cache/operator-local-20261014` installation remains untouched.

Exact command/workflow:

```sh
cd /Users/ksewen/IdeaProjects/github/kelios/.cache/operator-local-dcli-b1/source
./kelios install
```

Inputs: loopback `127.0.0.1`, HTTP technical port 18080, HTTPS port 18443, browser hostname localhost, fresh self-signed hostname-matching certificate/key, managed PostgreSQL, green/default logo, new authentication keys. Explicit administrator Email/display name and a generated compliant password were entered through the CLI's existing prompts. Credentials are retained only in a private ignored local file and reported directly to the operator, not in this evidence.

CLI exited **0**, reporting protected GLOBAL ADMIN creation and `KELIOS installation ready: https://localhost:18443`. It performed its own independent image builds, runtime startup and recovery; no manual service assembly, bootstrap bypass, reset or retry was used. Project: `kelios-local-052ef101e6a4`.

`./kelios status` exited 0: running; backend, frontend and postgres all `running (healthy)`; managed PostgreSQL; URL `https://localhost:18443`.

Read-only `curl --insecure --silent --show-error --fail` requests to `/` and `/api/v1/status` exited 0; frontend HTML was returned and backend returned `{"application":"KELIOS","status":"ready"}`. `--insecure` was used only for these local certificate checks, not to alter application TLS behavior. Browser must trust/accept the new self-signed localhost certificate; HTTP is not an authentication fallback.

The environment remains running. Supported stop command from that source directory: `./kelios stop`. Private installation files live in its `.kelios/`; supplied TLS and locally retained credentials live in the ignored parent directory. No business test data was created. Only supported infrastructure initialization and minimum administrator bootstrap occurred. Existing operator containers/data were not changed.

## Limits and preservation

No browser login/UI walkthrough or real OIDC interoperability was claimed. The administrator creation result is the supported backend recovery success, and runtime verification is CLI health plus read-only HTTP. The original failed build evidence remains historical and unchanged.

Snapshot comparison confirmed every pre-existing tracked/dirty/untracked file unchanged except the authorized ignore correction and diagnosis status update. Documentation links, whitespace and `git diff --check` passed. Index remains empty. Discoverability/logs documentation follow-up remains separate and unchanged. No O3 or subsequent gate started.


## Subsequent supported uninstall and operator documentation

On explicit operator instruction, `./kelios uninstall` was run from `.cache/operator-local-dcli-b1/source`. The runtime-removal prompt was confirmed, followed by managed-database deletion and the exact `DELETE kelios-local-052ef101e6a4` confirmation. Exit 0: managed database deleted, configuration/keys preserved. No manual Docker deletion or service assembly was used.

Post-uninstall `./kelios status` exited 0 and reported backend/frontend/postgres absent, stopped-or-degraded, managed database permanently deleted. Before/after resource identity comparisons found exactly three project containers, one project network and one managed database volume removed; no images removed and no unrelated resource identities changed. Private `.kelios` state remains marked data-deleted. Supplied TLS originals and locally retained credential material also remain in ignored private directories; the deleted database no longer contains the bootstrap account. The CLI intentionally does not purge host files/images. It rejects restart of data-deleted state; a fresh source/state directory is the documented new-install path.

Local preservation records: `.cache/operator-doc-closeout/{containers,volumes,networks,images}-{before,after}.txt`. A label-scoped `docker logs --tail 5` inspection of the frontend was verified before uninstall. No standalone stop/start cycle or browser login was claimed. The earlier running-state paragraph is historical, not the current environment state.

README now exposes an early link to the authoritative deployment guide. Task-owned guide additions cover source preparation, localhost self-signed TLS, exact prompts/Email/password handling, URL/status, start/stop, label-scoped Docker logs, uninstall/data deletion, retained state, recovery and failure output. The final closeout removes the temporary uncommitted-correction copy instruction because the correction is included in this commit. Existing unrelated guide preamble/password-wording hunks remain excluded from the commit. README and guide instructions were link/shell-syntax checked; no fresh installation was repeated for documentation-only closeout.

## Final human acceptance and commit boundary — 2026-10-15

The human accepted the DCLI-B1 correction, successful real `./kelios install` verification and deployment documentation closeout. Status: HUMAN ACCEPTED / CLOSED. Original 11-suite/222-test Docker evidence, image inspections, failure history and runtime limitations remain unchanged. Executable bytes match the verified correction, so expensive builds/install/tests were not rerun.

Accepted five-path manifest:

- `frontend/.dockerignore`: only the shared fixture allowlist correction.
- `README.md`: early operator-guide link.
- `docs/DEPLOYMENT_CLI.md`: only this wave's operator instructions, scoped historical/current status distinction and evidence link; pre-existing unrelated edits excluded.
- `docs/analysis/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_DIAGNOSIS.md`: bounded diagnosis and acceptance status.
- `docs/verification/DEPLOYMENT_FRONTEND_DOCKER_CONTEXT_CORRECTION.md`: original results plus lifecycle/documentation/acceptance closeout.

No private runtime artifacts, credentials, historical Phase A/B directories, remaining-plan audit, INVENTORY, historical deployment-plan edits or IDE files belong to this commit. No environment restart, new operator/business data, O3–O7 work, push or PR is authorized by this closeout.
