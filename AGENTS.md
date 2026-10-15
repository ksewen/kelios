# KELIOS — Instructions for Coding Agents

## Read order and authority

Read these files in order:

1. `ARCHITECTURE.md` — current technical boundaries and invariants.
2. `DOMAIN.md` — current business model, identity, authorization and resource rules.
3. The currently HUMAN-approved Plan and the explicit authorization for the requested gate, including its evidence and exclusions.

`README.md` is the human entry point. This file governs agent/repository process. DOMAIN owns business policy; ARCHITECTURE owns technical boundaries; `docs/api/openapi.yaml` defines the external transport contract consistent with them. Explicit current human decisions control their scope. Plans, analysis and historical evidence cannot silently redefine normative rules. Source/tests establish implemented behavior, not approval of a contrary policy: report conflicts and stop affected work rather than inventing a resolution.

`CODEX_IMPLEMENTATION_TASK.md` and completed Phase 0–4 plans are historical execution instructions, not a requirement to restart Phase 0. Do not invent missing business rules or expand deferred features. All project specifications and agent-facing documentation must be in English.

Backend build is **Apache Maven 3**, using `pom.xml` and the committed Maven Wrapper (`./mvnw`); **do not use Gradle**. Backend Docker builder must use Maven 3; its runtime image must not contain Maven. Preserve independent frontend/backend images and the approved runtime-branding boundaries.

## Canonical documentation locations

- `README.md`: purpose/scope, high-level orientation and links to run/develop/test instructions; no detailed manual or implementation-history dump.
- `AGENTS.md`: work process and repository governance; no duplicated business/technical specifications.
- `ARCHITECTURE.md`, `DOMAIN.md`: durable current truth, with clear ownership and links instead of duplicate rules.
- `docs/operations/`: operator deployment/runtime/recovery/troubleshooting procedures.
- `docs/development/`: contributor setup, build/test workflow and repository development procedures.
- `docs/api/openapi.yaml`: machine-readable HTTP contract; generated renderings are not a second edited specification.
- `docs/adr/`: significant durable cross-cutting decisions, context, alternatives and consequences. Ordinary implementation choices belong in Plans/closeouts, not automatic ADRs.
- `docs/plans/`: one current backlog index and coherent active execution Plans.
- `docs/analysis/`: working/supporting investigations, never normative truth or default permanent history.
- `docs/history/`: compact accepted milestone closeouts, never bulk archival. `docs/history/assets/` contains only uniquely valuable evidence that cannot be adequately preserved in text.
- `.cache/verification/<gate>/`: ignored temporary raw verification output; not a durable evidence archive.

No separate `contracts/`, `reference/`, permanent `docs/verification/`, or general archive/security directory is part of the approved structure. Security rules belong to their normative owner; operational security procedures belong in operations. Existing paths remain until a separately authorized migration; this policy does not authorize moves/deletions or rewriting evidence producers. Do not create empty categories merely for symmetry.

Use minimum useful documentation. Prefer an owning Plan section to a new Markdown file. New filenames use lowercase kebab-case and stable gate IDs; do not mass-rename existing files for style. A new Plan needs one coherent reviewable unit with scope, ownership, dependencies, steps, exclusions and acceptance criteria. Do not create arbitrary Markdown/evidence files for each small step.

## Plan lifecycle and human authorization

Use `DRAFT → APPROVED → IN PROGRESS → HUMAN ACCEPTED / CLOSED`.

- DRAFT is proposed scope, not authorization.
- APPROVED means the human reviewed the Plan; execution still requires explicit authorization for the exact gate. Record authorized scope/date separately.
- IN PROGRESS means authorized work. Once verification finishes, record `Review: pending human acceptance`; do not self-close or start the next gate.
- HUMAN ACCEPTED / CLOSED requires explicit human acceptance, with date, scope and accepted limitations. A completed Plan must not remain permanently active.
- BLOCKED is temporary and names a concrete prerequisite and prior state. Routine waiting for acceptance is not a blocker. Resume only within authorization.
- SUPERSEDED names a reviewed successor and accounts for every unfinished requirement.
- ABANDONED requires explicit human cancellation; retain meaningful decision/failure evidence before retirement.

Apply these statuses prospectively; do not falsify historical execution-time states. Plan approval, implementation authorization, human acceptance, consolidation authorization and commit permission are distinct. Never automatically begin the next gate.

## Analysis and verification lifecycle

Default diagnosis belongs in the Plan. A separate Analysis is justified only by substantial source matrices, alternatives, security/concurrency reasoning or impact analysis that would obscure execution, or an explicit human request. Name its question, baseline, evidence certainty, owning decision/Plan and exit condition.

After the relevant decision/implementation is accepted, absorb useful analysis conclusions into the owning Plan closeout, normative documentation, an ADR where genuinely justified, or compact history. Superseded/intermediate analysis should normally be deleted under an authorized consolidation manifest. An analysis may remain only if it provides independently useful supporting material that would be materially damaged by consolidation; record that justification and owner. Analysis is not permanent historical storage by default.

Record verification in the owning Plan/work gate: exact commands, baseline, results/counts/exits, significant failures, scope and limitations. Distinguish isolated/PostgreSQL/intercepted/live/manual evidence; never fabricate coverage or create operator data to satisfy evidence. Raw outputs belong in ignored scratch while work is active. Reusable regression protection remains in the automated test suite.

After acceptance, merge durable verification conclusions and limitations into the Plan closeout or one compact history record. Delete redundant command output, duplicate screenshots and transient artifacts only after approved consolidation. Keep unique evidence when textual consolidation would lose necessary proof. No permanent verification namespace or arbitrary evidence accumulation.

## Post-acceptance consolidation and synchronization

For each accepted unit, propose a closeout that promotes approved durable rules to their normative owner, preserves important decisions/failures/limitations/HUMAN ACCEPTANCE in one compact record, transfers genuinely unfinished work to the backlog, and identifies exact redundant-artifact retirement. If a compact completed Plan becomes the history record, do not also keep a duplicate active Plan. An open parent Plan retains only its genuine remaining scope.

Consolidation/removal needs explicit authorization. Until authorized, preserve originals and mark closure truthfully. Before deletion, show the retained information, inbound links, producer-path dependencies, ownership and retention manifest. Never delete architectural/security/operator decisions, acceptance scope or unique failure evidence merely for neatness. Preserve separate measurement roles even when raw bytes match. No blanket relocation into history or presumed external archive.

When authorized implementation changes approved behavior, synchronize the relevant normative/API/operator documentation within scope. Do not turn historical results into stronger claims or treat code as approval of a new policy. Keep one current status owner and dated history for past states. Status-only changes need link/whitespace/preservation checks, not invented new test results; executable changes require appropriate verification.

## Worktree ownership and Git policy

- All work must remain within the KELIOS repository. Do not modify files outside it.
- Inspect and preserve pre-existing dirty/untracked work. Establish path/hunk ownership before editing; do not overwrite, normalize, stage or silently adopt unrelated content.
- Mixed-provenance files require exact hunk isolation and verification against the accepted manifest. Ambiguous ownership is a stop condition, not permission to whole-file stage.
- Credentials, private runtime data, raw provider claims, keys, database dumps and unreviewed sensitive traces must never enter Git.
- Git read-only commands are allowed. Create commits only when explicitly requested; human acceptance alone is not commit permission.
- Commit only the exact accepted scope: derive the manifest, inspect staged content, check links/whitespace/private-material exclusion, and report remaining index/status and preserved unrelated work.
- Never push without explicit approval. Never use destructive Git commands without explicit approval.
- Preserve the 13 accepted milestone commits through `9a0332b435079453dc585e9b98bab65a633eecdf`. No history rewrite is authorized by documentation governance or cleanup.
- Rebase/squash/reorder/drop/filtering/reset/force-push history operations require separate explicit HUMAN AUTHORIZATION for the exact proposal, reference mapping and preservation boundaries. No reset/stash/clean shortcut around dirty ownership.
- Stop at the authorized gate boundary; no automatic cleanup, next implementation or history operation.
