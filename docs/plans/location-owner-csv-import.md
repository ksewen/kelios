# Location and Owner CSV import — planning baseline

**Feature status:** PLANNED / BLOCKED — Waiting for Asset CSV acceptance.

**Implementation:** NOT AUTHORIZED. This approved documentation baseline is not approval to implement or human acceptance of the feature.

**Baseline:** local `main` at `e4ec59a59797c3ec417b85a3cd3f2241cc078614`; isolated branch `feature/location-owner-csv-import`.
**Dependency inspected:** unaccepted `feature/asset-csv-import-export` at `ca2d72113d404e5ff444cf9e578cbc5f0854d604`. Its working-tree documentation also has uncommitted changes and is not part of this branch. The [reuse audit](../analysis/location-owner-csv-import-reuse-audit.md) separates verified facts from recommendations.

## Goal and boundary

Import multiple Location or Owner names from a CSV through the same creation semantics as the existing Standorte and Eigentümer forms. This is a small administrative convenience, not iServ integration, a data migration, or a general import framework. One import creates one resource kind in exactly one concrete Tenant. No timestamps, historical state, automatic reference matching, note mapping, deduplication, overwrite, or new business meaning are implied.

The source CSV contract is **not finalized**. Obtain a representative iServ or equivalent export before implementation; determine supported column shape, delimiters, encodings, and header handling from that evidence. No header aliases, including `name`, `Name`, or `Bezeichnung`, have been approved. A file whose first data value happens to match a possible header must not silently lose that row.

## Confirmed domain, API, and UI contract

- `Location` and `Owner` are flat Tenant-owned resources. V2's `location` table has a non-null Tenant FK and a non-null, nonblank `name`; `owner` inherits its column constraints. V7 adds nullable `note` and server-owned creator/update metadata. Neither table has a unique constraint on `name`, so duplicate names are permitted and are not inherently invalid. See `backend/src/main/resources/db/migration/V2__domain_foundation.sql` and `V7__business_metadata_and_place_note.sql`.
- `PlaceModel.Command` and `PlaceService` require a nonblank name and strip surrounding whitespace. They normalize omitted, null, or blank note to null. `PlaceDtos.Command` requires textual `name`, accepts optional textual/null `note`, and rejects unknown fields. **Name alone is sufficient for both creates.** See `backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceModel.java`, `backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceService.java`, and `backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceDtos.java`; the public schema is `PlaceCommand` in `docs/api/openapi.yaml`.
- Existing `POST /api/v1/tenants/{tenant}/locations` and `/owners` return `201` with a created ID. `PlaceOperations.create` requires an active administrator, resolves the concrete Tenant, and delegates to the transactional create service and audit. GLOBAL ADMIN supplies an authorized concrete Tenant; TENANT ADMIN uses its signed Tenant, regardless of a supplied path Tenant. The importer will use these existing APIs, one POST per row; no backend creation change is required. See `PlaceController.java`, `PlaceOperations.java`, and `backend/src/test/java/com/github/ksewen/kelios/identity/auth/PlaceIT.java`.
- The current `Management` Create form requires name, offers optional note, and sends `{name, note: null}` when note is blank. In Global context it requires a Tenant choice; in a concrete Tenant context it displays a read-only target. Tenant principals derive their context from signed identity. The import should retain these authority and presentation rules. See `frontend/src/features/inventory/pages.tsx` (`Management`) and `frontend/src/app/capabilities.ts`.

## Intended interaction and decisions

Add a small import entry point to each existing collection, visible only to authorized administrators. The flow selects a CSV, presents the parsed source rows and names for review, fixes one target Tenant, and confirms creation. GLOBAL ADMIN explicitly selects exactly one concrete Tenant per import, even when entering from a Tenant-scoped collection. TENANT ADMIN sees its own Tenant read-only and cannot change it. The UI must never distribute one import across Tenants. The selected Tenant is fixed for the execution; changing it requires a fresh review.

Attempt rows independently with the existing Create API and `{name, note: null}`. Preserve source row number, raw name, progress, created ID on success, and localized actionable backend error code/status on failure. Empty names and other backend-invalid values fail as individual rows; later rows continue. Do not preemptively reject or merge duplicate names. Do not automatically replay a POST after an uncertain response; retain its row as requiring human review. Keep partial results visible and refresh the relevant collection after successful creates.

**CSV configuration UI reuse:** After Asset CSV acceptance, prefer its established encoding, delimiter, and header-control patterns, and reuse components where practical. Keep configuration behavior consistent where the source format supports it. This is a UI decision, separate from parser reuse; do not import the complex Asset field-mapping workflow.

**CSV parser reuse:** After Asset CSV acceptance, inspect its accepted interface, coupling, complexity, and authorization boundary. Reuse or extract only if that is smaller and keeps TENANT ADMIN access and Location/Owner execution independent. At the inspected revision, automatic delimiter detection requires at least two columns, so a one-column file needs an explicit delimiter or another approved handling path. This expected limitation is not a defect; do not change the Asset parser solely for this feature. Any required shared backend parser endpoint/extraction is a scope change requiring an updated plan and explicit authorization. See the [reuse audit](../analysis/location-owner-csv-import-reuse-audit.md).

## Gates and acceptance

1. **Planning prerequisite:** Asset CSV work receives human acceptance. Re-audit accepted parser and CSV configuration UI interfaces. Obtain a representative source file and resolve the CSV contract, especially one-column parsing and header presence/labels. Update this plan if acceptance changes those interfaces. Record the exact implementation gate authorization before starting code.
2. **Implementation, only when authorized:** Add the smallest Location/Owner collection flow, localized copy, source-row handling and per-row Create calls. Reuse accepted UI patterns where practical. Keep backend changes at zero unless a separately authorized parser reuse decision requires them. Do not modify Asset CSV behavior for this import.
3. **Verification:** For both resources, cover GLOBAL ADMIN's explicit one-Tenant selection and TENANT ADMIN's signed-Tenant lock; valid, empty and duplicate names; the agreed headed/headerless formats; backend validation failures; partial success and source-row errors; and no cross-Tenant writes. Verify manual Place creation and Asset CSV functionality remain unaffected. Run focused frontend tests/build; run focused Maven and contract tests only if backend work is separately authorized. Record commands, results, and limitations here. Stop for human review; do not claim acceptance from tests.

**Exclusions:** iServ connection, XLSX, arbitrary column mapping, note mapping, historical timestamps, migration semantics, uniqueness/deduplication, record overwrite, new permissions, Asset import execution reuse, merge, push, or automatic next-gate work.

## Conditions for resuming

Asset CSV acceptance; fresh interface audit; representative source format and explicit decisions for columns, encoding/delimiter, and header handling; an updated plan if shared parser work is needed; and explicit authorization for the exact implementation gate. Until then, this feature remains PLANNED / BLOCKED.
