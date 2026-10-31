# Location and Owner CSV import — reuse audit

**Question:** Which accepted creation and CSV facilities can support a minimal, Tenant-scoped Location/Owner import without changing business policy?

**Baseline:** local `main` `e4ec59a59797c3ec417b85a3cd3f2241cc078614`; Asset branch `feature/asset-csv-import-export` `ca2d72113d404e5ff444cf9e578cbc5f0854d604`. The Asset branch is unaccepted and has separate uncommitted documentation changes. This audit reads its committed revision only.

**Owner and exit:** [Location/Owner import plan](../plans/location-owner-csv-import.md). Re-audit after Asset CSV acceptance; then retain only independently useful evidence or consolidate under separately authorized documentation closeout.

## Verified repository facts

| Area | Evidence at inspected revisions | Consequence |
|---|---|---|
| Place storage | `backend/src/main/resources/db/migration/V2__domain_foundation.sql` (`location`, `owner`); `V7__business_metadata_and_place_note.sql` | Tenant and nonblank name are required; note is nullable; no name uniqueness constraint. Duplicate names are valid. |
| Create validation | `backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceModel.java` (`Command`); `PlaceService.java` (`create`, `required`, `optional`); `backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceDtos.java` (`Command`) | Name alone suffices. Whitespace-only name fails; surrounding name whitespace is stripped. Omitted/null/blank note becomes null; extra command fields fail. |
| API and authority | `backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceController.java` (Tenant-scoped POST); `backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceOperations.java` (`context`, `create`); `backend/src/test/java/com/github/ksewen/kelios/identity/auth/PlaceIT.java` (`transportTenantNeverDeterminesTenantAuthority`, `detailProjectionAndNoteReplacement`, `strictCommandsNoOverposting`); `docs/api/openapi.yaml` (`PlaceCommand`) | Existing Create APIs and audit can be reused. GLOBAL ADMIN needs a concrete authorized Tenant; TENANT ADMIN's signed Tenant is authoritative. |
| Manual UI | `frontend/src/features/inventory/pages.tsx` (`Management`, `createFields`, `createAction`); `frontend/src/app/capabilities.ts` | Both collection forms require name and offer optional note; empty note sends null. Global context requires Tenant selection; Tenant context is read-only. |
| Asset parser | Asset revision `ca2d721`: `backend/src/main/java/com/github/ksewen/kelios/platform/security/AssetCsvParser.java` (`parse`, `decode`, `records`) and `backend/pom.xml` | Apache Commons CSV parses quoted and multiline fields, strict UTF-8/BOM or explicit Windows-1252/ISO-8859-1, explicit/headerless records, record/line numbers, and bounded file/rows/columns/cells. Auto delimiter checks semicolon/comma/tab but requires at least two columns. A one-column auto parse fails by design. |
| Asset parser tests | Asset revision `ca2d721`: `backend/src/test/java/com/github/ksewen/kelios/platform/security/AssetCsvParserTest.java` | Covers BOM, quoted multiline text, headerless first row and physical lines, legacy encoding overrides, malformed records, ambiguous delimiters, and limits. It does not prove one-column auto detection or Place imports. |
| Asset preview and execution | Asset revision `ca2d721`: `backend/src/main/java/com/github/ksewen/kelios/platform/security/AssetCsvController.java`, `AssetCsvPreviewService.java`, `AssetCsvReferenceResolver.java`, `AssetCsvImportService.java`, and `backend/src/main/java/com/github/ksewen/kelios/inventory/AssetCsvImportRowWriter.java` | Their routes, reference resolution, warnings, historical state/Tag behavior, row writer, and failure CSV are Asset-specific. Preview/execute require GLOBAL ADMIN; direct reuse would violate TENANT ADMIN access and expand the Place feature. |

## Architectural recommendations, not approved implementation

**CSV configuration UI reuse:** Prefer the accepted Asset importer's settings behavior and, where practical, its controls for encoding, delimiter and header presence. Keep the Location/Owner interaction much smaller. The current `AssetCsvImport.tsx` keeps those controls inside its Asset-specific component, so component extraction is optional and must justify its cost. This decision is independent of parser reuse.

**CSV parser reuse:** Reassess only after Asset CSV acceptance. Its bounded decode/parser is the potentially generic part, but the class is in the Asset CSV security package, its parsed result is package-scoped, and its current parse-preview route is GLOBAL ADMIN-only. Assess whether a narrow shared interface and authorization-safe access would be less work than a Place-local parse. Do not copy Asset preview/execution and do not change the Asset parser solely to make one-column auto-detection work. An explicit delimiter is a candidate handling path, subject to the source-format decision. No backend extraction is authorized by this audit.

**Place execution:** Use existing JSON Create APIs per row, with one fixed target Tenant and `{name, note: null}`. This supports independent transactions and partial success without a new bulk API. Preserve row identity and backend error codes. Duplicate names are permitted; no duplicate precheck or overwrite follows from the Asset warning logic.

## Unresolved decisions and dependency

- A representative iServ or equivalent CSV export is needed to decide one-column versus wider files, delimiter/encoding, whether a header is present, and exact accepted header text. No alias policy (`name`, `Name`, `Bezeichnung`, or other) is approved.
- Decide how a single-column file supplies a delimiter if the accepted Asset parser is reused. Its current auto-detection limitation is expected, not an Asset defect.
- Asset CSV is not human accepted. Its interface, tests, and pending working-tree documentation may change. A fresh audit of the accepted revision must precede implementation; any required backend parser change needs an updated plan and explicit authorization.

**Current status:** PLANNED / BLOCKED — Waiting for Asset CSV acceptance. Implementation NOT AUTHORIZED.
