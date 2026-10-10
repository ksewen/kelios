# Phase 3 file manifest

No commit/push; all repository files were already untracked at entry, so this records the actual Phase 3 change set rather than claiming a Git diff. Evidence artifacts are listed separately in PHASE_3.md.

## Backend — created

- `backend/src/main/java/com/github/ksewen/kelios/audit/AuditReads.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/DeregistrationReason.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/InventoryReads.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/BusinessFailure.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/JdbcRows.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/ListInput.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/Page.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/InventoryOperations.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/CommandInput.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/AssetController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/CatalogController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/PlaceController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/AuditController.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/BusinessProblems.java`

## Backend — changed

- `backend/src/main/java/com/github/ksewen/kelios/audit/AuditLog.java`
- `backend/src/main/java/com/github/ksewen/kelios/audit/AuditService.java`
- `backend/src/main/java/com/github/ksewen/kelios/catalog/CatalogService.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/AssetService.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/AssetHistory.java`
- `backend/src/main/java/com/github/ksewen/kelios/inventory/PlaceService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/AuthenticationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcConfigurationService.java`
- `backend/src/main/java/com/github/ksewen/kelios/identity/auth/OidcProvisioning.java`
- `backend/src/main/java/com/github/ksewen/kelios/tenancy/TenantService.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/AuthProblems.java`
- `backend/src/main/java/com/github/ksewen/kelios/platform/security/SecurityConfiguration.java`

## Frontend — created

- `frontend/src/InventoryApp.tsx`
- `frontend/src/inventory.ts`

## Frontend — changed

- `frontend/src/App.tsx`
- `frontend/src/Authentication.tsx`
- `frontend/src/auth.ts`

## Migration — created

- `backend/src/main/resources/db/migration/V5__audit_tenant_attribution.sql`

## Tests/configuration

- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/Phase3InventoryIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/platform/security/BusinessProblemsTest.java`
- `backend/src/test/java/com/github/ksewen/kelios/inventory/DomainFoundationIT.java`
- `backend/src/test/java/com/github/ksewen/kelios/identity/auth/IdentityAuthenticationIT.java`
- `frontend/src/inventory.test.ts`
- `frontend/src/auth.test.ts`
- `frontend/tests/inventory.spec.ts`
- `frontend/playwright.config.ts`

## Documentation/contract

- `docs/adr/0004-phase-3-inventory-and-target-owned-audit.md`
- `docs/INVENTORY.md`
- `docs/api/openapi.yaml`
- `ARCHITECTURE.md`
- `README.md`
- `docs/plans/PHASE_3_PLAN.md`
- `docs/verification/PHASE_3.md`

## Verification scripts — created

- `scripts/smoke-phase3.sh`
- `scripts/smoke-phase3-foundation.sh`
