import type { Identity, SessionResponse, LocalLoginRequest, RoutingOrganizations } from './api-contracts';
export type { Identity } from './api-contracts';
type Session = SessionResponse;

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
  /** Presentation boundary only: login/logout change generation; ordinary refresh does not. */
  contextVersion(): number { return this.generation; }
  private install(value: unknown): Identity {
    if (!value || typeof value !== 'object') throw new ApiFailure(0, 'INVALID_AUTH_RESPONSE', 'Invalid authentication response');
    const data = value as Partial<Session>;
    const u = data.user;
    if (typeof data.accessToken !== 'string' || !data.accessToken || typeof data.expiresIn !== 'number' || data.expiresIn <= 0
      || !u || typeof u !== 'object' || typeof u.id !== 'string' || !u.id || !['GLOBAL', 'TENANT'].includes(u.scope) || !['ADMIN', 'USER'].includes(u.role)
      || !['PENDING', 'ACTIVE'].includes(u.status) || typeof u.displayName !== 'string'
      || (u.scope === 'GLOBAL') !== (u.tenantId == null) || (u.scope === 'TENANT' && (typeof u.tenantId !== 'string' || !u.tenantId))) throw new ApiFailure(0, 'INVALID_AUTH_RESPONSE', 'Invalid authentication response');
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
  async login(email: string, password: string): Promise<Identity> {
    if (this.pendingRefresh) await this.pendingRefresh;
    this.generation++;
    const command: LocalLoginRequest = { email, password };
    const response = await this.unsafe('/api/v1/auth/local/login', 'POST', command);
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
  async request<T>(path: string, method = 'GET', body?: unknown, responseType: 'json' | 'text' = 'json'): Promise<T> {
    const generation = this.generation;
    if (!path.startsWith('/api/v1/')) throw new ApiFailure(0, 'REQUEST_FAILED', 'Unsupported API path');
    if (!this.access || Date.now() >= this.expiresAt) await this.refresh();
    if (!this.access || generation !== this.generation) throw new ApiFailure(0, 'AUTHENTICATION_REQUIRED', 'Authentication required');
    const send = async () => {
      const headers: Record<string, string> = { Authorization: `Bearer ${this.access}` };
      if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
      if (path === '/api/v1/auth/local/password') {
        const csrf = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000) });
        if (!csrf.ok) throw new ApiFailure(0, 'AUTH_UNAVAILABLE', 'Authentication unavailable');
        headers['X-CSRF-Token'] = (await csrf.json() as { token: string }).token;
      }
      if (generation !== this.generation) throw new ApiFailure(0, 'SESSION_CHANGED', 'Session changed');
      return fetch(path, { method, credentials: 'same-origin', cache: 'no-store', headers, signal: AbortSignal.timeout(path === '/api/v1/asset-csv/execute' ? 300000 : path.startsWith('/api/v1/asset-csv/export?') ? 120000 : path === '/api/v1/asset-csv/preview' ? 60000 : 15000), ...(body === undefined ? {} : { body: body instanceof FormData ? body : JSON.stringify(body) }) });
    };
    let response = await send();
    if (method === 'GET' && response.status === 401 && await this.refresh()) response = await send();
    if (generation !== this.generation) throw new ApiFailure(0, 'SESSION_CHANGED', 'Session changed');
    if (!response.ok) {
      let code: string | undefined;
      try {
        const problem: unknown = await response.json();
        if (problem && typeof problem === 'object' && 'code' in problem && typeof problem.code === 'string' && problem.code) code = problem.code;
      } catch { /* Status remains authoritative; raw response content is never displayed. */ }
      throw new ApiFailure(response.status, code, 'Request failed. Review before resubmitting.');
    }
    if (response.status === 204) return undefined as T;
    const text = await response.text(); return responseType === 'text' ? text as T : text ? JSON.parse(text) as T : undefined as T;
  }
  async organizations(page = 0, q = ''): Promise<RoutingOrganizations> {
    const generation=this.generation,query=q.trim();
    if(!Number.isInteger(page)||page<0||page>100000||query.length>128)throw new ApiFailure(400,'INVALID_INPUT','Invalid organization query');
    const response = await fetch(`/api/v1/auth/oidc/organizations?${new URLSearchParams({page:String(page),size:'20',q:query})}`, {credentials:'same-origin',cache:'no-store',signal:AbortSignal.timeout(10000)});
    if(generation!==this.generation)throw new ApiFailure(0,'SESSION_CHANGED','Session changed');
    if (!response.ok) throw new ApiFailure(response.status,'PROVIDER_FAILED','Organizations unavailable');
    const data=await response.json() as Partial<RoutingOrganizations>;
    if(generation!==this.generation)throw new ApiFailure(0,'SESSION_CHANGED','Session changed');
    if(!data||typeof data!=='object'||!Number.isSafeInteger(data.totalElements)||data.totalElements! < 0||data.page!==page||!Number.isInteger(data.size)||data.size! < 1||data.size! > 20
      ||!Array.isArray(data.items)||data.items.length>data.size!||!data.items.every(item=>item&&typeof item.name==='string'&&typeof item.routingId==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item.routingId)))throw new ApiFailure(0,'PROVIDER_FAILED','Invalid organization response');
    return {items:data.items,totalElements:data.totalElements!,page:data.page,size:data.size!};
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
  constructor(readonly status: number, readonly code: string | undefined, detail: string) { super(`${code ?? `HTTP_${status}`}: ${detail}`); }
}
export const authSession = new AuthSession();
