# O1 — Deployment CLI / I1 Email bootstrap-recovery handoff

Date: 2026-10-13. **Execution plan HUMAN REVIEWED / ACCEPTED, 2026-10-13. O1.1 HUMAN ACCEPTED / CLOSED. O1.2 HUMAN ACCEPTED / CLOSED. O1.3 HUMAN ACCEPTED / CLOSED. O1.4 HUMAN ACCEPTED / CLOSED. O1.5 HUMAN ACCEPTED / CLOSED. O1.6 HUMAN ACCEPTED / CLOSED. O1.7 HUMAN ACCEPTED / CLOSED. Overall O1 HUMAN ACCEPTED / CLOSED; the seven-path O1 closeout commit is explicitly authorized.**

This is the bounded O1 execution plan. Human approval of this plan does not authorize any implementation step, runtime action or commit. Each step below requires explicit authorization; after its evidence is recorded, stop. Accepted backend/I1 and frontend integration remain CLOSED.

## 1. Purpose, authority and baseline

Baseline HEAD: `2edfa2b4ad82f07f6cebc4689c5681a2d3a954e3`, `feat: close accepted routing and conflict integration`. The [remaining-backlog audit](../analysis/REMAINING_BACKLOG_AFTER_INTEGRATION_CLOSEOUT.md) establishes O1 as a confirmed CLI caller mismatch. This plan independently reconciles source and the accepted recovery contract; it does not adopt older login requirements.

Authority: [ARCHITECTURE](../../ARCHITECTURE.md), [DOMAIN](../../DOMAIN.md) §2 and the accepted I1/backend recovery implementation; [AUTHENTICATION](../AUTHENTICATION.md) first-installation/zero-admin recovery and implemented Email sections. [I1 evidence](../verification/I1_GLOBAL_EMAIL_IDENTITY.md) is historical verification, not a competing identifier design. [Backend execution plan](COLLECTION_QUERY_IMPLEMENTATION_PLAN.md) records I1 acceptance. [Integration plan](FRONTEND_BACKEND_INTEGRATION_PLAN.md) closes the accepted application work; O1 is not another F gate.

For this narrow CLI handoff, this human-accepted plan specifies the current execution boundary. The [original CLI plan](DEPLOYMENT_CLI_PLAN.md) remains historical CLI v1 design/implementation context. The [CLI guide](../DEPLOYMENT_CLI.md) is the operator-facing surface to synchronize at the appropriate step. [Original CLI verification](../verification/DEPLOYMENT_CLI.md) retains its original commands, counts, pending-review status and limits; never rewrite it to imply Email recovery was implemented then. Do not infer human approval from a commit alone.

No existing plan, guide, evidence or application file was edited during the original planning phase. A separate document avoids adopting or overwriting the three existing dirty documents. Its normative target is now human-accepted; it never claims the current CLI already implements the target. O1.1 adds only the narrow current-boundary cross-references recorded below.

## 2. Independently confirmed source/contract reconciliation

| Boundary | Actual current source / behavior | Consequence / smallest delta |
|---|---|---|
| CLI input | [kelios](../../kelios) `recovery()`197–203 prompts `GLOBAL ADMIN login`, defaults `manual-admin`, runs generic `identifier` plus minimum3 | Retired input is not Email. `identifier`55 excludes `@` and limits64. Do not change the shared helper used for project/DB names; remove only its recovery use and the retired login default/minimum. |
| CLI handoff | `recovery()`208 passes quoted `--kelios.recovery.login=$LOGIN` | Replace this argument with quoted `--kelios.recovery.email=<operator Email>`. No simultaneous old property/alternative identifier. |
| Backend entry point | [AdministratorRecoveryCommand](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/AdministratorRecoveryCommand.java) `run`15–20 requires non-web mode, bounded password file, `kelios.recovery.email` and display name; calls bootstrap | Old CLI cannot supply the required property. No backend compatibility change is necessary or permitted. |
| Authoritative application behavior | [AuthenticationService](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/AuthenticationService.java) `bootstrap`20–29 canonicalizes Email, validates/hashes password, locks recovery guard, checks usable verified ACTIVE GLOBAL ADMIN, then atomically inserts User/credential/audit | Preserve exactly; CLI passes input, does not calculate authority/usability or invent provenance. Failure remains refusal/failure; no force or fallback. |
| Email/password policy | [Passwords](../../backend/src/main/java/com/github/ksewen/kelios/identity/auth/Passwords.java) `email`, `validate`, `hash` | Practical ASCII dot-atom/dotted domain, outer whitespace stripping/case-insensitive canonical Email;12–128 Unicode code points with composition, Argon2id. Backend alone remains authoritative. Old guide15 minimum is stale description, not a policy to implement. |
| Install caller | `install()`287: `start_runtime; recovery; start_runtime`, then ready URL | Shared helper is the only identifier/handoff delta. Preserve build, TLS, migration, readiness, private state, project ownership, health and ready-message ordering. |
| Explicit recovery caller | dispatch356: terminal check, `start_runtime; recovery` | Same corrected helper. Preserve existing installation validation/locking and runtime availability checks; no second lifecycle. |
| State/cleanup | recovery204–213 and cleanup16–22: exact password file, private mount, no echoed password, trap cleanup; successful prepared state becomes initialized only after backend success | Preserve bytes/no newline, mount path, output suppression, cleanup and failure state; no Email/password persisted in installation manifest. |

This is a static confirmed incompatibility, not a live reproduction. No install/recovery command or test suite ran in this planning task.

## 3. Human-accepted precise CLI target

### Input, prompt, default and validation

- Use `GLOBAL ADMIN Email`. **No default account identifier**: the existing prompt helper may receive an empty default; require explicit operator input. Do not suggest `manual-admin`, silently append a domain, guess Email from displayName or offer Login mode.
- Display-name prompt/default and its existing validation stay unchanged.
- Reject empty/all-whitespace Email before recovery dispatch with a concise Email-required message. Use only narrow local input checks; no Email regex/canonicalization framework. Full syntax, normalized length254/local-part64, case-insensitive comparison and uniqueness remain backend validation. Do not run the project/DB `identifier` validator on Email.
- Forward nonblank Email as a single quoted argument, without local case-folding, aliasing or shell evaluation. Backend strips surrounding whitespace and canonicalizes. Tests must include mixed case, outer spaces and valid dot-atom punctuation; no shell metacharacter may become executable or split argv. Backend-invalid input must fail safely, not be “repaired” by the CLI. Basic CLI validation is convenience, not the authority boundary.
- Prompt password and confirmation through the existing no-echo path. Preserve exact bytes, including intentional surrounding password spaces, no incidental newline, matching confirmation and cleanup on cancellation/failure. No new shell password policy; explain the accepted12–128 composition rule in current operator documentation.
- Email is the operator-selected identifier; do not persist it as installation configuration or invent a credential output file. Password/token/key material must never enter argv, ordinary environment, source, public config, logs or evidence. Test fixtures may contain clearly fictional Emails, not operator credentials.

### Exact non-web handoff

Retain these existing arguments and mount semantics, changing only the identifier property:

```text
--spring.main.web-application-type=none
--kelios.recovery.enabled=true
--kelios.recovery.email=<explicit operator input; one quoted argv element>
--kelios.recovery.display-name=<existing display-name input; one quoted argv element>
--kelios.recovery.password-file=/run/secrets/kelios-recovery/password
```

The password is a temporary mounted file, not an argument value. `kelios.recovery.login` is absent, not accepted as compatibility. Generic safe failure messaging remains sanitized; do not dump raw bootstrap output to diagnose invalid Email/password or existing-admin refusal.

### Domain outcome and caller lifecycle

On eligible zero-usable-admin recovery the backend creates exactly one GLOBAL/ADMIN/ACTIVE identity, manually verified Email credential and `GLOBAL_ADMIN_BOOTSTRAPPED` AuditLog atomically. Creation metadata uses that authoritative real bootstrap actor, not seed attribution. The CLI never writes SQL or changes role/status/verification itself.

Usable verified administrator present: backend refusal; no new User/credential/audit and no forced overwrite. Invalid input, failure, interruption: no success/ready claim, transient recovery material removed, existing data/configuration/keys retained. Prepared installation stays prepared on failed recovery; initialized installation does not lose its state on refusal. The existing `start` command never bootstraps. No automatic replay of recovery after refusal/uncertain result.

## 4. Existing dirty-document hunk provenance and future edit boundary

Provenance here means observable pre-task diff, not invented authorship/acceptance. At HEAD the documents already existed; the following working-tree changes were present **before O1 planning** and remain byte-identical. A hash snapshot and exact diff are in ignored local O1 planning scratch.

| Dirty document / current diff | Future O1 need | Permitted narrow future boundary; what stays outside |
|---|---|---|
| `docs/DEPLOYMENT_CLI.md`: added “Executable baseline operations…” authority paragraph at top; First install paragraph adds “implemented baseline” but retains GLOBAL ADMIN login and15–128 | Yes, current operator instructions must match corrected Email helper | Step1 may append a narrowly identified current O1 mismatch/target cross-reference without changing existing hunks. Step6 replaces only the First install login/password description with actual Email/current backend policy and explains same helper for recover-admin. These edits intersect the already modified paragraph: record exact before/after hunk and obtain hunk-level ownership before later staging. Do not silently adopt the unrelated top paragraph or normalize blank lines/whole file. |
| `docs/plans/DEPLOYMENT_CLI_PLAN.md`: only new historical-authority preamble, blank lines at top | At most a current O1 execution cross-reference/status outside the historical design | Preserve that existing hunk exactly; leave original design/sequence/history unchanged. A new scoped cross-reference to this plan is sufficient if separately authorized. Do not convert the entire historical plan to current implementation instructions. |
| `docs/INVENTORY.md`: added baseline-authority preamble; B.3 routing sentence replaces “No deployment routing change”; Asset25 paragraph labeled implemented baseline | **No O1 edit needed** | All hunks are unrelated to recovery handoff; preserve byte-for-byte and exclude from implementation/commit manifest. |

Other source documents are read-only unless a listed step explicitly permits a narrow update. AUTHENTICATION currently describes the correct Email command; Step6 may replace only its “CLI still needs…old login” limitation after implementation evidence exists, with a link to new O1 evidence/current status. Do not synchronize unrelated routing/status/Tenant wording in this gate.

`.idea/`, historical Phase A/B evidence directories and the uncommitted backlog audit stay outside O1. Do not infer ownership from `git status`; future commit must distinguish approved O1 hunks from pre-existing document hunks even when they share a file. If a clean accepted staging boundary cannot be established without modifying unrelated content, STOP and ask for ownership/commit direction; never overwrite/reset/stash work to manufacture it.

## 5. Test inventory, known blockers to old tooling, proposed evidence

### Existing relevant tests

- [Control tests](../../scripts/test-deployment-cli.py): PTY conversations, fake Docker and copied checkout/private state, no operator runtime. Fake `run` currently only succeeds/refuses; it does **not** validate recovery argv or actual backend Email behavior. The supplied-versioned-key install fixture originally supplied `GLOBAL ADMIN login/control-admin`; O1.3 now uses fictional Email and full-element argv assertions while preserving its existing key/privacy assertions.
- [Control wrapper](../../scripts/test-deployment-cli.sh): shell syntax plus Python control tests. No backend/Compose deployment. Keep wrapper unchanged unless a focused invocation genuinely requires a narrow adjustment.
- [IdentityAuthenticationIT](../../backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java): existing password-policy-all-writers, bootstrap refusal/recovery/audit, malformed credential rollback, concurrent recovery/promotion, unverified-admin recovery and Email canonical uniqueness coverage. These use isolated PostgreSQL Testcontainers. They prove service/database behavior, not that the CLI transmitted the command correctly.
- Static backend entry-point inspection confirms required Email property; there is no direct `AdministratorRecoveryCommand` test found in current test sources. If source+argv tests do not adequately prove the actual non-web boundary, Step5 may add **only a focused command test** (candidate `backend/src/test/java/com/github/ksewen/kelios/identity/auth/AdministratorRecoveryCommandTest.java`) for required-property/file/non-web dispatch, with no backend production changes. Human approval of Step5 must explicitly include that test file before adding it. A mock command test cannot be represented as PostgreSQL proof.

### Broad historical verifier — do not run/modernize it as O1

[verify-deployment-cli.py](../../scripts/verify-deployment-cli.py) uses old GLOBAL ADMIN login prompts, `{login:...}` local-login/create-user bodies, `KELIOS_TEST_LOGIN` while current browser test expects `KELIOS_TEST_EMAIL`, and a fixed V1–V6 schema assertion. It also exercises branding/network/uninstall/external-database lifecycle and account mutations. Replacing only prompt labels would still leave it incompatible.

Those broader obsolete assumptions are **not a production dependency** of the small CLI correction. Do not run this script against operator state or expand O1 into full real-write/browser/deployment modernization. Leave it and old smoke-phase2/3/4 login callers untouched; explicitly record that they remain uncertified historical tooling. O1 instead uses focused control tests plus affected isolated backend recovery tests. If human requires full actual Docker/non-web CLI proof, authorize a separate narrowly scoped fresh disposable fixture verification step and its exact file/runtime boundary; do not silently introduce a new large verifier or reuse old fixture installations. Until then, report that full Docker install interoperability was not newly executed.

### Required focused control coverage after authorization

1. Install and recover-admin independently produce exactly one recovery invocation containing only the quoted Email property, existing display/non-web/password-file options and no retired login property; assert full argv, not shell-string substring matching.
2. No default account: empty/all-whitespace refuses before dispatch. Valid mixed-case/padded Email and permitted punctuation survive argv exactly; backend remains normalizer/validator. Invalid syntax/over-bound backend-refusal outcome stays safe; fake Docker must not be described as authentic Email validation.
3. Password bytes/file path/mount unchanged; no plaintext in argv/environment/terminal output; Email not written into saved installation fields; display-name path unchanged.
4. Successful install reports readiness only after recovery success, moves prepared -> initialized and retains normal startup/build ordering. Failed/invalid/refused recovery never reports a ready installation or marks prepared initialized.
5. Recover-admin succeeds in permitted fixture, refuses when fake backend reports usable-admin refusal; no retry/second invocation. Existing initialized manifest, key/volume ownership and guard-related behavior are preserved. Authenticated backend eligibility tested independently in PostgreSQL.
6. Mismatched password/interrupt restores echo, removes temporary password/output and lock as existing cleanup requires; backend invocation never occurs before confirmation. Success/refusal also leave no recovery temporary directory.
7. Existing no-install/overwrite, missing-image/volume, symlink, privacy and unrelated command controls continue passing. Their existing semantics may not be weakened to obtain green results.

## 6. Separately authorizable implementation steps

O1.1 is **HUMAN ACCEPTED / CLOSED**. O1.2 is **HUMAN ACCEPTED / CLOSED**. O1.3 is **HUMAN ACCEPTED / CLOSED**. O1.4 is **HUMAN ACCEPTED / CLOSED**. O1.5 is **HUMAN ACCEPTED / CLOSED**. O1.6 is **HUMAN ACCEPTED / CLOSED**. O1.7 is **HUMAN ACCEPTED / CLOSED**. Overall O1 is **HUMAN ACCEPTED / CLOSED**; the seven-path closeout commit is explicitly authorized. Plan acceptance does not authorize executable implementation. Human may explicitly authorize a small group later, but authorization must name the scope and each boundary remains independently reviewable.

| Step | Objective / intended files | Prerequisite | Acceptance / evidence / STOP |
|---|---|---|---|
| O1.1 Contract/source reconciliation and authoritative doc boundary — HUMAN ACCEPTED / CLOSED | Reconfirm this plan against current CLI/backend, record no broadened dependency; if authorized add narrowly scoped plan/guide cross-references distinguishing current defect from approved target. No code. | Human accepts planning deliverable and explicitly authorizes Step1 | Same narrow Email handoff identified; dirty-hunk provenance recorded; future input/default/validation agreed; no contradictory current handoff instruction; planning/documentation changes reviewed. Stop for approval before code. |
| O1.2 CLI input/handoff correction — HUMAN ACCEPTED / CLOSED | `kelios` recovery helper only: Email prompt/no default/basic required-input check, remove recovery identifier/minimum3, quoted required property. Generic identifier helper remains for unrelated names. | O1.1 accepted; explicit Step2 authorization | Review exact resource-local delta; no backend/shared runtime change, no Login fallback; password/display/cleanup unchanged. Run shell syntax/static argument checks; mark implemented pending review, stop. |
| O1.3 Install-path integration — HUMAN ACCEPTED / CLOSED | Directly affected install PTY fixture/assertions in `scripts/test-deployment-cli.py`; helper already used by install, no new branch | Accepted helper or explicit grouped authorization | Prove success/failure ordering and prepared-state preservation, no accounts/business/runtime claims from fake Docker. Do not change installer build/TLS/migrations/ownership. Stop. |
| O1.4 Recover-admin integration — HUMAN ACCEPTED / CLOSED | Directly affected recover-admin PTY cases in same control test file; verify saved installation flow calls same helper | Accepted helper; Step3 is not a technical blocker if independently authorized | Prove exact Email argv, sanitized refusal, no replay, saved state/privacy/cleanup. No force/reset/update command or alternate lifecycle. Stop. |
| O1.5 Focused automated verification — HUMAN ACCEPTED / CLOSED | Run scoped control tests and full existing control suite; affected isolated PostgreSQL recovery/Email cases. Command-boundary test only if explicitly included by human; no broad historical verifier | Both caller slices implemented; explicit test/runtime scope | Exact counts/exits and final tests, failure attempts/exclusions recorded separately; arguments/control evidence distinct from service/PostgreSQL/Docker evidence; no operator target. Stop for review. |
| O1.6 Operational docs synchronization — HUMAN ACCEPTED / CLOSED | Narrow First install/recover-admin guide paragraph, this plan status/evidence; narrow AUTHENTICATION CLI-handoff limitation; optional scoped historical-plan link | Verified behavior; explicit doc scope/dirty-hunk ownership | Current docs describe Email and12–128 existing backend policy accurately, no claim of real mail; original CLI evidence preserved; no unrelated dirty hunks adopted. Stop. |
| O1.7 Evidence, human acceptance and separately authorized commit — HUMAN ACCEPTED / CLOSED (closeout commit explicitly authorized) | New `docs/verification/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY.md`; this plan status, exact owned file/hunk manifest and limitations | Verification/docs completed; human review | Initially IMPLEMENTED / PENDING HUMAN ACCEPTANCE. Only explicit human decision closes O1; commit requires separate explicit authorization after acceptance and exact staged-manifest checks. Stop; never advance another backlog item. |

Steps3 and4 share the helper but test different callers; neither demands a production caller change if the shared helper already suffices. Steps1/6 separate approved target/source alignment from truthful post-implementation operator instructions. No step may be silently skipped, merged into a rewrite or auto-started.

## 7. Verification ladder for later authorized steps

Planning now: documentation/source inspection, relative links, whitespace/diff and preservation only; no scripts/tests/recovery commands executed.

Later implementation:

- `sh -n kelios`; run directly affected Python unittest cases, then `./scripts/test-deployment-cli.sh` for complete control preservation. Use copied, clearly isolated fixtures under a new O1-owned ignored location or per-run target; refuse/review collisions, do not erase/adopt earlier accepted `.cache/deployment-cli` fixtures. Test-target ownership changes must be scoped to directly affected harness use, not framework refactor.
- From `backend`, the repository Maven3 wrapper may run a focused selected `IdentityAuthenticationIT` method set with `test-compile failsafe:integration-test failsafe:verify` under existing isolated Testcontainers discipline. Include bootstrap refusal/audit/rollback/concurrent guard/unverified admin, password-policy and Email canonical tests needed for this handoff. Record actual selected names/counts; no speculative counts or reuse of historical802 as a fresh result.
- If a focused command unit is explicitly approved, run it plus affected units. Full backend verification is required only if an approved backend-test change warrants it or new evidence identifies a broader preservation concern. Backend production changes are a STOP condition, not implicit permission for a full repair.
- Full historical Docker verifier, frontend browser/build suites, live bootstrap/recovery/operator database are not required for this CLI-only delta and remain excluded. If actual Docker handoff evidence is separately requested, use a fresh isolated database/project, current maintained images/contracts and private fixture inputs; no manual project adoption, business-domain seeding or reset. Do not present mocked Compose argv as successful live recovery.
- `git diff --check`, touched-document relative links/new-file whitespace; pre-step file/hunk hashes and final diff; evidence records commands/exits/counts, failed attempts, environment and missing actual Docker/operator evidence honestly.
- Human checkpoint after each authorized slice; aggregate final acceptance only after all required slices/limits are reviewed. No commit until explicitly requested.

## 8. Dependencies, non-dependencies and STOP rules

Required contract dependencies I1, M1 metadata/actor ownership, zero-admin guard and accepted transactions/audit are already CLOSED. No new Domain/identity decision is necessary to repair the caller. The no-default/basic-input policy and test/doc boundaries are approved through human acceptance of this plan; each implementation step still requires separate authorization. Shell/OpenSSL/Python are current CLI/test tooling, not new installer dependencies.

O1 does not depend on Product wrapping, Phase C/D, table filtering, real IServ, Email delivery, export, CI or future lifecycle policy. B1/B2/application integration are accepted and not reopened. Lack of operator credentials is not a blocker for isolated control/PG verification and never permits inventing an account. General excluded real-write-suite modernization remains separate; its old verifier is not used as a required O1 test.

STOP if source contracts differ at execution, backend change appears necessary, command-body copying/reflection/framework or schema/reset is proposed, a test needs operator/manual data, broad old-verifier modernization becomes necessary for requested evidence, a shared helper change would alter unrelated fields, or accepted/unrelated dirty hunks cannot be separated safely. Report the precise affected step, not weaken the contract or silently add compatibility.

Hard exclusions: backend support for retired login property/dual identity; I2/Email delivery; password/lifecycle redesign; new CLI commands/framework; Provider/OIDC changes; deployment architecture overhaul; schema/migrations/data reset; live recovery to manufacture evidence; frontend changes; O2–O6/BQ1–BQ3/T1–T9 implementation; unrelated test/doc cleanup. No normalization of dirty documents, historical evidence rewriting, IDE/artifact staging, destructive Git, push/PR/deploy.

## 9. Status/evidence and commit protocol

Future session: read this plan and relevant accepted source; confirm authorized step/prerequisites and current dirty state; snapshot owned/unrelated content; implement only that step; verify; inspect diff; update actual status/evidence; mark IMPLEMENTED / PENDING HUMAN ACCEPTANCE; STOP. No implementation permission is inferred from plan approval, passing tests or elapsed time.

Proposed future owned files: `kelios`; directly affected `scripts/test-deployment-cli.py` (wrapper only if necessary); this plan; new O1 evidence; scoped `docs/DEPLOYMENT_CLI.md` and AUTHENTICATION handoff paragraph; optional old-plan cross-reference. A focused backend command test is conditional on explicit Step5 approval. No production Java/OpenAPI/migrations or frontend files expected. Old `scripts/verify-deployment-cli.py`, phase smoke scripts, `docs/INVENTORY.md`, `.idea/` and prior verification reports remain excluded.

Evidence must enumerate actual file/hunk ownership, control vs PostgreSQL vs real Docker/runtime checks, original/new results and limits, exact install/recover-admin behavior and preservation. No actual passwords/tokens/private keys/Email operator identities in repository evidence. Fixture Emails must clearly be fictional.

After human acceptance, synchronize only current statuses. A separate commit authorization must name O1 scope: derive manifest from actual evidence, review overlapping dirty guide hunks, stage only accepted owned content, inspect staged diff/manifest and run `git diff --cached --check` plus link/private-material checks. If mixed provenance prevents reliable separation, STOP without commit; never change production code for a commit boundary. Verify empty index, exact commit manifest and unrelated hashes afterwards. No push/PR, subsequent gate or automatic commit.

## 10. Original planning deliverable and preservation — historical

This planning task creates only `docs/plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md`. Existing guide/plan/inventory hunks and all implementation/tests/evidence are preserved exactly, including the uncommitted remaining-backlog audit. No executable content changed; no tests/build/deployment/recovery/data operation performed. Planning checks and final repository status are reported to the human; plan approval/first-step authorization remain pending.


## 11. O1.1 execution record — 2026-10-13

**IMPLEMENTED / PENDING HUMAN ACCEPTANCE.** The plan was explicitly HUMAN REVIEWED / ACCEPTED; only O1.1 was authorized. Immediate source reconciliation reconfirmed the shared CLI Login/default/identifier/property mismatch, both callers, backend required Email and accepted AUTHENTICATION Email/password/recovery rules. No changed contract or broader dependency was found. Explicit GLOBAL ADMIN Email/no account default/basic required-input checking/backend authority/only Email property are approved targets, not present CLI behavior.

Exact O1.1-owned manifest:

- `docs/plans/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY_PLAN.md`: planning acceptance, O1.1 status/history clarification and this execution record; gate scope unchanged.
- `docs/DEPLOYMENT_CLI.md`: one inserted current O1 boundary paragraph linking this plan and truthfully identifying the current mismatch; existing operational paragraphs unchanged.
- `docs/plans/DEPLOYMENT_CLI_PLAN.md`: one inserted current O1 cross-reference distinguishing accepted narrow target from historical CLI v1 design/evidence; historical body unchanged.

Pre-existing provenance: guide authority-preamble and “implemented baseline” First install wording, old-plan historical preamble, and all INVENTORY hunks predate O1.1. Removing only the two new inserted paragraphs reconstructs each prior document byte-for-byte, including its dirty hunks/blank lines. INVENTORY and every other pre-existing file, executable/test/runtime configuration and historical evidence remain byte-identical to the pre-O1.1 snapshot. Shared Git diff context does not assign ownership of adjacent pre-existing hunks to O1.1.

Documentation links/whitespace, `git diff --check`, exact three-file delta, prior-content reconstruction, unrelated-file preservation and empty-index checks performed; exact results reported with completion. No install/recover-admin/bootstrap, tests/build, data/runtime change, staging/commit or next step. O1.6 operational paragraph synchronization has not occurred. STOP for human O1.1 acceptance; O1.2 not started or authorized.


## 12. O1.1 human acceptance and O1.2 execution — 2026-10-13

O1.1 **HUMAN ACCEPTED / CLOSED** by explicit human decision; its documentation boundary, source reconciliation, dirty-hunk provenance and preservation evidence are accepted. Original execution record above remains historical. O1.2 alone was subsequently explicitly authorized.

O1.2 **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**. Exactly two task-owned files: `kelios` (shared recovery helper only) and this plan (minimal acceptance/status/execution record). Email prompt has an empty default; the narrow POSIX whitespace predicate rejects empty/all-whitespace input before display/password prompts or backend dispatch. Nonblank input is unmodified and passed in exactly one quoted `--kelios.recovery.email=$RECOVERY_EMAIL` argv element. The recovery Login property/default/minimum and generic identifier validation call are removed. Backend still owns normalization, syntax/length, uniqueness and recovery eligibility.

The generic identifier helper and all its other calls are unchanged. Reverse only the two input lines and one argument replacement to recover the pre-O1.2 script bytes exactly. Display-name, password bytes/confirmation/no-echo, temporary path/mount, cleanup/traps, output sanitization, success/refusal, prepared/initialized state and both callers are unchanged. No Email persistence or fallback/retry/force behavior added.

Verification for this step only: `sh -n kelios`; static helper/argv/retired-property inspection, reversible three-line delta and unchanged generic helper/calls/outside-helper content; documentation links/new-file whitespace; `git diff --check`; exact two-file SHA256 ownership and prior dirty-document diff preservation; empty-index check. Results reported on completion. No CLI invocation, backend/frontend suite, Docker/PostgreSQL recovery, runtime/data or operator operation executed.

Known intentional test incompatibility: `scripts/test-deployment-cli.py` still supplies GLOBAL ADMIN Login/control-admin in its install fixture; broad historical verifier also retains Login/API/migration assumptions. No test changed or suite run to work around these. Caller regression fixture correction belongs to separately authorized O1.3/O1.4; broad verifier remains excluded.

Guide/AUTHENTICATION and all other documentation/evidence are unchanged. O1.3–O1.7 remain NOT AUTHORIZED, including O1.6 operator-instruction synchronization. STOP for human O1.2 acceptance; no staging, commit or next step.


## 13. O1.2 human acceptance and O1.3 install regression — 2026-10-13

O1.2 **HUMAN ACCEPTED / CLOSED** by explicit human decision; its exact three-line production delta, preservation checks and static-only verification limitations are accepted. O1.3 alone was subsequently authorized. O1.1 remains HUMAN ACCEPTED / CLOSED.

O1.3 **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**. Exactly two task-owned files: `scripts/test-deployment-cli.py` and this plan. No production delta. Existing install/key fixture now supplies fictional Email; extracted only its TLS/supplied-key answers for reuse. Existing key/privacy/permission/cleanup assertions remain. Six new install tests plus full-element argv/order/state/password/privacy assertions cover this gate; no recover-admin-specific case added.

Strictly necessary shared support: a test-only fake-Docker argv recorder writes JSON arrays, current fixture phase and password-file SHA256/mode (no plaintext password) to its own isolated checkout. Existing fake command outcomes and exported-secret guard remain unchanged. No shell joining/evaluation, real Docker, configuration framework or conversation/PTY behavior change. Exact recovery suffix contains one Email element, unchanged non-web/enabled/display/password-file/mount elements and no Login property. Tests prove mixed case/padding/punctuation remain literal, shell-execution sentinel is absent, blank/POSIX-whitespace fails before dispatch, password bytes/mode/mount remain intact, state contains no Email spelling, build/up/run/up ordering, and refused recovery leaves prepared/no-ready state with no replay.

### Executed focused verification

Commands from repository root (ignored runner scripts only select existing/new cases, override BASE to a fresh O1-owned private fixture directory and do not execute the historical __main__ fixture deletion):

- `python3 .cache/o1-step3/run-focused.py > .cache/o1-step3/focused.log 2>&1`: initial sandbox attempt2 passed/5 failed, because `stty` returned TIOCGETD Operation not permitted at the password prompt. No production defect established; terminal-access rerun required.
- `python3 .cache/o1-step3/run-focused.py > .cache/o1-step3/focused-final.log 2>&1` outside sandbox: **7 passed**, zero failures/errors/skips,20.308s.
- After strengthening persisted-Email/password-argv and ready-message-order assertions, `python3 .cache/o1-step3/run-final.py > .cache/o1-step3/final.log 2>&1` outside sandbox: **13 passed**, zero failures/errors/skips,32.525s. Seven focused cases included, not added to13. Six extra related controls verify copied-key install reuse/start preservation and unchanged fake-harness pre-recovery behavior; not the complete29-case control suite.
- `sh -n kelios`: PASS. AST parsing and unittest discovery: PASS,29 available cases; discovery did not execute them.
- Exact diff/ownership/production-byte preservation, prior unrelated document/content hashes, relative plan links/new-file whitespace and `git diff --check`: checked at completion; index empty.

Exact final selected test names:

```text
test_supplied_versioned_keys_are_preserved
test_install_email_preserves_case_spaces_and_password_bytes
test_install_email_punctuation_is_one_literal_argument
test_install_email_empty_has_no_default
test_install_email_posix_whitespace_rejected_before_dispatch
test_install_refused_recovery_never_reports_ready
test_install_password_mismatch_never_dispatches_recovery
test_initialized_missing_volume_refuses_empty_replacement
test_start_missing_image_does_not_pull
test_unknown_command
test_install_refuses_existing
test_password_not_echoed
test_interrupt_password_restores_echo
```

Fake-Docker evidence proves CLI argv/control flow and isolated file/state/privacy behavior only; it does not prove backend Email syntax/normalization/eligibility, successful real recovery, PostgreSQL/audit transactions or a working deployed installation. No real Docker/backend/Provider/operator data used. Only disposable PTY/fake-Docker checkout files and locally generated test TLS/crypto material under ignored private O1 scratch were created. Existing prior fixture installations were not overwritten or removed.

Accepted O1.2 `kelios` hash and every other unrelated pre-step file remain unchanged. No backend/frontend, Compose, operator guide/AUTHENTICATION/INVENTORY, old verifier/smoke or historical evidence change. Complete controls/PG verification stays O1.5. O1.4–O1.7 remain NOT AUTHORIZED. No stage/commit/deploy or next step. STOP for human O1.3 acceptance.


## 14. O1.3 human acceptance and O1.4 recover-admin regression — 2026-10-13

O1.3 **HUMAN ACCEPTED / CLOSED** by explicit human decision; original results/limitations remain historical. O1.1–O1.3 are closed. O1.4 alone was authorized and is **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**.

Exactly two task-owned files: `scripts/test-deployment-cli.py` (six recover-admin cases and three scoped fixture/assertion helpers appended; all existing test/harness methods unchanged) and this plan (acceptance/status/execution record only). Reuse the accepted fake install/key fixture to obtain valid initialized private state, snapshot all persisted files and inspect only subsequent recover-admin calls. No shared harness rewrite or production change.

Coverage: full-element Email/non-web/enabled/display/password-file/mount argv, one invocation/no replay, unmodified mixed-case/padded/punctuated fictional Email and exact password bytes/hash/mode, sanitized backend refusal including suppressed fake stdout/stderr, blank/POSIX-whitespace pre-dispatch rejection, password mismatch, interrupt/echo restoration and empty temporary directory/lock removal. Persisted initialized manifest, crypto/TLS/branding files remain byte-identical; Email/password are not placed in saved configuration. Runtime startup remains one initialized fake up before command; no build.

Executed from repository root: `python3 .cache/o1-step4/run.py > .cache/o1-step4/focused.log 2>&1` with PTY permission outside sandbox. Runner selects six new cases plus two accepted O1.3 preservation cases, overrides BASE to a new private O1-owned scratch checkout and does not run historical __main__ deletion. **8 passed, zero failures/errors/skips,32.684s**, exit0. Fake install setup assertions within the six new cases are fixture setup, not additional unittest cases. No full control suite, PostgreSQL suite or broad historical verifier run.

Exact selected cases:

```text
test_recover_admin_email_argv_and_initialized_privacy
test_recover_admin_refusal_is_sanitized_and_never_replayed
test_recover_admin_empty_email_has_no_default
test_recover_admin_whitespace_email_never_dispatches
test_recover_admin_password_mismatch_preserves_initialized_state
test_recover_admin_password_interrupt_restores_echo_and_state
test_install_email_preserves_case_spaces_and_password_bytes
test_install_refused_recovery_never_reports_ready
```

Python AST syntax, links/whitespace, `git diff --check`, exact two-file ownership, existing-method equality, production/other-file SHA256 and pre-existing dirty-document diff preservation checks performed at completion; index empty. The refusal test adds unsafe output only to its disposable fake Docker file; accepted shared harness responses/source remain unchanged.

This is isolated PTY/fake-Docker argv/control/file evidence only, not successful real recovery, backend Email/eligibility validation or PostgreSQL/audit proof. No real Docker/backend/frontend, operator data, runtime/environment, guides/AUTHENTICATION/INVENTORY, historical evidence or old verifier/smoke modification. Only test-owned private scratch files/test crypto created. No staging/commit; O1.5–O1.7 NOT AUTHORIZED. STOP for human O1.4 acceptance.


## 15. O1.4 human acceptance and O1.5 automated verification — 2026-10-13

O1.4 **HUMAN ACCEPTED / CLOSED** by explicit human decision. O1.1–O1.4 are accepted; their original evidence/results/limitations remain historical. O1.5 alone was authorized and is **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**. Only this plan changed in O1.5 (minimal status and execution record); no production, test, operator-document synchronization or verification-report edits.

### Exact commands and results

From repository root, with PTY access outside sandbox:

```sh
./scripts/test-deployment-cli.sh > .cache/o1-step5/control.log 2>&1
```

Exit0: **complete maintained control suite35 passed**, zero failures/errors/skips,68.877s. Wrapper also ran `sh -n kelios` and syntax checks on maintained shell scripts. The suite uses copied checkout/private local files and fake Docker. Its normal __main__ cleanup affects only the dedicated `.cache/deployment-cli/control` disposable test tree; no manual installation/database targeted. No initial failed control attempt in O1.5; prior O1.3 terminal-permission failures remain in their own historical record. Testcases already counted at O1.3/O1.4 are included, not additional unique cases.

From `backend`, with isolated Testcontainers/Docker access outside sandbox:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B '-Dit.test=IdentityAuthenticationIT#passwordPolicyEveryCredentialPathAndChangeDoesNotRevoke+bootstrapRefusesUsableAdminButLaterRecoversAndAudits+malformedCredentialAdminDoesNotBlockRecoveryAndFailureRollsBack+concurrentRecoveryCommandsCreateExactlyOne+recoverySerializesWithAdminPromotion+pendingAndCredentiallessAdminsDoNotCountAsUsable+unverifiedAdminDoesNotBlockGuardedRecovery+emailCanonicalUniquenessAndEditPreservesIdentityPasswordAndCreationMetadata+globalLoginAndRefreshRequireVerificationApprovalAndActive+selfDisableDemotionRejectedWithoutLastAdminInvariant' test-compile failsafe:integration-test failsafe:verify > ../.cache/o1-step5/postgres.log 2>&1
```

Exit0, **BUILD SUCCESS**:15 PostgreSQL integration cases, zero failures/errors/skips; suite16.534s, Maven18.482s. Fresh PostgreSQL17-alpine Testcontainer uses current V1–V8 migrations and existing ephemeral identity keys/HTTPS OIDC fixture infrastructure. Selected cases do not contact a real Provider or manual environment. Surefire/unit suite and full backend suite were not executed; do not label historical802 as a fresh result or add control cases to a Maven test total.

Selected existing method names (nine single cases + one six-case parameterized method):

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

The validity matrix executes ACTIVE/PENDING/DISABLED × verified/unverified, six cases. Failsafe XML `backend/target/failsafe-reports/TEST-com.github.ksewen.kelios.identity.auth.IdentityAuthenticationIT.xml` was inspected: exactly these15 executed outcomes, failures0/errors0/skipped0. Self-protection/zero-admin behavior is included because caller refusal must not imply a permanent last-admin invariant. No test implementation or assertion changed.

Additional commands/checks: explicit `sh -n kelios` exit0; `git diff --check` exit0; documentation relative links/whitespace and SHA256 two-sided pre-gate ownership/preservation checks; exact pre-existing dirty-document diff comparison; empty index. Exact results reported on completion. Ignored local logs are diagnostic run artifacts, not new committed evidence/private credentials.

### Evidence boundaries and sufficiency

Control35 proves both CLI callers, complete argv/private-file handling, output/state/cleanup and other maintained control preservation using fake Docker. PostgreSQL15 separately proves authoritative service Email/password/eligibility, recovery serialization, rollback/audit, metadata/canonical uniqueness and verification/status behavior. These are distinct layers; no claim that mocked CLI execution proves database authorization/atomicity, or that a service test proves the actual Compose/ApplicationRunner invocation end to end.

The accepted quoted Email argv plus static `AdministratorRecoveryCommand.run` required-property dispatch and the focused existing service tests expose no new insufficiency requiring a command unit. No AdministratorRecoveryCommandTest was added. Actual fresh Docker install/non-web command interoperability is not independently executed here; retain that limitation, do not invent it or extend O1.5 to modernize the broad historical verifier.

No real/operator install, recovery/bootstrap, PostgreSQL state or business-data mutation; no backend/frontend production/test, schema/API, Compose, old verifier/smoke, guides/AUTHENTICATION/INVENTORY or historical evidence change. Testcontainers/disposable control fixtures only. O1.6–O1.7 remain NOT AUTHORIZED, no staging/commit or next step. STOP for human O1.5 acceptance.


## 16. O1.5 human acceptance and O1.6 operator documentation — 2026-10-13

O1.5 **HUMAN ACCEPTED / CLOSED** by explicit human decision; its exact35 complete controls and15 PostgreSQL cases, commands/exits/limitations above remain historical and unchanged. O1.1–O1.5 are closed. O1.6 alone was authorized and is **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**. O1.7 remains NOT AUTHORIZED.

Exact four-document task manifest and hunk ownership:

- `docs/DEPLOYMENT_CLI.md`: update only the O1.1-owned current-boundary paragraph; in the already-dirty First install paragraph replace “GLOBAL ADMIN login” with explicit Email/no account default and “15–128” with current12–128; add one narrow shared-recovery/backend-authority/no-delivery paragraph; expand only the recover-admin command row. Other content/blank lines, including the pre-existing authority preamble and wording “The implemented baseline backend password policy applies”, is preserved. O1.6 owns those replacements, not the earlier unrelated whole-file hunk.
- `docs/AUTHENTICATION.md`: replace only the obsolete CLI old-login handoff limitation in the V8 paragraph with current Email handoff/status/evidence boundaries and plan link. Original V8 refusal/reset restriction and all other authentication text unchanged.
- `docs/plans/DEPLOYMENT_CLI_PLAN.md`: update only the O1.1-owned current cross-reference paragraph, because its current-boundary wording incorrectly still said O1.2 not authorized/Email not implemented. Historical v1 body and pre-existing historical-authority preamble remain unchanged; no broader optional plan rewrite.
- This plan: O1.5 acceptance, O1.6 status and this execution record only.

The First install hunk overlaps a pre-existing dirty paragraph: its original pre-O1 planning edit added “The implemented baseline” while retaining Login/15. That pre-existing wording is not removed or silently adopted; only the accepted O1-specific Email and current policy substitutions plus separate recovery paragraph belong to O1.6. The general guide preamble remains untouched even though it predates current handoff acceptance. Explicit current O1 paragraph governs this narrow implemented behavior, not the older generic future wording.

Commands/checks: relative links/new-document whitespace, `git diff --check`, exact four-file SHA256 ownership, reverse-owned-edit reconstruction of guide/authentication/historical-plan to pre-O1.6 bytes, and all unrelated file-hash preservation. No suite rerun or stronger verification claim. O1.6-only hunks are retained separately from the full HEAD diff in ignored planning scratch for future ownership review.

Current instructions accurately describe manually supplied GLOBAL ADMIN Email, no default, backend-only Email policy/eligibility, current12–128 Unicode-code-point composition policy, exact/untrimmed password file and no Email delivery. No new live Docker/operator recovery evidence claimed. Historical CLI/I1/O1 verification records and earlier results/limitations unchanged. INVENTORY, kelios/tests/backend/frontend/API/schema/runtime and all unrelated dirty/untracked content are untouched. No staging/commit; STOP for human O1.6 acceptance, no O1.7 start.


## 17. O1.6 acceptance and O1.7 final review package — 2026-10-13

O1.6 **HUMAN ACCEPTED / CLOSED** by explicit human decision. O1.1–O1.6 are closed; original execution results/status-at-execution and limits remain historical. O1.7 was authorized for final evidence/package only, **IMPLEMENTED / PENDING HUMAN ACCEPTANCE**. Overall O1 is not self-accepted; commit is NOT AUTHORIZED.

[O1 verification and exact proposed commit boundary](../verification/DEPLOYMENT_CLI_O1_EMAIL_RECOVERY.md) consolidates the already accepted control35 and PostgreSQL15 results, all exact commands/selected methods, earlier test-terminal failures and runtime limitations without rerunning suites. Exact closeout task manifest: this plan and the new verification report only. Historical CLI/I1 verification and all other accepted records are unchanged.

Proposed eventual O1 commit: seven paths, five whole-file changes and two partial documentation changes. Include kelios, scoped control tests, AUTHENTICATION handoff paragraph, new O1 plan/report and only O1-owned guide/old-plan hunks. Exclude the guide's pre-existing general authority preamble and “The implemented baseline” wording, old plan's pre-existing historical-authority preamble, INVENTORY, backlog audit, IDE/historical artifacts and all private/generated/runtime/residual work. A full `git add` of guide/old plan would incorrectly adopt those unrelated edits. Review-only candidate document blobs/patch are in ignored `.cache/o1-step7`; neither worktree replacement nor index mutation occurred.

Record final human acceptance separately before requesting any commit. On later explicit commit authorization, rederive the actual approved manifest/hunks, include only approved current-status amendments, verify proposed index vs owned content, links/whitespace/private-material/exclusions, then commit/verify index and unrelated work. If ownership changes or executable content differs from accepted verification, STOP rather than adapt code for closeout. No automatic commit/next gate.

O1.7 checks: exact two-document delta against immediate pre-closeout SHA256 snapshot; current executable/test bytes match accepted O1.5 verification baseline; every unrelated pre-existing file and original evidence retained; doc links/new whitespace and `git diff --check`; empty index; proposed seven-file/hunk/private-material boundary checked. Exact results in final report; no new test/runtime claim. STOP for final human review.


O1.7 commit-hygiene finding: this accepted plan links the pre-existing uncommitted remaining-backlog audit, excluded from O1 ownership. Current worktree links resolve, but the proposed seven-path O1-only checkout would lack that audit. Commit preparation requires explicit human disposition: separately approve a prerequisite audit-document commit or authorize a narrow reference adjustment. No ownership expansion/reference edit/staging is performed here. Final review must acknowledge this dependency; the proposed commit is conditional, not link-complete by itself.


## 18. Final human acceptance and commit authorization — 2026-10-14

O1.7 and overall O1 are **HUMAN ACCEPTED / CLOSED** by explicit human decision. The seven-path, hunk-scoped closeout commit is now explicitly authorized. The backlog-audit dependency is resolved by accepted prerequisite commit `24f24f3539a4b0d84d1d892f0fa3678cc65392b3`; its pre-O1 snapshot remains historical. Earlier execution-time statuses, results and limitations above are preserved. No suite rerun or stronger runtime claim accompanies this status update; no subsequent backlog work is authorized.
