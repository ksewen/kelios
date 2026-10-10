# Inventory and administrative audit operations

The authenticated inventory workspace reuses same-origin HTTPS and the Phase 2 memory Access Bearer / HttpOnly Refresh architecture. See [OpenAPI](api/openapi.yaml) for all request shapes and [authentication operations](AUTHENTICATION.md) for deployment keys and provider configuration. No browser-stored tokens, Redis or server-side token sessions are used.

GLOBAL users select Tenant context from the workspace selector; all-Tenant Asset browsing is read-only until a Tenant is selected for creation. TENANT users remain fixed to their authenticated Tenant. GLOBAL ADMIN writes across Tenants, TENANT ADMIN writes only within its own Tenant, and USER roles are read-only. The backend validates all paths and referenced IDs independently of UI controls.

Registration accepts Product ID alone for Catalog facts, or required Category/Manufacturer IDs without Product; require Location and explicit loanable. Tag, actor, state and timestamps are generated server-side. Optional Local identifier (`localIdentifier`) is organization-assigned, non-unique and separate from the KELIOS Asset Tag, serial and external inventory number. Outer whitespace is stripped; blank becomes null; case/internal whitespace are preserved. It accepts at most 128 Unicode code points and rejects Cc/U+2028/U+2029. Ordinary text replacement, Catalog reassignment and loanability are separate commands. Loanability cannot change during a current loan. Lifecycle/Condition have dedicated graph-validated operations; deregistration needs TRANSFER/DISPOSAL/LOST/ERROR and emits one DEREGISTERED event. It does not delete the Asset, clear references, return a loan or move Location. Return remains available for any outstanding loan, including DEREGISTERED/UNUSABLE Assets.

Asset detail browser URLs use `/inventory/assets/{id}` so reloads remain compatible with the existing nginx `/assets/` static-file boundary; API detail remains `/api/v1/assets/{id}`. Tenant collections use `/tenants/{tenantId}/...`. No deployment routing change is needed.

Metadata replacement supplies the complete nullable `serialNumber`, `externalInventoryNumber`, `note` and `localIdentifier` state: omission and explicit null both clear a field. This is not PATCH. Registration omission means null. New REGISTERED/UPDATED and other authoritative state snapshots include the identifier; old event JSON is never inferred or rewritten. Deregistration/internal deletion retain it; receiving Tenant registration does not automatically copy it.

Every mutable Asset command supplies expectedVersion. A 409 leaves the draft for review: reload authoritative current state and explicitly resubmit only after assessing the change. No blind retry after conflict, timeout or uncertain success. History displays stored immutable snapshots, including original labels, reason, actor and loan values; unavailable fields in older history are not fabricated.

Asset list uses page=0, size=25 by default/max 100, fixed sorts/filters and literal case-insensitive q up to 128 characters across tag, localIdentifier, serial and external inventory number. There is no dedicated localIdentifier filter, sort, index or uniqueness check. It excludes internally deleted rows, but includes DEREGISTERED. No Asset delete/restore/hard-delete interface exists. Retained references continue to guard Catalog/place deletion regardless of lifecycle/internal soft deletion.

Catalog/place screens expose separate create/edit/delete and authorized publication/Merge commands with backend guards. Product historical use permanently freezes ordinary editing; Category/Manufacturer/Owner reference guards remain. Location label edits never move an Asset. Equal names are valid. Merge preview is advisory and can become stale; confirmation sends only source/target IDs, and execution freshly locks/validates/discovers. Explicit source/target ID controls support every legal scope combination without duplicate matching. Only Category/Manufacturer Merge exists.

Administrative audit is separate from Asset business history. GLOBAL ADMIN sees all; TENANT ADMIN sees only captured own-Tenant records, including counts/detail. Publication belongs to the original Tenant and Merge to its source; subsequent global operations have null attribution. No live target lookup changes historical visibility. USER/PENDING have no audit access.

## Migration and verification

**V5 pre-deployment cutover:** clears only disposable existing audit_log development/test rows, then adds historical nullable tenant_id and scoped ordering index without a Tenant FK. KELIOS has not been deployed; no production history backfill exists. All AssetEvents, Assets, Catalog/place, Tenant/User, credential/provider/external identity data and tag counters are retained. Never apply an edited V1–V4.

**V6:** adds nullable Asset local_identifier plus Unicode character-count/forbidden-character CHECKs. Existing values are null; no backfill/reset/index/table, and V1–V5 are unchanged.

Use `./scripts/smoke-phase4.sh` for current independent images, the isolated `kelios-phase4` Compose project, HTTPS/authentication/browser, en/de × blue/green same-image branding and disposable backup/restore checks. Evidence goes only to docs/verification/phase4. Default HTTP/HTTPS ports are 9080/9443. Historical evidence-writing scripts are not rerun unchanged. Private keys/passwords/dumps stay under ignored protected .cache directories. Stop the isolated services, preserving volumes, with `docker compose -p kelios-phase4 -f deploy/compose.yaml stop`.

Frontend English/German labels are presentation only. The public switch persists only validated `kelios.ui.language` in localStorage; authentication tokens/credentials remain prohibited from local/sessionStorage. User data and stored snapshot values are not translated. See [presentation localization](LOCALIZATION.md).

Real IServ interoperability remains [MANUAL VERIFICATION PENDING](verification/PHASE_2_ISERV_MANUAL.md); documented fixture verification does not establish real-provider compatibility.
