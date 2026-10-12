# Step 9 — Audit backend query/read completion

Status: implementation and full verification complete, **pending human review**. Step 8 configuration is HUMAN ACCEPTED / CLOSED. Approved Q3 backend-only work; no frontend, Phase C/D, export or delivery implementation.

## Query and authorization

GET `/api/v1/audit-log` implements one combined collection contract:

| Input | Semantics |
|---|---|
| page/size | Zero-based page, default 0/20, backend size 1–100. Choices 10/20/30/40/50/100 work; no browser unpaged mode. |
| action/targetType | Exact bound machine strings; no free-text/name/JSON search or inferred enum. Wildcards are literal because predicates use equality, not LIKE. |
| targetId/actorId | Exact persistent UUID predicates, independent of live resource/User existence. No actor-name/Email filter or presentation change. |
| from/until | Independently optional inclusive absolute bounds: from <= occurredAt <= until. Explicit UTC/offset ISO input; offsets normalize to Instant. Reversed/invalid/sub-microsecond bounds return 400. No arbitrary maximum time window. |
| sort/direction | Only occurredAt is accepted; closed asc/desc direction. Direction can stand alone because no other sort is allowed; default DESC, including explicit sort without direction. No mandatory UUID secondary order. Equal timestamps have unspecified relative order. |
| tenantId/globalOnly | Mutually exclusive GLOBAL ADMIN narrowing. true globalOnly means captured tenantId IS NULL (known global ownership); false leaves GA scope unrestricted. Neither grants authority. |

InventoryOperations retains authoritative current signed identity/status/ADMIN guards. ACTIVE GLOBAL ADMIN reads all authorized records or narrows; ACTIVE TENANT ADMIN is forced to its authenticated Tenant and supplying GA scope criteria remains denied 403. USER/PENDING/DISABLED behavior remains unchanged. Count and page use the identical captured-Tenant/criteria predicates. SQL filters the full authorized set, orders it, then LIMIT/OFFSET; no client sorting, arbitrary subsets or live User/target/Tenant joins. GLOBAL historical narrowing does not require a current live referenced entity.

GET `/api/v1/audit-log/{id}` retains independent scoped selection and foreign/absent non-disclosure 404. Explicit application record/HTTP DTO preserve existing public fields: id, tenantId, actorId, actorDisplayName, action, targetType, targetId, occurredAt, data. No secret or persistence entity projection. Historical actorDisplayName/UUID and JSON values (including null) come directly from the immutable record. Reads produce no permanent audit/event.

## Time/paging decisions

PostgreSQL timestamptz has microsecond precision. Query boundaries must be representable exactly; nonaligned nanoseconds are rejected rather than silently rounded inward/outward. UTC output remains Instant/Z. Either boundary may be absent, including both; equality includes the stored timestamp. No date-only or offset-free query is accepted.

Future frontend local calendar day handling converts local start-of-day to UTC for from, and next local start-of-day minus one microsecond for inclusive until. This handles DST day lengths without altering stored timestamps. Tests exercise a Europe/Berlin 23-hour day; no frontend conversion implementation or manual UI claim is made.

Page responses retain items/page/size/totalElements. Beyond-end pages return empty items with the requested page and scoped filtered count; no page redirection. Default size 25 -> 20 is intentional for Audit only; shared helper/other unrelated resource defaults are untouched. Count/page use the existing read-only transaction model; no new snapshot/concurrency framework is introduced. No historical-data compatibility or schema/index machinery is needed.

## Boundaries and preservation

AuditDtos owns HTTP parsing/defaults/closed sort validation and explicit item/page mapping. AuditQuery owns typed criteria/range/page validation. InventoryOperations owns identity/authority and forced scope. AuditReads owns explicit persisted Row/columns, bound predicates/count/order/page and maps AuditRecord. No generic query framework or untyped Domain Map query remains; immutable JSON data is still a Domain payload, not a query DSL.

PRE-Step 9 hashes confirm AuditService/AuditLog, migrations, identity/OIDC/Tenant and frontend are untouched. Reversing only the Audit signatures/criteria access restores InventoryOperations's exact baseline hash, proving all Asset operations unchanged. Actor presentation, captured target attribution, writer metadata/transactions and historical immutability remain intact.

## Verification

From backend:

```sh
MAVEN_USER_HOME="$PWD/../.cache/maven-home" TMPDIR="$PWD/../.cache/tmp" ./mvnw -B verify
```

| Check | Result |
|---|---|
| Maven3/Java25 compile/package/verify | BUILD SUCCESS; 1m34s |
| Unit tests | 74 passed; 0 failures/errors/skips; 5 suites |
| PostgreSQL/integration tests | 661 passed; 0 failures/errors/skips; 13 suites |
| Total | 735 passed; 0 failures/errors/skips |
| AuditQueryIT | 43 passed |
| OpenAPI YAML/internal reference validation | 218 resolved; query defaults/parameters and retained projections checked |
| git diff --check |PASS |

AuditQueryIT proves combined context/action/type/target/actor/time predicates across four pages, full-result ASC/DESC ordering, filtered scoped counts, all six supported sizes plus minimum/default/max, malformed/unsupported pagination/UUID/time/sort/query input, inclusive microsecond and offset/equality/one-sided/no bounds, reversed/mixed filters, beyond-end metadata, role/status boundaries, foreign/absent detail and forged scope filters, historical actor without live User, literal machine-string matching, no read mutations and local-day UTC/DST/long-range behavior. Existing full attribution/security/JIT/Email/Tenant/provider/Asset/history suites also ran. No tests disabled or weakened. Test fixtures use isolated PostgreSQL17 Testcontainers only; no manual environment/business data. Logs/XML remain local at ignored `.cache/step9/verify.log` and backend target test reports, not credential-bearing committed evidence.

## Exact Step 9 delta

Compared with PRE-Step 9 dirty worktree, not HEAD:

- `backend/src/main/java/com/github/ksewen/kelios/audit/AuditQuery.java`
- `backend/src/main/java/com/github/ksewen/kelios/audit/AuditReads.java`
- `backend/src/main/java/com/github/ksewen/kelios/audit/AuditRecord.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/AuditController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/AuditDtos.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/AuditQueryIT.java`
- `docs/api/openapi.yaml`
- `docs/plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md`
- `docs/verification/STEP_9_AUDIT_QUERY.md`

## Limitations and stop

Frontend integration/local-time controls/page-size selector remain separately gated and unchanged. Equal timestamps intentionally carry no unique tie ordering guarantee; no speculative index, cursor/snapshot paging, new locking or mandatory UUID tie is claimed. Real UI/provider/manual business-data verification was not performed. No remaining known backend gap in the approved Q3 criteria/read surface is established by these tests; human acceptance is pending.

No schema/migration, writer/audit semantics, runtime/deployment, data reset, persistent business-data creation, staging/commit/push/PR or unrelated cleanup occurred. Existing dirty/untracked approved work is preserved. Step 9 does not authorize frontend, Phase C/D, Excel export, Email delivery or other future gates. STOP for human review.
