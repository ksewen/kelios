# Asset CSV export and legacy import

Use **Assets** in the authenticated KELIOS application. CSV is administrative tooling; ordinary Asset registration and editing remain the standard workflow. These actions do not require a deployment CLI command.

## Export the current Asset result

1. Choose the desired operating Tenant in the top-level context selector, or keep Global to include all authorized Tenants.
2. Apply text search with the search button, confirm any advanced filters, and choose the desired table sort. Unsubmitted text and draft filters are not part of the export.
3. Choose **Export CSV**, select semicolon, comma, tab or a custom single-character delimiter, then choose **Download CSV**.

The download includes every matching authorized Asset, not just the visible page. It is UTF-8 with a BOM for common spreadsheet software, has stable English column names, and includes readable associations alongside relevant IDs. The backend limits one export to 20,000 Assets and 20 MiB and escapes formula-like cells. If the limit is exceeded, narrow the collection query and try again. ACTIVE Asset readers may export only their authorized scope; TENANT identities remain bound to their signed Tenant.

## Import legacy Assets

Only an ACTIVE GLOBAL ADMIN can open **Import CSV** from Assets. A file may contain rows for different Tenants; each row must identify an existing Tenant. CSV v1 has a header and is limited to 5 MiB, 5,000 data rows, 64 columns and 32 KiB per decoded cell. No XLSX or historical checkout/return import is supported.

1. Select the CSV file. Choose automatic encoding/delimiter when unambiguous, or explicitly choose UTF-8, Windows-1252 or Latin-1 and semicolon, comma, tab or a custom delimiter. Check the raw parsed rows before mapping; German Umlauts must look correct there. Expand **CSV columns and required fields** for the field rules.
2. Map a source column to **Tenant UUID** or **Tenant Name**. Each row must resolve one existing Tenant; an absent or invalid Tenant is a row error. Map other source columns to KELIOS fields, using either UUID or Name for each reference. A mapped Product supplies its existing Manufacturer and Category; otherwise map both directly. The historical Tag date is import input for a normal KELIOS Tag, not an Asset creation timestamp.
3. Create a Preview and check the resolved Tenant shown for each row. Resolve distinct source enum values and ambiguous Name references, then **Recheck preview**. Duplicate Tenant names require an explicit existing-Tenant choice. Tenants cannot be created inside Import. A missing other reference is never created automatically: open its normal collection/create workflow, create it with ordinary permissions and validation, then recheck. Errors are excluded; Ready and Warning rows may be selected.
4. Review selected rows and warnings, then confirm **Import**. Every selected row is revalidated against current Tenant and references. Successful rows commit independently; a failed row rolls back without stopping later rows. The immediate result shows success/failure/skipped counts and offers a failure CSV containing original columns and reasons.

Preview does not reserve Tags or references. If a request times out or its result is unknown, inspect Assets before retrying: local identifiers and other legacy input are not globally unique, so repeating a successful row can create a duplicate. Re-import corrected failed rows rather than the whole original file. Import records one truthful initial snapshot and administrative audit action; it does not invent earlier lifecycle or loan history. Some backend-supplied row reasons currently remain in English in the German UI.
