/** Endpoint-owned transport contracts for backend 939c737. No persistence entities or secret response fields. */
export type UUID = string;
export type Instant = string;
export type Scope = 'GLOBAL' | 'TENANT';
export type Role = 'ADMIN' | 'USER';
export type UserStatus = 'PENDING' | 'ACTIVE' | 'DISABLED';
export type Visibility = 'GLOBAL' | 'TENANT';
export type Direction = 'asc' | 'desc';
export type Identity = { id: UUID; scope: Scope; tenantId: UUID | null; role: Role; status: UserStatus; displayName: string };
export type SessionResponse = { accessToken: string; expiresIn: number; user: Identity };
export type Page<T> = { items: T[]; page: number; size: number; totalElements: number };
export type Reference = { id: UUID; name: string };
export type Metadata = {
  createdBy: UUID; createdAt: Instant; updatedBy: UUID; updatedAt: Instant;
  createdByDisplayName: string | null; updatedByDisplayName: string | null;
};
export type CreatedResource = { id: UUID };
export type CatalogLabelItem = { id: UUID; name: string; visibility: Visibility; tenantId: UUID | null; tenant: Reference | null; metadata: Metadata };
export type CatalogLabelDetail = CatalogLabelItem & { deleted: boolean; deletedAt: Instant | null; deletedBy: UUID | null };
export type ProductItem = CatalogLabelItem & { manufacturerNumber: string | null; categoryId: UUID; category: Reference; manufacturerId: UUID; manufacturer: Reference };
export type ProductDetail = ProductItem & { deleted: boolean; deletedAt: Instant | null; deletedBy: UUID | null };
export type PlaceItem = { id: UUID; tenantId: UUID; tenant: Reference; name: string; metadata: Metadata };
export type PlaceDetail = PlaceItem & { note: string | null; deleted: boolean; deletedAt: Instant | null; deletedBy: UUID | null };
export type UserState = Identity;
export type UserItem = UserState & { tenant: Reference | null; metadata: Metadata };
export type UserDetail = UserItem & { globalAccount: { email: string; emailVerifiedAt: Instant | null } | null };
export type TenantItem = { id: UUID; name: string; metadata: Metadata };
export type TenantDetail = TenantItem;
/** Tenant POST returns a JSON UUID string, unlike Catalog/Place POST {id}. */
export type TenantCreated = UUID;
export type OidcConfigurationDetail = {
  tenantId: UUID; issuer: string; clientId: string; secretConfigured: boolean; enabled: boolean;
  version: number; allowedGroupIds: UUID[]; callbackUri: string; tenant: Reference; metadata: Metadata;
};
export type RoutingOrganization = { routingId: UUID; name: string };
export type RoutingOrganizations = Page<RoutingOrganization>;
export type Lifecycle = 'REGISTERED' | 'IN_STOCK' | 'DEPLOYED' | 'DEREGISTERED';
export type Condition = 'UNKNOWN' | 'WORKING' | 'DEFECTIVE' | 'UNUSABLE';
export type DeregistrationReason = 'TRANSFER' | 'DISPOSAL' | 'LOST' | 'ERROR';
/** Asset command responses retain flat state; collection/detail reads add safe associations. */
export type AssetState = {
  id: UUID; tenantId: UUID; assetTag: string; categoryId: UUID; manufacturerId: UUID; productId: UUID | null;
  locationId: UUID; ownerId: UUID | null; lifecycle: Lifecycle; condition: Condition; loanable: boolean;
  serialNumber: string | null; externalInventoryNumber: string | null; note: string | null; localIdentifier: string | null;
  version: number; deletedAt: Instant | null; loanedTo: string | null; loanedAt: Instant | null;
  expectedReturnAt: Instant | null; loanNote: string | null;
  createdAt: Instant; createdBy: UUID; updatedAt: Instant; updatedBy: UUID;
  createdByDisplayName: string | null; updatedByDisplayName: string | null;
};
export type AssetAssociations = { tenant: Reference; category: Reference; manufacturer: Reference; product: Reference | null; location: Reference; owner: Reference | null };
export type AssetItem = Omit<AssetState, 'note' | 'deletedAt' | 'loanNote'> & AssetAssociations;
export type AssetDetail = AssetState & AssetAssociations;
export type AssetEvent = {
  id: UUID; assetId: UUID; tenantId: UUID; type: string; occurredAt: Instant; createdAt: Instant;
  actorId: UUID; actorDisplayName: string; data: Record<string, unknown>; note: string | null;
};
export type AuditItem = { id: UUID; tenantId: UUID | null; actorId: UUID; actorDisplayName: string; action: string; targetType: string; targetId: UUID; occurredAt: Instant; data: Record<string, unknown> };
export type MergePreview = { productCount: number; assetCount: number; affectedTenants: { tenantId: UUID | null; referenceCount: number }[]; blockers: string[] };

// Request DTOs contain mutable input only. Defining Email DTOs does not migrate the F1a login client/UI.
export type LocalLoginRequest = { email: string; password: string };
export type GlobalUserCreate = { email: string; displayName: string; password: string };
export type GlobalCredentialCommand = { email: string; password: string };
export type GlobalEmailCommand = { email: string };
export type PasswordChangeCommand = { currentPassword: string; newPassword: string };
export type UserRoleCommand = { role: Role };
export type TenantCreate = { name: string };
export type CatalogLabelCommand = { name: string };
export type ProductCommand = { name: string; categoryId: UUID; manufacturerId: UUID; manufacturerNumber?: string | null };
export type PlaceCommand = { name: string; note?: string | null };
export type MergeCommand = { sourceId: UUID; targetId: UUID };
export type OidcConfigureCommand = { issuer: string; clientId: string; clientSecret?: string | null; enabled: boolean; version?: number | null; allowedGroupIds: UUID[] };
export type OidcDeleteQuery = { version: number };
export type CatalogSelection = { productId: UUID; categoryId?: never; manufacturerId?: never } | { productId?: never; categoryId: UUID; manufacturerId: UUID };
export type AssetRegistration = CatalogSelection & { locationId: UUID; ownerId?: UUID | null; loanable: boolean; serialNumber?: string | null; externalInventoryNumber?: string | null; note?: string | null; localIdentifier?: string | null };
export type AssetTextCommand = { expectedVersion: number; serialNumber?: string | null; externalInventoryNumber?: string | null; note?: string | null; localIdentifier?: string | null };
export type AssetCatalogCommand = CatalogSelection & { expectedVersion: number };
export type AssetLoanabilityCommand = { expectedVersion: number; loanable: boolean };
export type AssetLocationCommand = { expectedVersion: number; locationId: UUID };
export type AssetOwnerCommand = { expectedVersion: number; ownerId: UUID | null };
export type AssetLifecycleCommand = { expectedVersion: number; target: Lifecycle; reason?: DeregistrationReason | null; note?: string | null };
export type AssetConditionCommand = { expectedVersion: number; target: Condition; note?: string | null };
export type AssetLoanCommand = { expectedVersion: number; borrower: string; expectedReturnAt?: Instant | null; loanNote?: string | null };
export type AssetReturnCommand = { expectedVersion: number };

// Closed endpoint criteria. Pending/applied UI behavior remains owned by later gates.
export type PagingQuery = { page?: number; size?: number };
export type CatalogLabelQuery = PagingQuery & { q?: string; visibility?: Visibility; sort?: 'name' | 'visibility'; direction?: Direction };
export type CategoryQuery = Omit<CatalogLabelQuery, 'sort'> & { sort?: 'name' | 'visibility' | 'tenantName' };
export type ProductQuery = PagingQuery & { q?: string; visibility?: Visibility; categoryId?: UUID; manufacturerId?: UUID; manufacturerNumber?: string; sort?: 'name' | 'visibility' | 'manufacturerNumber'; direction?: Direction };
export type PlaceQuery = PagingQuery & { q?: string; sort?: 'name'; direction?: Direction };
export type UserQuery = PagingQuery & { q?: string; scope?: Scope; role?: Role; status?: UserStatus; tenantId?: UUID; sort?: 'displayName' | 'scope' | 'role' | 'status'; direction?: Direction };
export type AuditQuery = PagingQuery & { action?: string; targetType?: string; targetId?: UUID; actorId?: UUID; tenantId?: UUID; globalOnly?: boolean; from?: Instant; until?: Instant; sort?: 'occurredAt'; direction?: Direction };
export type AssetQuery = PagingQuery & { q?: string; lifecycle?: Lifecycle; condition?: Condition; loaned?: boolean; loanable?: boolean; locationId?: UUID; ownerId?: UUID; categoryId?: UUID; manufacturerId?: UUID; productId?: UUID; localIdentifier?: string; serialNumber?: string; externalInventoryNumber?: string; sort?: 'tag' | 'createdAt' | 'lifecycle' | 'condition'; direction?: Direction };
export type AssetHistoryQuery = PagingQuery;
export type RoutingQuery = PagingQuery & { q?: string };
