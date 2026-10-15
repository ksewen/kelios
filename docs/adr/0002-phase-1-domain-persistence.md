# ADR 0002: Phase 1 persistence and operation-specific transactions

**Historical accepted implementation record.** Preserve the decisions and evidence below as implementation-time history. Current normative Domain/identity/query requirements are in [DOMAIN.md](../../DOMAIN.md) and [ARCHITECTURE.md](../../ARCHITECTURE.md); separately approved future work follows [the consolidated implementation gates](../plans/COLLECTION_QUERY_IMPLEMENTATION_PLAN.md). Earlier login identifiers/password minima, paging defaults, deleted selectors, sort ties and phase-specific exclusions are not competing target requirements.


Status: Accepted by the user on 2026-10-10 through approval of the revised [Phase 1 plan](../history/phase0-4.md). Scope: Phase 1 internal domain foundation only.

## Context and authority

[Domain](../../DOMAIN.md) is authoritative. The reviewed revision removed Product Merge and clarified independent mutation operations, retained-reference membership, registration inputs, tags, minimal identity, immutable historical actors, and state graphs. This ADR records implementation choices, not new business rules. [ADR 0001](0001-phase-0-foundation.md), V1, the frontend, and Phase 0 evidence remain intact.

## Decisions

- Keep Java 25, Spring Boot 4.1.1, Maven 3.9.16 Wrapper and independent images. Add Boot-managed Spring Data JPA/Hibernate, validating Flyway schema (`ddl-auto=validate`, open-in-view disabled). No schema generation, public business repositories, business controllers, or authentication substitute.
- Map the ten domain entities in their specified modules. Inventory depends on Catalog/Tenant application services and Audit services. Catalog uses a narrow CatalogUsage port implemented by Inventory; repositories are not shared. The internal inventory CatalogMergeService explicitly coordinates the cross-module transaction.
- V2 adds the domain tables, counter, seeds, constraints and indexes. V3 adds only AssetEvent append-only and reserved UNKNOWN row protections. No Product usage flag, Asset identity trigger, generic purge trigger, or SQL implementation of application mutation rules.
- Declarative FKs/checks/unique keys protect structural references, scope/nullability, enum values, required strings and tags. Composite FKs enforce same-Tenant Location/Owner/Event ownership and Product-derived Category/Manufacturer equality. The latter is deferred, with NO ACTION rather than a history-bypassing cascade, allowing explicit atomic Merge updates.
- Normal Spring transactions use PostgreSQL READ COMMITTED. Reference validation holds FOR SHARE on relevant parents; guarded mutation holds FOR UPDATE, followed by fresh reference/history queries. Merge orders source/target UUIDs and affected row sets locally. Asset uses JPA @Version; no universal isolation policy, lock hierarchy, locking abstraction or automatic transaction retry.
- The pool initializes `lock_timeout='10s'`; supported operations expose lock/deadlock conflicts and roll back completely. Spring's PostgreSQL SQL-error-code translator classifies 55P03/40P01; Asset flush translates optimistic conflicts consistently. Callers may explicitly submit a fresh command. No stale-version replay.
- UTC registration timestamp drives daily atomic counter UPSERT/RETURNING and yyyyMMdd-NNN formatting. Sequence width expands, uniqueness is independently constrained, bigint overflow fails, and no gapless allocation is promised.
- Product editing queries retained Asset.product_id and indexed `data.before.productId` / `data.after.productId` in immutable JSONB events. IDs are server-written canonical UUID strings or JSON null. Registration and supported relationship updates write this history atomically; no live User FK or denormalized usage latch is needed.
- AssetEvent UPDATE/DELETE/TRUNCATE bindings use one rejecting function scoped to that table. UNKNOWN guards use reserved OLD-row UUIDs on Category/Manufacturer only. CHECK/FK/UNIQUE and read-only mappings cannot alone guarantee those two protections against ordinary bulk DML. No other production triggers are introduced.
- Immutable Asset UUID/Tenant/tag are protected by JPA identity/non-updatable mappings and command shapes. Business services expose soft deletion only. Arbitrary privileged SQL/DDL is outside the supported command boundary.
- Commands express intent through reference IDs and mutable input. Product-backed registration has no redundant Category/Manufacturer inputs. Trusted actor context is internal, with value snapshots for history; authenticated context and HTTP authorization remain Phase 2.
- The state-machine engine is a generic immutable graph with no persistence/domain/security dependency. Inventory defines only the approved graphs in Phase 1; lifecycle/condition/loan workflow services remain Phase 3.

## Verification and image build boundary

JUnit unit tests run under Surefire, required PostgreSQL Testcontainers tests under Failsafe in ordinary `./mvnw -B verify`. There is no H2 replacement or disabled-without-Docker setting. Full verification fails when Docker is unavailable.

An isolated Docker build has no host Docker socket and cannot launch Testcontainers. Its Maven command explicitly uses `-DskipITs verify`, compiling all tests and running unit verification; it is not PostgreSQL evidence. Full host verification is a separate required release gate before image/deployment smoke checks. No privileged/nested Docker or daemon socket mount is added to the builder. Runtime remains a non-root JRE with no Maven/compiler.

Actual commands, results and limitations are recorded separately in [Phase 1 evidence](../history/phase0-4.md). Authentication, authorization, business REST/UI, transition/loan workflows, privacy policy and all other deferred features remain outside this ADR.
