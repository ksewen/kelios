# UI redesign verification

**Current documentary boundary (2026-10-15):** Implementation was committed and later dependent frontend work was accepted, but this report's separate visual/user acceptance remains unresolved as [VR1](../plans/remaining-work.md). The captures retained below are a minimal pending-review sample, not proof of human visual acceptance or current backend behavior. The original execution-time results and limitations remain historical.

## Status and scope

Implementation and verification target the approved redesign and minimal anonymous organization routing contract. User visual acceptance remains pending review. Accepted Phase 0–4 evidence is unchanged. Real IServ interoperability remains **MANUAL VERIFICATION PENDING**.

The architecture review and intended/implemented permission matrix are in [UI_REDESIGN_PLAN.md](../UI_REDESIGN_PLAN.md), §3. No core identity or authorization mismatch was found. Exact `(issuer, subject)` resolution, one Tenant per TENANT User, JWT snapshots/Refresh reload, JIT PENDING, backend role/Tenant guards and captured AuditLog SQL scoping remain unchanged. The superseded default-provider draft is absent.

## Implemented changes

- Minimal login, separate named/searchable organization dialog and retained organization entry links. Anonymous discovery returns only enabled configured organization names/routing UUIDs and bounded pagination metadata. Selection is routing, never membership proof.
- React Router shell, desktop collapsible sidebar/mobile Sheet, compact header, authorized operating context, named language dropdown and account menu.
- Real authorized dashboard counts/recent Assets/admin activity; separate Asset/Catalog/place/User/Tenant/OIDC/Audit pages. Focused dialogs, accessible confirmations, state/history tabs and stale-draft protection preserve explicit commands.
- English/German semantic resources, stable enum/error labels and mounted runtime branding retained. No fake metrics, generic Roles/Settings, Asset deletion, schema change, authentication bypass or default-provider inference.

## Commands and environment

Repository-local private cache files contain deployment secrets and are excluded from evidence. No password/token/cookie/private key/provider payload is recorded here.

Executed:

```sh
# backend directory; local cache/temp remain inside repository
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify > ../.cache/ui-redesign/maven-verify.log 2>&1
# frontend directory, Node 24 on PATH
npm test
npm run build
# isolated deployment; invokes independent docker build backend/frontend and Compose up --wait
sh .cache/ui-redesign/start.sh
# actual HTTPS Chromium workflows, environment injected by the private local script
sh .cache/ui-redesign/check-browser.sh green
sh .cache/ui-redesign/switch-brand.sh blue
sh .cache/ui-redesign/check-browser.sh blue
sh .cache/ui-redesign/switch-brand.sh green
git diff --check
```

Node 24.15.0, npm 11.12.1, Java 25.0.2, Vite 8.3.4, Vitest 5.0.3, Playwright 1.64.0, Docker client/server 29.8.2; Maven Wrapper/Maven 3.9.16 and PostgreSQL 17.11 Testcontainers. The Chromium launch keeps `chromiumSandbox: true`; narrow process/Docker permission was required by the workspace sandbox. The in-app browser capability was unavailable, so actual Chromium Playwright was used.

Pinned additions: react-router-dom 7.18.4, lucide-react 1.55.0, @radix-ui/react-dialog 1.2.0, dropdown-menu 2.1.25, alert-dialog 1.1.24, tabs 1.1.22, tooltip 1.3.0. Peer compatibility with the existing React toolchain was checked before installation. Existing toolchain/i18n/table versions were preserved.

## Automated results

| Check | Actual result |
| --- | --- |
| Maven verification | BUILD SUCCESS; 74 unit + 241 real PostgreSQL integration tests; 0 failures/errors/skips |
| Frontend unit | 50 tests / 4 files passed; 0 failed |
| Frontend build | TypeScript and Vite production build passed; lazy chunks below the initial 500k warning threshold |
| Green HTTPS browser | 35 passed; 0 failed/skipped (1.7 minutes) |
| Blue HTTPS browser | 35 passed; 0 failed/skipped (1.7 minutes); completed during recovery |
| OpenAPI | YAML parsed; all 173 local references resolved |
| Diff whitespace | Passed |

Backend verification includes existing accepted authorization, independent-connection concurrency, migration, OIDC protocol and identity invariants. Two new PostgreSQL/HTTP tests cover minimal directory fields, pagination/search/bounds, disabled/unconfigured omission, secure initiation and same-identity/other-Tenant rejection. No H2 substitutes PostgreSQL. Browser UI-only fixtures are explicitly labeled; they verify presentation/loading/permission-request behavior, not real provider or backend authorization. Real backend/HTTPS tests separately verify those boundaries.

## Deployment and image evidence

Verification uses **kelios-ui-redesign**, HTTPS `https://localhost:11443`, HTTP 11080 and its independent PostgreSQL volume. Test fixtures exist only in this disposable verification environment. The existing manual-test project `kelios-manual-20261010` on 10443 was not altered.

Final independent images:

- Backend: `sha256:4850836cb48158a2c79326f7ec248b68f83a787e2be0b9f80cbeee6c1fb2b0cf`, runtime user `kelios` (UID 100); read-only runtime check found no Maven or javac.
- Frontend: `sha256:c4e32cedbe2c8e793cc1ff570011da0e52b09300274b6b55a1340a2d62d411f3`, runtime user 101.

Compose PostgreSQL/backend/frontend health passed. HTTPS browser workflows use the normal same-origin nginx `/api` proxy, Bearer business authentication, Secure HttpOnly Refresh cookie, CSRF and exact Origin checks. Blue/green switching changes only the mounted runtime configuration via `--no-build`, not the frontend image.

## Rendered review and workflows

The original run generated en/de × 1440/1024/768/390 × green/blue page/dialog/menu/sidebar captures and fixture-only organization loading/empty/error states. Sixteen representative [pending-review captures](ui-redesign/) remain: brand/locale/desktop/mobile login and dashboard, mobile Product/detail/sidebar, 768px dialog, 1024px Asset/Audit and organization states. Other iteration/matrix captures were retired after automated route/heading, overflow and focus results were consolidated; they can be regenerated only in an isolated environment. Original captures do not prove current backend or real Provider behavior.

Real browser workflows cover registration and Product/direct references; localIdentifier search/change/clear; metadata/Catalog/place/loanability commands; loaned terminal transitions and Return; immutable history; Catalog/place management, Merge preview/publication; User/credential/role/disable/OIDC administration; Audit; direct read-only-user mutation denial; stale-version preservation/no replay; dirty navigation cancellation/discard; memory-only authentication, reload Refresh/logout, CSRF/Origin rejection; bilingual preference/draft preservation and runtime-brand failure fallbacks.

## Failures found and corrections

- Old tests depended on the removed technical wrapper/inline forms; selectors were updated to real page/dialog semantics while preserving security and business assertions.
- Successful registration was initially blocked by the dirty-navigation guard: clear committed form dirty state before success navigation.
- Textarea labels included entered content ambiguously: explicit accessible labels now stabilize both interaction and screen-reader naming.
- A long Tenant menu exceeded the viewport: bound it to Radix available height with scrolling.
- Captures initially raced route changes: require exact URL and expected heading before screenshots.
- Dialog focus and mobile Sheet title/brand duplication were corrected; sidebar active-state prefix matching was narrowed. Create actions were moved above long administrative tables; Tenant rows open workspaces and Audit Tenant filters use authorized names. An absent OIDC configuration is a setup state identified by HTTP 404, not a generic failure.
- Initial main bundle exceeded 500k: lazy authentication/shell boundaries resolved the warning without an unrelated framework.
- Test logout/cookie and modal-overlay races were fixed through awaited UI state and explicit close actions. A role-switch UI fixture now waits for the previous ADMIN Audit page to settle before clearing its request collector, so it cannot misclassify that in-flight request as a new USER request.
- Repeated full reloads hit unchanged authentication throttling: visual navigation uses normal SPA links; test sessions reuse cookies in memory and refresh normally. Production limits were not weakened.
- Sandbox Chromium launch required narrowly approved execution. One Docker Hub token fetch timed out; retry succeeded without changing pinned images or Dockerfile.

## Preservation and limits

DOMAIN, ARCHITECTURE, ADR 0001–0005, V1–V6, deployment configuration, accepted PHASE_0–4 verification documents and IServ manual procedure remain unchanged. Full current backend regression and adapted frontend/auth/branding/browser suites are rerun; accepted restore evidence was not overwritten or rerun. No commit/push/PR/Phase 5.

Real IServ is not verified by the local protocol fixture or organization UI mock. Safari/Firefox, a user dark-mode switch, cross-origin authentication, actual assistive-technology sessions and large-production-dataset performance are not claimed. Final visual/user acceptance remains **PENDING REVIEW**.

## Interrupted-session recovery — 2026-10-11

Recovered `main` at accepted baseline `8c9275770f19970c34b7d0056919d81a4f378bb4`, with the existing redesign uncommitted. Original scope/acceptance is recoverable from `docs/UI_REDESIGN_PLAN.md`; no missing product decision required source work. No Java, TypeScript, test, dependency, migration or authentication/authorization changes were made during recovery.

Evidence provenance was checked before resuming:

- Final frontend production source edit: 2026-10-10 23:49:44 local; Docker frontend build completed 23:49:58 with 50 unit tests and production build passing. Running image exactly matches that build hash.
- Final browser-test edit: 23:51:24; green browser log completed 23:53:50 with 35 passes. That result is valid and was reused, **not rerun**.
- Latest backend source edit: 23:20:32; Maven verification completed 23:21:48. Surefire/Failsafe XML confirms 74/241 passes with zero failures/errors/skips. These results were reused, not rerun.
- Previous blue log completed 23:49:44, before the final Audit selector/Tenant workspace-link implementation and browser-test correction. Its 35-pass result was therefore not counted for the final source. The missing final check was `sh .cache/ui-redesign/check-browser.sh blue`, executed against the already-built final image; all 35 passed.
- OpenAPI YAML/local references and `git diff --check` were rechecked. Named Audit filters, Tenant links, role-dependent requests, cross-Tenant protections, bilingual drafts and browser console/viewport checks passed in the resumed suite.

At the original recovery checkpoint, all 248 expected brand/language/viewport page/dialog/menu/sidebar captures existed and representative captures were visually reviewed. This is a dated result; only the sixteen stated representative images remain in the current tree. Blue/green used the same frontend image; green branding was restored after verification with `--no-build`. Manual-test and accepted smoke deployments were only inspected, not changed.

The now-retired `ui-redesign/recovery-results.json` recorded source/log fingerprints and verified counts for that recovered state. Fingerprints were created during recovery, not logged by the earlier session; the durable count/provenance conclusions are above and original bytes remain in Git history. No automated failure or architectural blocker was found then. Outstanding items are the separate visual/workflow acceptance decision and real-IServ manual verification; browser-engine/assistive-technology/performance limits above remain unchanged.
