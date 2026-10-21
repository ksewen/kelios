import { test, expect } from '@playwright/test';

test('mounted logo and palette render, and API proxy connects', async ({ page }) => {
  test.skip(process.env.KELIOS_TEST_TLS !== 'true', 'Requires deployed HTTPS and API proxy');
  const green = process.env.KELIOS_EXPECTED_BRAND === 'green';
  await page.goto('/');
  await expect(page.getByLabel('Email',{exact:true})).toBeVisible();
  const logo = page.getByRole('img', { name: 'KELIOS logo' });
  await expect(logo).toBeVisible();
  expect(await logo.evaluate((image: HTMLImageElement) => image.complete && image.naturalWidth > 0)).toBe(true);
  await expect(page.getByRole('button', { name: 'Sign in',exact:true })).toHaveCSS('background-color', green ? 'rgb(8, 127, 91)' : 'rgb(36, 88, 211)');
  await expect(page.locator('body')).toHaveCSS('background-color', green ? 'rgb(242, 250, 245)' : 'rgb(244, 247, 255)');
  expect((await page.request.get('/api/v1/status')).status()).toBe(200);
  await page.screenshot({ path: `test-results/${green ? 'green' : 'blue'}.png`, fullPage: true });
});

for (const scenario of ['missing', 'malformed', 'unsupported', 'timeout'] as const) {
  test(`${scenario} branding safely renders defaults`, async ({ page }) => {
    await page.route('**/runtime/config.json', async (route) => {
      if (scenario === 'missing') return route.fulfill({ status: 404 });
      if (scenario === 'malformed') return route.fulfill({ contentType: 'application/json', body: '{' });
      if (scenario === 'unsupported') return route.fulfill({ contentType: 'application/json', body: '{"schemaVersion":2,"theme":{"primary":"#000000"}}' });
      // Never resolve this request; initialization must reach its timeout fallback.
      return new Promise<void>(() => {});
    });
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await expect(page.getByRole('button', { name: 'Sign in',exact:true })).toHaveCSS('background-color', 'rgb(205, 58, 6)');
    await expect(page.getByText('KELIOS', { exact: true })).toBeVisible();
    await expect(page.getByText('O', { exact: true })).toHaveCSS('color', 'rgb(205, 58, 6)');
  });
}

test('broken logo falls back without hiding the application', async ({ page }) => {
  await page.route('**/runtime/config.json', (route) => route.fulfill({ contentType: 'application/json', body: '{"schemaVersion":1,"logoUrl":"/runtime/logos/absent.svg"}' }));
  await page.goto('/');
  await expect(page.getByText('KELIOS', { exact: true })).toBeVisible();
  await expect(page.getByRole('img', { name: 'KELIOS logo' })).toHaveCount(0);
});

test('organization logo keeps the themed live wordmark', async ({ page }) => {
  await page.setViewportSize({width:390,height:844});
  await page.route('**/runtime/config.json', route => route.fulfill({ json: {schemaVersion:1, logoUrl:'/runtime/logos/legacy.svg', organizationLogoUrl:'/runtime/logos/organization.svg', theme:{primary:'#087f5b'}} }));
  await page.route('**/runtime/logos/organization.svg', route => route.fulfill({contentType:'image/svg+xml', body:'<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><rect width="40" height="40" fill="black"/></svg>'}));
  await page.goto('/');
  await expect(page.getByRole('img', {name:'Organization logo'})).toBeVisible();
  await expect(page.getByRole('img', {name:'KELIOS logo'})).toHaveCount(0);
  await expect(page.getByText('KELIOS', {exact:true})).toBeVisible();
  await expect(page.getByText('O', {exact:true})).toHaveCSS('color', 'rgb(8, 127, 91)');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('broken organization logo falls back to neutral mark and live KELIOS', async ({page}) => {
  await page.route('**/runtime/config.json', route => route.fulfill({json:{schemaVersion:1,organizationLogoUrl:'/runtime/logos/absent.svg'}}));
  await page.goto('/');
  await expect(page.getByRole('img',{name:'Organization logo'})).toHaveCount(0);
  await expect(page.getByText('KELIOS',{exact:true})).toBeVisible();
  await expect(page.getByText('O',{exact:true})).toHaveCSS('color','rgb(205, 58, 6)');
});

test('legacy full-wordmark logo keeps its original image treatment', async ({page}) => {
  await page.route('**/runtime/config.json', route => route.fulfill({json:{schemaVersion:1,logoUrl:'/runtime/logos/legacy.svg'}}));
  await page.route('**/runtime/logos/legacy.svg', route => route.fulfill({contentType:'image/svg+xml',body:'<svg xmlns="http://www.w3.org/2000/svg" width="160" height="40"><text x="0" y="30">KELIOS</text></svg>'}));
  await page.goto('/');
  await expect(page.getByRole('img',{name:'KELIOS logo'})).toBeVisible();
  await expect(page.getByText('KELIOS',{exact:true})).toHaveCount(0);
});
