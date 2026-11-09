# Bugfix: explicit trust for private-network iServ hosts

**Status:** IN PROGRESS — implementation authorized by the operator on 2026-11-09. **Review:** pending human acceptance. The operator authorized a scoped local commit, branch push and PR on 2026-11-09. Merge and production deployment remain separately gated.

## Problem and decision

The production iServ issuer hostname resolved to the operator's `10.0.0.1` gateway. `/start` returned 503 with sanitized `OIDC stage=discovery transport=UnknownHostException`. The backend deliberately blocks private DNS results unless the exact hostname is in `kelios.auth.trusted-provider-hosts`. The deployment CLI removed that input from its environment and did not persist an operator setting. A 503 alone did not establish network or TLS failure; the DNS result and code path identify the current block.

## Authorized implementation

- Add a persistent, optional `TRUSTED_PROVIDER_HOSTS` installation field, defaulting to empty for existing state. Expose it through interactive and noninteractive `./kelios config`, validate exact comma-separated DNS hostnames, and pass it to the existing Compose/backend setting. Existing state, secrets, external PostgreSQL and other installations are not rewritten by read-only commands.
- Keep the backend's default private-address block, exact-issuer/endpoint checks and TLS validation. Do not add a provider bypass, public API, schema migration or Connector UI setting.
- Update Domain, Architecture, deployment and iServ interoperability guidance. The operator must verify each trusted hostname and DNS result; the production host is not accessible from this repository.

## Verification and acceptance

Run `./scripts/test-deployment-cli.sh` and the relevant Maven authentication tests. Cover legacy state, absent/default trust, save/clear through both config paths, malformed input refusal without manifest mutation, and Compose propagation on `start`. Verify staged diff, tracked secrets exclusion and preserved unrelated work.

After a separately approved merge and production update, the operator backs up external PostgreSQL and KELIOS state, configures only the approved issuer/endpoint hostnames, runs `start` and `status`, then retries a fresh login. A successful Discovery step is not proof of complete iServ login; TLS, metadata and authorization remain to be observed. Record sanitized results only.

## Evidence — 2026-11-09

Baseline: fetched `origin/main` at `1456aee58bcbe83af0cc5313a54f8fc22e9e196b`; implementation is in an isolated `bugfix/iserv-private-provider-hosts` worktree. Root `main` and its pre-existing `AGENTS.md` modification were not changed.

- `./scripts/test-deployment-cli.sh`: 72 tests passed in 211.401 seconds with normal pseudo-terminal privileges. Earlier sandbox runs exposed two new-test defects, corrected before this run; a later sandbox run had 16 existing install/recovery tests fail at `stty: TIOCGETD: Operation not permitted`. The privileged rerun passed all 72, including those cases. Raw output remains in ignored `.cache/verification/iserv-private-provider-hosts/`.
- `MAVEN_USER_HOME=/Users/ksewen/IdeaProjects/github/kelios/.cache/maven-home ./mvnw -B -o -Dmaven.repo.local=/Users/ksewen/IdeaProjects/github/kelios/.cache/maven-repository -Dtest=AuthPolicyTest -Dit.test=IdentityAuthenticationIT verify`: passed with normal loopback fixture privileges; 17 unit and 91 isolated OIDC/PostgreSQL integration tests, zero failures. An initial sandbox run failed before the integration tests because it could not bind the local HTTPS fixture socket.
- `./scripts/check-portable-paths.sh` and `git diff --check`: passed. No frontend test is required because the frontend and public API did not change.

The Compose propagation test uses a fake Docker CLI and generated local keys/certificates; it does not prove reachability or TLS behavior on the production gateway. Real production DNS/TLS/login outcomes remain operator-owned observations. Review remains pending human acceptance; no merge or production deployment has occurred.
