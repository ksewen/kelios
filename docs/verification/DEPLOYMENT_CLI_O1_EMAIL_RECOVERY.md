# O1 — Deployment CLI Email bootstrap/recovery verification

Date: 2026-10-13. **Current status: O1.1–O1.7 and overall O1 HUMAN ACCEPTED / CLOSED. The seven-path closeout commit is explicitly authorized. Historical execution-time statuses below remain unchanged.**

[Authoritative O1 execution plan](../plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md). Baseline HEAD `2edfa2b4ad82f07f6cebc4689c5681a2d3a954e3` (`feat: close accepted routing and conflict integration`). This package records existing accepted runs; no suite rerun occurred in O1.7.

## 1. Exact scope and implementation

The shared [kelios](../../kelios) `recovery()` helper originally prompted Login with `manual-admin`, applied generic identifier/minimum3 checks and passed retired `kelios.recovery.login`. The accepted [backend command](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/AdministratorRecoveryCommand.java) requires `kelios.recovery.email` and calls authoritative bootstrap. Both install and recover-admin use the same helper.

O1.2 changes exactly three lines: explicit GLOBAL ADMIN Email prompt with empty default, a narrow POSIX whitespace required-input case, and one quoted Email argument. Nonblank input is not trimmed, case-folded, aliased or regex-validated by the CLI. The backend remains authoritative for normalization, practical syntax/length, uniqueness and zero-usable-admin eligibility. No default account, Login fallback/compatibility, retry or force.

Display-name input/default/validation, password/no-echo/confirmation/exact bytes, temporary file path/mount, cleanup/traps, output sanitization, caller/startup ordering and prepared/initialized transitions are unchanged. Generic project/database identifier helper and its unrelated uses remain unchanged. No Email saved in installation state or credential file. No backend/frontend/API/schema/Compose modification.

O1.3 adds six install cases; O1.4 adds six recover-admin cases to [control tests](../../scripts/test-deployment-cli.py). Existing install/key fixture receives fictional Email. Scoped fake-Docker observation captures argv arrays, phase, password-file SHA256 and mode; no plaintext password. Existing fake outcomes/exported-secret guard and original assertions remain. No universal harness or production framework introduced.

O1.6 synchronizes only current [CLI guide](../DEPLOYMENT_CLI.md) Email/password/recovery instructions, [AUTHENTICATION](../AUTHENTICATION.md) obsolete handoff limitation, O1 status and the O1-owned old-plan cross-reference. Existing12–128 Unicode-code-point composition/Argon2id policy is described, not redesigned. Manually supplied bootstrap Email is verified by accepted backend behavior; no delivery/token infrastructure or real verification mail is added.

## 2. Accepted verification, exact commands and counts

### CLI controls — isolated PTY/fake Docker only

Focused runners imported the maintained module and selected named cases while overriding BASE to a new O1-owned private scratch checkout; they did not run historical fixture cleanup. They are ignored local run helpers, not installer dependencies.

| Step / command from repository root | Exact result | Boundary |
|---|---|---|
| O1.3 `python3 .cache/o1-step3/run-focused.py > .cache/o1-step3/focused.log 2>&1` | Initial sandbox attempt2 passed/5 failed | `stty` TIOCGETD Operation not permitted at password prompt; not a proven production defect |
| O1.3 same runner redirected to `.cache/o1-step3/focused-final.log`, outside sandbox |7 passed, failures/errors/skips0;20.308s; exit0 | Seven affected install cases, before later assertion strengthening |
| O1.3 `python3 .cache/o1-step3/run-final.py > .cache/o1-step3/final.log 2>&1`, outside sandbox |13 passed, failures/errors/skips0;32.525s; exit0 | Seven install + six related controls, strengthened assertions included |
| O1.4 `python3 .cache/o1-step4/run.py > .cache/o1-step4/focused.log 2>&1`, outside sandbox |8 passed, failures/errors/skips0;32.684s; exit0 | Six recover-admin + two O1.3 preservation cases |
| O1.5 `./scripts/test-deployment-cli.sh > .cache/o1-step5/control.log 2>&1`, outside sandbox | **Complete maintained suite35 passed; failures/errors/skips0;68.877s; exit0** | Shell syntax checks plus all current controls; no real Docker |

The complete control suite contains original23 plus six install and six recover-admin cases. Earlier7/13/8 are overlapping selections, not additional unique passed tests. Fake install setup inside recover-admin fixtures is setup/assertions, not another unittest count. The O1.5 wrapper's normal cleanup is confined to its dedicated disposable `.cache/deployment-cli/control` tree, not an operator installation.

Coverage proves: exactly one literal Email argv element plus unchanged non-web/enabled/display/password-file/mount options and no retired property; mixed case/padding/valid punctuation and no shell execution/splitting; no account default; empty/POSIX-whitespace rejection before dispatch; exact password bytes/hash/mode and no argv/environment/terminal leak; temporary cleanup/echo restoration; no Email state persistence; install build/up/run/up order, readiness only after recovery success and prepared state on refusal; recover-admin up/run with initialized files preserved and sanitized unsafe fake stdout/stderr; no replay; existing key/privacy/missing-image/missing-volume/other controls preserved.

### Backend — existing isolated PostgreSQL/Testcontainers tests

From `backend`, with Docker access for isolated Testcontainers:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B '-Dit.test=IdentityAuthenticationIT#passwordPolicyEveryCredentialPathAndChangeDoesNotRevoke+bootstrapRefusesUsableAdminButLaterRecoversAndAudits+malformedCredentialAdminDoesNotBlockRecoveryAndFailureRollsBack+concurrentRecoveryCommandsCreateExactlyOne+recoverySerializesWithAdminPromotion+pendingAndCredentiallessAdminsDoNotCountAsUsable+unverifiedAdminDoesNotBlockGuardedRecovery+emailCanonicalUniquenessAndEditPreservesIdentityPasswordAndCreationMetadata+globalLoginAndRefreshRequireVerificationApprovalAndActive+selfDisableDemotionRejectedWithoutLastAdminInvariant' test-compile failsafe:integration-test failsafe:verify > ../.cache/o1-step5/postgres.log 2>&1
```

**15 passed, failures0/errors0/skipped0; exit0; BUILD SUCCESS.** Suite16.534s, Maven18.482s. Fresh PostgreSQL17-alpine Testcontainer applies V1–V8; ephemeral test keys and existing HTTPS OIDC fixture are not real operator credentials/Provider. Selected [IdentityAuthenticationIT](../../backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java) methods:

```text
passwordPolicyEveryCredentialPathAndChangeDoesNotRevoke
bootstrapRefusesUsableAdminButLaterRecoversAndAudits
malformedCredentialAdminDoesNotBlockRecoveryAndFailureRollsBack
concurrentRecoveryCommandsCreateExactlyOne
recoverySerializesWithAdminPromotion
pendingAndCredentiallessAdminsDoNotCountAsUsable
unverifiedAdminDoesNotBlockGuardedRecovery
emailCanonicalUniquenessAndEditPreservesIdentityPasswordAndCreationMetadata
globalLoginAndRefreshRequireVerificationApprovalAndActive
selfDisableDemotionRejectedWithoutLastAdminInvariant
```

Nine single cases plus six ACTIVE/PENDING/DISABLED × verified/unverified validity cases. Failsafe XML was inspected for exactly15 outcomes and zero failures/errors/skips. Tests independently prove guarded eligibility, password policy, Email canonical uniqueness, verified/active interactions, metadata preservation, rollback/audit and concurrency/self-protection. No backend test/production changes. No Surefire/full backend/frontend suite rerun; historical802 backend result is not new O1 evidence. Control35 and PostgreSQL15 remain separate evidence categories, not a combined backend test total.

## 3. Explicit limitations and exclusions

The composed evidence is structural CLI argv/control proof plus existing service/PostgreSQL proof. It does **not** independently execute fresh real Docker install/non-web ApplicationRunner recovery end to end. Static required-property dispatch plus exact quoted argv and service tests exposed no insufficiency requiring a new command test; no AdministratorRecoveryCommandTest was added. Do not represent this as live/operator recovery or real Email validation performed by fake Docker.

No broad [historical verifier](../../scripts/verify-deployment-cli.py) or smoke modernization/rerun: it retains obsolete Login bodies/prompts, browser environment and V1–V6 assertions. Its historical [CLI report](DEPLOYMENT_CLI.md) remains unchanged; original23 control/35 Docker assertions/etc. are their own implementation-time evidence, not overwritten by O1 counts.

No real Email delivery/I2, Provider/OIDC, frontend integration, password/lifecycle redesign, deployment architecture, schema/reset, operator accounts/business data, extra command/framework, unrelated residual work or live recovery. All test state is isolated PTY/fake-Docker files or Testcontainers. Previous A/B/integration/I1 evidence and accepted limitations remain unchanged.

## 4. Exact O1-owned file/hunk manifest and proposed commit boundary

**Seven paths; five whole-file changes and two partial-document changes. No staging or commit authorized/performed.** Ownership is derived from O1 planning/step1–6 snapshots and accepted execution manifests, not merely current Git status.

| Path | O1 ownership to include after final acceptance and explicit commit authorization | Excluded content in same path |
|---|---|---|
| `kelios` | Whole HEAD diff: exactly three recovery-helper lines from O1.2 | None; pre-O1 file equals HEAD; production bytes unchanged since accepted verification |
| `scripts/test-deployment-cli.py` | Whole HEAD diff: directly affected Email install fixture, six install/six recover-admin cases, scoped capture/assertion support | None; pre-O1 file equals HEAD; old test bodies/behaviors preserved |
| `docs/AUTHENTICATION.md` | Whole HEAD diff: only obsolete CLI-handoff limitation replaced under O1.6 | None; pre-O1 file equals HEAD; all other authentication clauses untouched |
| `docs/DEPLOYMENT_CLI.md` | **Partial:** O1-owned current boundary; Email/no-default and15->12 First install substitutions; shared recovery/authority/no-delivery paragraph; recover-admin row | Pre-O1 general “Executable baseline operations…” authority paragraph/extra blank lines, and pre-existing replacement of “Existing backend password policy applies:” by “The implemented baseline backend password policy applies:” |
| `docs/plans/DEPLOYMENT_CLI_PLAN.md` | **Partial:** only O1 current execution cross-reference paragraph inserted at Step1 and updated at Step6 | Pre-O1 “Historical accepted implementation record…” authority preamble/extra blank lines; original CLI v1 body unchanged |
| `docs/plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md` | Entire new human-accepted O1 execution plan, actual per-step status/results/provenance and approved final-review updates | None; created by O1 planning, absent from HEAD |
| `docs/verification/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY.md` | Entire new final-review evidence package; current pending status until human decision | None |

### Reliable partial-document construction

Guide: take current accepted working text; remove only the pre-O1 general authority preamble and its owned extra blank lines; restore only the pre-existing wording change to `Existing backend password policy applies:`. Keep the O1 Email/default/policy/recovery/cross-reference changes. This preserves accepted operational meaning while leaving unrelated wording edits out of a future index. It is a **candidate index copy**, not an overwrite of current guide text.

Old CLI plan: remove only its pre-O1 historical-authority preamble/extra blank lines from a candidate copy; retain the O1 current-boundary paragraph. Original v1 design/evidence is neither rewritten nor staged as a new implementation.

Prior to O1, removing those exact known hunks reconstructs HEAD byte-for-byte in both documents. Current candidate copies are saved only in ignored `.cache/o1-step7/proposed-content`; the worktree remains unchanged. Do not whole-file stage either mixed-provenance document. Future stage-review must compare actual index content to newly approved candidates, not assume the present full worktree diff is owned.

**Commit-document dependency requiring human disposition:** the accepted O1 plan links `docs/analysis/REMAINING_BACKLOG_AFTER_INTEGRATION_CLOSEOUT.md`. That existing audit is untracked and explicitly outside O1 ownership; it is not present in HEAD or this seven-path proposal. Its link resolves in the current worktree, but would not resolve in an O1-only checkout. Before any commit, the human must separately approve committing that audit (for example as a separate prerequisite documentation commit), or authorize a narrow reference adjustment. Do not add it to O1 implicitly, stage it, or alter the accepted reference in this evidence-only gate. The seven O1 paths/hunks are exact; the proposed commit remains conditional on this documentation dependency and final acceptance/commit authorization.

The eventual commit needs separate explicit authorization after final human acceptance, including any necessary approved acceptance/status-note amendments. Recompute candidates/manifest at that time; current pending notes are not a final accepted commit package. No code adaptation for commit boundaries. If new changes make reliable separation impossible, STOP before staging/commit.

### Everything left out

- `docs/INVENTORY.md`: all existing dirty authority/routing/list-description hunks are unrelated.
- `docs/analysis/REMAINING_BACKLOG_AFTER_INTEGRATION_CLOSEOUT.md`: prior uncommitted backlog audit, not an O1 implementation/evidence file.
- `.idea/`: local IDE content.
- `docs/verification/merge-combobox-phase-b/`, `phase-b1/` through `phase-b4/`, `sidebar-phase-a/` through `sidebar-phase-a4/`: historical artifacts, not O1.
- General dirty guide/old-plan hunks identified above, all private/runtime/bootstrap files, `.cache/`, certificates/keys/passwords/tokens/dumps, generated build/test reports, actual Docker/installation state and all residual/future gates.

No migration/API/frontend/backend production/test/OpenAPI or historical-report file belongs to the O1 commit. No new executed content in closeout warrants a suite rerun.

## 5. O1.7 closeout checks and preservation

Only O1.7 repository changes: this new report and O1 execution plan acceptance/status/evidence record. O1.6 human acceptance is recorded centrally; other documents retain their O1.6 execution-time pending note because this gate authorizes only report/plan updates. Human final acceptance/commit authorization can separately synchronize those scoped status notes without rewriting implementation evidence.

Checks performed: documentation/source relative links and changed/new-file whitespace; `git diff --check`; complete non-ignored pre-O1.7 SHA256 comparison; accepted source/tests against O1.5 baseline; original CLI/I1/history bytes; proposed seven-path/hunk manifest/patch against source snapshots and HEAD; private/generated/excluded-path scan; empty index. Exact results are reported on gate completion. No accepted suites rerun; no private contents read/copied into this report.

Final review state: O1.1–O1.6 HUMAN ACCEPTED / CLOSED; O1.7 IMPLEMENTED / PENDING HUMAN ACCEPTANCE. No staging, commit, push, PR, deploy/reset, live recovery/data changes or next gate. STOP for final human review.


### Final closeout results

PASS: exactly two O1.7 repository changes (report and plan); all other pre-O1.7 non-ignored bytes preserved, including accepted source/tests against O1.5 verification snapshot. PASS:41 proposed-content relative link occurrences resolve in the current worktree, changed-document whitespace, `git diff --check`, empty index and seven-path scope/private/generated-material inspection. **Conditional:** the single excluded untracked backlog-audit link dependency prevents claiming a self-contained O1-only checkout; human disposition is required before committing. Partial-document candidate construction excludes exactly the recorded unrelated preambles/guide wording and does not replace current files.

Review-only seven-path manifest and owned patch: ignored `.cache/o1-step7/proposed-manifest.txt` and `proposed-owned.patch`; they are not commit files. No accepted suite rerun, staging or commit.

Final `git status --short`:

```text
 M docs/AUTHENTICATION.md
 M docs/DEPLOYMENT_CLI.md
 M docs/INVENTORY.md
 M docs/plans/DEPLOYMENT_CLI_PLAN.md
 M kelios
 M scripts/test-deployment-cli.py
?? .idea/
?? docs/analysis/REMAINING_BACKLOG_AFTER_INTEGRATION_CLOSEOUT.md
?? docs/plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md
?? docs/verification/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY.md
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

## Final human acceptance and commit authorization — 2026-10-14

O1.7 and overall O1 are **HUMAN ACCEPTED / CLOSED**. The human authorized only the seven-path O1 commit with the partial-document ownership recorded above. The former documentation dependency is resolved by accepted backlog-audit prerequisite commit `24f24f3539a4b0d84d1d892f0fa3678cc65392b3`. All preceding execution-time statuses, verification counts, failures and limitations remain historical evidence. No suites or live recovery were rerun for closeout.
