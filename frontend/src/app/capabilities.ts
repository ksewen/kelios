import type { Identity } from '../auth';
/** UI request/destination policy only; backend scope remains authoritative. */
export function capabilities(user: Identity) {
  const active = user.status === 'ACTIVE';
  const global = active && user.scope === 'GLOBAL';
  const admin = active && user.role === 'ADMIN';
  return { global, admin, globalAdmin: global && admin, tenantCollection: global,
    tenantDetail: global || admin, oidc: admin, users: admin, audit: admin };
}
export function tenantBusinessContext(path: string, user: Identity): string {
  if (user.scope === 'TENANT') return user.tenantId!;
  return /^\/tenants\/([0-9a-f-]+)\/[^/]+/i.exec(path)?.[1] ?? '';
}
