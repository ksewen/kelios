import { test, expect } from '@playwright/test';
import { readFileSync } from 'node:fs';

const login = process.env.KELIOS_TEST_LOGIN ?? 'phase2-smoke-admin';
const password = () => readFileSync(process.env.KELIOS_TEST_PASSWORD_FILE!, 'utf8');

async function signIn(page: import('@playwright/test').Page) {
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeEnabled();
  await page.getByLabel('Login identifier').fill(login);
  await page.getByLabel('Password', { exact: true }).fill(password());
  const response = page.waitForResponse('**/api/v1/auth/local/login');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const result = await response;
  expect(result.status()).toBe(200);
  return await result.json() as { accessToken: string; user: { id: string } };
}

test('real HTTPS login, memory transport, reload refresh and logout', async ({ page, context }) => {
  const result = await signIn(page);
  await expect(page.getByText('Signed in as Phase 2 smoke administrator')).toBeVisible();
  const refresh = (await context.cookies()).find(cookie => cookie.name === '__Secure-kelios-refresh');
  expect(Boolean(refresh?.secure && refresh.httpOnly && refresh.sameSite === 'Strict' && refresh.path === '/api/v1/auth/')).toBe(true);
  expect(await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length, cookies: document.cookie }))).toEqual({ local: 0, session: 0, cookies: '' });
  expect((await page.request.get('/api/v1/auth/me', { headers: { Authorization: `Bearer ${result.accessToken}` } })).status()).toBe(200);
  const refreshResponse = page.waitForResponse('**/api/v1/auth/session/refresh');
  await page.reload();
  expect((await refreshResponse).status()).toBe(200);
  await expect(page.getByText('Signed in as Phase 2 smoke administrator')).toBeVisible();
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeEnabled();
  expect((await context.cookies()).some(cookie => cookie.name === '__Secure-kelios-refresh')).toBe(false);
  await page.reload();
  await expect(page.getByRole('button', { name: 'Sign in', exact: true })).toBeEnabled();
});

test('real cookie refresh rejects missing CSRF and wrong Origin', async ({ page }) => {
  await signIn(page);
  expect((await page.request.post('/api/v1/auth/session/refresh')).status()).toBe(403);
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json() as { token: string };
  expect((await page.request.post('/api/v1/auth/session/refresh', { headers: { Origin: 'https://untrusted.invalid', 'X-CSRF-Token': csrf.token } })).status()).toBe(403);
  expect((await page.request.get('/api/v1/tenants')).status()).toBe(401);
  expect((await page.request.get('/api/v1/status', { headers: { Origin: 'https://untrusted.invalid' } })).headers()['access-control-allow-origin']).toBeUndefined();
});

test('real disabled account loses refresh while issued Access expires naturally', async ({ page }) => {
  const admin = await signIn(page);
  const username = `browser-${Date.now()}`;
  const created = await page.request.post('/api/v1/users/global', { headers: { Authorization: `Bearer ${admin.accessToken}` }, data: { login: username, displayName: 'Disabled browser fixture', password: password() } });
  expect(created.status()).toBe(200);
  const user = await created.json() as { id: string; scope: string; role: string; status: string };
  expect([user.scope, user.role, user.status]).toEqual(['GLOBAL', 'USER', 'ACTIVE']);
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByLabel('Login identifier').fill(username);
  await page.getByLabel('Password', { exact: true }).fill(password());
  const loginResponse = page.waitForResponse('**/api/v1/auth/local/login');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const issued = await (await loginResponse).json() as { accessToken: string };
  await expect(page.getByText('Signed in as Disabled browser fixture')).toBeVisible();
  expect((await page.request.post(`/api/v1/users/${user.id}/disable`, { headers: { Authorization: `Bearer ${admin.accessToken}` } })).status()).toBe(200);
  expect((await page.request.get('/api/v1/auth/me', { headers: { Authorization: `Bearer ${issued.accessToken}` } })).status()).toBe(200);
  await page.getByRole('button', { name: 'Refresh account status' }).click();
  await expect(page.getByRole('alert')).toHaveText('Session expired or account disabled. Sign in again.');
});

test('frontend Tenant initiation/completion shows mandatory pending state without business requests', async ({ page, baseURL }) => {
  // This tests the browser contract only; HTTPS provider wire behavior is tested by IdentityAuthenticationIT.
  const tenant = 'ba4115de-c534-4f46-94d1-59783e967a40';
  let ready = false;
  const businessRequests: string[] = [];
  page.on('request', request => { if (/\/api\/v1\/(assets|users|tenants|catalog)/.test(request.url())) businessRequests.push(request.url()); });
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { token: 'browser-contract-proof' } }));
  await page.route('**/api/v1/auth/session/refresh', route => ready
    ? route.fulfill({ json: { accessToken: 'browser-contract-access', expiresIn: 900, user: { id: '59067609-316d-4514-ad98-9ee6915bd0e7', scope: 'TENANT', tenantId: tenant, role: 'USER', status: 'PENDING', displayName: 'Pending fixture' } } })
    : route.fulfill({ status: 401, json: { status: 401, detail: 'Authentication required' } }));
  await page.route(`**/api/v1/auth/oidc/${tenant}/start`, route => { ready = true; return route.fulfill({ json: { authorizationUrl: `${baseURL}/auth/complete` } }); });
  await page.goto(`/login/tenant/${tenant}`);
  await expect(page.getByLabel('Tenant ID')).toHaveValue(tenant);
  await expect(page.getByRole('button', { name: 'Sign in with IServ' })).toBeEnabled();
  await page.getByRole('button', { name: 'Sign in with IServ' }).click();
  await expect(page).toHaveURL(`${baseURL}/auth/complete`);
  await expect(page.getByText('Your account is pending approval. Contact your administrator.')).toBeVisible();
  expect(businessRequests).toEqual([]);
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
});

test('safe OIDC failure state contains no tokens or authorization code', async ({ page }) => {
  await page.goto('/auth/failed');
  await expect(page.getByRole('alert')).toHaveText('Tenant login failed. Contact your administrator.');
  expect(new URL(page.url()).search).toBe('');
});
