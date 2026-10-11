export type Identity = {
  id: string; scope: 'GLOBAL' | 'TENANT'; tenantId: string | null;
  role: 'ADMIN' | 'USER'; status: 'PENDING' | 'ACTIVE' | 'DISABLED'; displayName: string;
};
type Session = { accessToken: string; expiresIn: number; user: Identity };

/** Only this object holds access tokens. Refresh is an HttpOnly cookie managed by the backend. */
export class AuthSession {
  private access: string | null = null;
  private user: Identity | null = null;
  private pendingRefresh: Promise<Identity | null> | null = null;
  private generation = 0;
  private expiresAt = 0;
  private listeners=new Set<(user:Identity|null)=>void>();
  subscribe(listener:(user:Identity|null)=>void):()=>void {this.listeners.add(listener);return()=>{this.listeners.delete(listener);};}
  private changed(){for(const listener of this.listeners)listener(this.user);}
  current(): Identity | null { return this.user; }
  private install(data: Session): Identity {
    const u = data.user;
    if (typeof data.accessToken !== 'string' || !data.accessToken || typeof data.expiresIn !== 'number' || data.expiresIn <= 0
      || !u || !['GLOBAL', 'TENANT'].includes(u.scope) || !['ADMIN', 'USER'].includes(u.role)
      || !['PENDING', 'ACTIVE'].includes(u.status) || typeof u.displayName !== 'string'
      || (u.scope === 'GLOBAL') !== (u.tenantId == null)) throw new ApiFailure(0, 'INVALID_AUTH_RESPONSE', 'Invalid authentication response');
    this.access = data.accessToken; this.user = u; this.expiresAt = Date.now() + data.expiresIn * 1000;this.changed();return u;
  }
  private async unsafe(path: string, method: string, body?: unknown): Promise<Response> {
    const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000) });
    if (!csrfResponse.ok) throw new ApiFailure(0, 'AUTH_UNAVAILABLE', 'Authentication unavailable');
    const csrf: unknown = await csrfResponse.json();
    if (!csrf || typeof csrf !== 'object' || !('token' in csrf) || typeof csrf.token !== 'string') throw new ApiFailure(0, 'AUTH_UNAVAILABLE', 'Authentication unavailable');
    return fetch(path, { method, credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(15000),
      // Cookie-authenticated refresh must work after the previous Access JWT expires.
      headers: { 'X-CSRF-Token': csrf.token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
  }
  async login(login: string, password: string): Promise<Identity> {
    if (this.pendingRefresh) await this.pendingRefresh;
    this.generation++;
    const response = await this.unsafe('/api/v1/auth/local/login', 'POST', { login, password });
    if (!response.ok) throw new ApiFailure(response.status, response.status === 403 ? 'ACCESS_DENIED' : response.status === 429 ? 'THROTTLED' : 'LOGIN_FAILED', 'Login failed');
    return this.install(await response.json() as Session);
  }
  refresh(): Promise<Identity | null> {
    if (this.pendingRefresh) return this.pendingRefresh;
    const generation = this.generation;
    this.pendingRefresh = (async () => {
      try {
        const response = await this.unsafe('/api/v1/auth/session/refresh', 'POST');
        if (!response.ok) throw new ApiFailure(0, 'AUTHENTICATION_REQUIRED', 'Refresh denied');
        const data = await response.json() as Session;
        return generation === this.generation ? this.install(data) : null;
      } catch { if (generation === this.generation) { this.access = null; this.user = null;this.changed(); } return null; }
      finally { this.pendingRefresh = null; }
    })();
    return this.pendingRefresh;
  }
  async me(): Promise<Identity> {
    if (!this.access || Date.now() >= this.expiresAt) await this.refresh();
    let response = await this.authorizedMe();
    if (response.status === 401 && await this.refresh()) response = await this.authorizedMe();
    if (!response.ok) throw new ApiFailure(0, 'AUTHENTICATION_REQUIRED', 'Authentication required');
    return await response.json() as Identity;
  }
  private authorizedMe(): Promise<Response> {
    return fetch('/api/v1/auth/me', { credentials: 'same-origin', cache: 'no-store', headers: this.access ? { Authorization: `Bearer ${this.access}` } : {}, signal: AbortSignal.timeout(10000) });
  }
  /** Reads may refresh once; mutations are never replayed after an uncertain response. */
  async request<T>(path: string, method = 'GET', body?: unknown): Promise<T> {
    const generation = this.generation;
    if (!path.startsWith('/api/v1/')) throw new ApiFailure(0, 'REQUEST_FAILED', 'Unsupported API path');
    if (!this.access || Date.now() >= this.expiresAt) await this.refresh();
    if (!this.access || generation !== this.generation) throw new ApiFailure(0, 'AUTHENTICATION_REQUIRED', 'Authentication required');
    const send = async () => {
      const headers: Record<string, string> = { Authorization: `Bearer ${this.access}` };
      if (body !== undefined) headers['Content-Type'] = 'application/json';
      if (path === '/api/v1/auth/local/password') {
        const csrf = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000) });
        if (!csrf.ok) throw new ApiFailure(0, 'AUTH_UNAVAILABLE', 'Authentication unavailable');
        headers['X-CSRF-Token'] = (await csrf.json() as { token: string }).token;
      }
      if (generation !== this.generation) throw new ApiFailure(0, 'SESSION_CHANGED', 'Session changed');
      return fetch(path, { method, credentials: 'same-origin', cache: 'no-store', headers, signal: AbortSignal.timeout(15000), ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
    };
    let response = await send();
    if (method === 'GET' && response.status === 401 && await this.refresh()) response = await send();
    if (generation !== this.generation) throw new ApiFailure(0, 'SESSION_CHANGED', 'Session changed');
    if (!response.ok) {
      let problem: { code?: string; detail?: string } = {};
      try { problem = await response.json() as typeof problem; } catch { /* Sanitized fallback. */ }
      throw new ApiFailure(response.status, problem.code ?? 'REQUEST_FAILED', 'Request failed. Review before resubmitting.');
    }
    if (response.status === 204) return undefined as T;
    const text = await response.text(); return text ? JSON.parse(text) as T : undefined as T;
  }
  async organizations(page = 0, q = ''): Promise<{items: {routingId: string; name: string}[]; totalElements: number}> {
    const response = await fetch(`/api/v1/auth/oidc/organizations?${new URLSearchParams({page:String(page),size:'25',q})}`, {credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(10000)});
    if (!response.ok) throw new ApiFailure(response.status,'PROVIDER_FAILED','Organizations unavailable');
    const data=await response.json() as {items?:{routingId:string;name:string}[];totalElements?:number};
    if(!Array.isArray(data.items)||data.items.length>25||typeof data.totalElements!=='number'||data.totalElements<0||!data.items.every(item=>item&&typeof item.name==='string'&&typeof item.routingId==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item.routingId)))throw new ApiFailure(0,'PROVIDER_FAILED','Invalid organization response');
    return {items:data.items,totalElements:data.totalElements};
  }
  async startTenant(tenantId: string): Promise<string> {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(tenantId)) throw new ApiFailure(0, 'INVALID_TENANT', 'Invalid organization route');
    const response = await this.unsafe(`/api/v1/auth/oidc/${tenantId}/start`, 'POST');
    if (!response.ok) throw new ApiFailure(0, 'PROVIDER_FAILED', 'Tenant identity provider unavailable or access denied');
    const data: unknown = await response.json();
    if (!data || typeof data !== 'object' || !('authorizationUrl' in data) || typeof data.authorizationUrl !== 'string') throw new ApiFailure(0, 'PROVIDER_FAILED', 'Invalid provider response');
    const url = new URL(data.authorizationUrl); if (url.protocol !== 'https:' || url.username || url.password) throw new ApiFailure(0, 'PROVIDER_FAILED', 'Invalid provider response');
    return url.href;
  }
  async logout(): Promise<void> {
    this.generation++; this.access = null; this.user = null; this.expiresAt = 0;this.changed();
    // Finish an outstanding refresh before clearing cookies, preventing a later response from restoring them.
    if (this.pendingRefresh) await this.pendingRefresh;
    const response = await this.unsafe('/api/v1/auth/session', 'DELETE');
    if (!response.ok) throw new ApiFailure(0, 'LOGOUT_FAILED', 'Signed out locally; cookie clearing failed. Retry logout.');
  }
}

export class ApiFailure extends Error {
  constructor(readonly status: number, readonly code: string, detail: string) { super(`${code}: ${detail}`); }
}
export const authSession = new AuthSession();
