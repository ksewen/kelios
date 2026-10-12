# ADR 0005 — Phase 4 metadata, bilingual presentation and stabilization

**Historical accepted implementation record.** Preserve the decisions and evidence below as implementation-time history. Current normative Domain/identity/query requirements are in [DOMAIN.md](../../DOMAIN.md) and [ARCHITECTURE.md](../../ARCHITECTURE.md); separately approved future work follows [the consolidated implementation gates](../plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md). Earlier login identifiers/password minima, paging defaults, deleted selectors, sort ties and phase-specific exclusions are not competing target requirements.


Status: Implementation explicitly approved by the user. Phase 4 user acceptance pending implementation/evidence review.

## Authority

DOMAIN.md, ARCHITECTURE.md and docs/plans/PHASE_4_PLAN.md are authoritative. L1–L19/N1/M1/S1 and I18N-1–I18N-13 are resolved. Preserve accepted Phases 0–3, historical ADRs and evidence. Real IServ interoperability remains pending manual verification, not a Phase 4 blocker.

## Decisions

- Add V6 with a nullable Asset local_identifier text column, no UNIQUE/dedicated index/table or backfill. Existing values become null; V1–V5 unchanged. Small field-specific server normalization/validation enforces Java optional-text semantics, max128 Unicode code points and exclusion of Cc/U+2028/U+2029. PostgreSQL CHECKs cover length and explicit forbidden character ranges without a normalization trigger.
- Extend registration, ordinary four-field replacement metadata, View and every authoritative snapshot. Omission/null clears metadata; no PATCH/client-compatibility exceptions. Existing REGISTERED/UPDATED history remains atomic with @Version, no new event/AuditLog type or historical JSON rewriting.
- Extend bounded escaped parameterized q with localIdentifier; no filter/sort/index. Preserve case, internal whitespace, all duplicate values and independent identity concepts. No transfer copy, terminal edit freeze or public Asset deletion.
- Static en/de resources with i18next/react-i18next, one shared instance and semantic keys. Pinned i18next 26.4.3 and react-i18next 17.0.16: their published peer ranges support the existing React and TypeScript toolchain; host and independent Docker frontend builds verify compatibility. No remote translator/detection plugin/server locale/storage/API.
- Valid explicit en/de browser presentation preference precedes ordered supported browser preference; regional variants map to base, default English. Public switch persists only language in localStorage. Access stays memory-only, Refresh HttpOnly; no tokens/credentials in browser storage.
- Translate frontend-owned text and display mappings, not user-entered data/history or API machine values. Known stable errors map to localized UI; unknown safe errors get generic fallback, no raw server exception text. Intl formatting changes presentation only. No unsafe HTML.
- Invariant coverage matrix and focused new tests; full real PostgreSQL, frontend/browser and same-image en/de × blue/green regression. Isolated fresh deployment and disposable PostgreSQL backup/restore with required external encryption keys establish operational evidence; no backup service/retention/RPO/RTO/HA policy.
- Preserve READ COMMITTED, targeted locks/@Version and caller-visible conflicts; scalar non-unique identifier needs no new cross-row locking or retry infrastructure.

## Evidence and stop boundary

Actual package versions, commands/counts/images/migration/concurrency/restore outcomes and limits belong in docs/verification/PHASE_4.md. Docker builder unit verification does not replace host PostgreSQL Testcontainers. No real IServ compatibility claim, Phase 5, commit/push/PR.
