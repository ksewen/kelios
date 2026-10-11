import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthSession } from './auth';

const user = { id: 'a', scope: 'GLOBAL', tenantId: null, role: 'ADMIN', status: 'ACTIVE', displayName: 'Admin' };
function response(body: unknown, status = 200) { return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } }); }
describe('stateless browser session integration', () => {
  let fetcher: ReturnType<typeof vi.fn>;
  beforeEach(() => { fetcher = vi.fn(); vi.stubGlobal('fetch', fetcher); });
  afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
  it('uses memory bearer access and never returns refresh credentials to JavaScript', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ accessToken: 'access', expiresIn: 900, user })).mockResolvedValueOnce(response(user));
    const session = new AuthSession(); await session.login('admin', 'fixture'); await session.me();
    expect(fetcher.mock.calls[2][1].headers).toEqual({ Authorization: 'Bearer access' });
    expect(fetcher.mock.calls[1][1].headers['X-CSRF-Token']).toBe('proof');
    expect(fetcher.mock.calls[1][1].credentials).toBe('same-origin');
  });
  it('reload restoration coordinates concurrent refresh', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ accessToken: 'access', expiresIn: 900, user }));
    const session = new AuthSession(); const first = session.refresh(); const second = session.refresh();
    expect(first).toBe(second); await Promise.all([first, second]); expect(fetcher).toHaveBeenCalledTimes(2); expect(session.current()?.status).toBe('ACTIVE');
  });
  it('pending restoration remains pending and fetches no business endpoints', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ accessToken: 'access', expiresIn: 900, user: { ...user, scope: 'TENANT', tenantId: 'tenant', role: 'USER', status: 'PENDING' } }));
    expect((await new AuthSession().refresh())?.status).toBe('PENDING'); expect(fetcher.mock.calls.every(([path]) => path.startsWith('/api/v1/auth/'))).toBe(true);
  });
  it('disabled refresh clears local access', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({}, 403)); expect(await new AuthSession().refresh()).toBeNull();
  });
  it('refreshes after Access expiry without sending the expired Bearer', async () => {
    const now = Date.now(); const time = vi.spyOn(Date, 'now').mockReturnValue(now);
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ accessToken: 'old-access', expiresIn: 900, user }))
      .mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ accessToken: 'fresh-access', expiresIn: 900, user })).mockResolvedValueOnce(response(user));
    const session = new AuthSession(); await session.login('admin', 'fixture'); time.mockReturnValue(now + 901000); await session.me();
    expect(fetcher.mock.calls[3][0]).toBe('/api/v1/auth/session/refresh');
    expect(fetcher.mock.calls[3][1].headers.Authorization).toBeUndefined();
    expect(fetcher.mock.calls[4][1].headers.Authorization).toBe('Bearer fresh-access');
  });
  it('logout discards memory and asks the backend to clear its cookie', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ accessToken: 'access', expiresIn: 900, user }))
      .mockResolvedValueOnce(response({ token: 'bound-proof' })).mockResolvedValueOnce(response({}));
    const session = new AuthSession(); await session.login('admin', 'fixture'); await session.logout(); expect(session.current()).toBeNull();
    expect(fetcher.mock.calls[3][0]).toBe('/api/v1/auth/session'); expect(fetcher.mock.calls[3][1].method).toBe('DELETE'); expect(fetcher.mock.calls[3][1].headers.Authorization).toBeUndefined();
  });
  it('uses only the configured backend start flow and validates provider navigation', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ authorizationUrl: 'https://provider.example/authorize?state=s' }));
    const session = new AuthSession(); expect(await session.startTenant('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')).toMatch(/^https:/);
    expect(fetcher.mock.calls[1][0]).toBe('/api/v1/auth/oidc/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/start');
    await expect(session.startTenant('../other')).rejects.toThrow();
  });
  it('rejects non-HTTPS provider shortcuts', async () => {
    fetcher.mockResolvedValueOnce(response({ token: 'proof' })).mockResolvedValueOnce(response({ authorizationUrl: 'http://provider.example/authorize' }));
    await expect(new AuthSession().startTenant('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa')).rejects.toThrow();
  });
});

describe('Phase 3 safe Bearer command transport', () => {
  let fetcher: ReturnType<typeof vi.fn>;
  beforeEach(() => { fetcher=vi.fn();vi.stubGlobal('fetch',fetcher); });
  afterEach(() => {vi.unstubAllGlobals();vi.restoreAllMocks();});
  async function loggedIn(){fetcher.mockResolvedValueOnce(response({token:'proof'})).mockResolvedValueOnce(response({accessToken:'access',expiresIn:900,user}));const s=new AuthSession();await s.login('admin','fixture');return s;}
  it('uses the shared memory access for business reads and strict commands',async()=>{const s=await loggedIn();fetcher.mockResolvedValueOnce(response({id:'a'}));await s.request('/api/v1/assets/a','PUT',{expectedVersion:1,note:'draft'});expect(fetcher.mock.calls[2][1].headers.Authorization).toBe('Bearer access');expect(JSON.parse(fetcher.mock.calls[2][1].body)).toEqual({expectedVersion:1,note:'draft'});});
  it.each([409,401,503])('does not replay a mutation after HTTP %i',async status=>{const s=await loggedIn();fetcher.mockResolvedValueOnce(response({code:'CONFLICT',detail:'Review'},status));await expect(s.request('/api/v1/assets/a/metadata','PUT',{expectedVersion:0})).rejects.toThrow('CONFLICT');expect(fetcher).toHaveBeenCalledTimes(3);});
  it('does not replay an ambiguous network failure',async()=>{const s=await loggedIn();fetcher.mockRejectedValueOnce(new Error('Network'));await expect(s.request('/api/v1/assets/a/return','POST',{expectedVersion:1})).rejects.toThrow('Network');expect(fetcher).toHaveBeenCalledTimes(3);});
  it('discards an in-flight read when logout changes the session generation',async()=>{const s=await loggedIn();let resolve!: (v:Response)=>void;fetcher.mockImplementationOnce(()=>new Promise<Response>(r=>{resolve=r;})).mockResolvedValueOnce(response({token:'proof'})).mockResolvedValueOnce(new Response(null,{status:204}));const read=s.request('/api/v1/assets');await s.logout();resolve(response({items:[]}));await expect(read).rejects.toThrow('Session changed');});
  it('protects own password changes with CSRF plus memory Bearer',async()=>{const s=await loggedIn();fetcher.mockResolvedValueOnce(response({token:'csrf'})).mockResolvedValueOnce(new Response(null,{status:204}));await s.request('/api/v1/auth/local/password','PUT',{currentPassword:'old',newPassword:'new'});expect(fetcher.mock.calls[3][1].headers).toEqual({Authorization:'Bearer access','Content-Type':'application/json','X-CSRF-Token':'csrf'});});
});

describe('anonymous organization routing',()=>{
 afterEach(()=>vi.unstubAllGlobals());
 it('uses bounded public discovery without Bearer credentials or identity guesses',async()=>{
  const directory={items:[{routingId:'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',name:'KBS'}],totalElements:1};const fetcher=vi.fn().mockResolvedValue(response(directory));vi.stubGlobal('fetch',fetcher);expect(await new AuthSession().organizations(0,'KBS')).toEqual(directory);expect(fetcher.mock.calls[0][0]).toBe('/api/v1/auth/oidc/organizations?page=0&size=25&q=KBS');expect(fetcher.mock.calls[0][1].headers).toBeUndefined();
 });
 it('rejects malformed routing data instead of navigating using it',async()=>{vi.stubGlobal('fetch',vi.fn().mockResolvedValue(response({items:[{routingId:'../other',name:'KBS'}],totalElements:1})));await expect(new AuthSession().organizations()).rejects.toThrow('PROVIDER_FAILED');});
});

describe('shared presentation session state',()=>{
 afterEach(()=>vi.unstubAllGlobals());
 it('notifies the shell when normal refresh loses authentication, without a business-request revocation lookup',async()=>{
  const fetcher=vi.fn().mockResolvedValueOnce(response({token:'proof'})).mockResolvedValueOnce(response({accessToken:'access',expiresIn:900,user})).mockResolvedValueOnce(response({token:'proof'})).mockResolvedValueOnce(response({},403));vi.stubGlobal('fetch',fetcher);const s=new AuthSession();const updates:(typeof user|null)[]=[];s.subscribe(u=>updates.push(u as typeof user|null));await s.login('admin','fixture');expect(updates).toEqual([user]);await s.refresh();expect(updates).toEqual([user,null]);expect(s.current()).toBeNull();
 });
});
