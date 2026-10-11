import { test, expect } from '@playwright/test';

test('mounted logo and palette render, and API proxy connects', async ({ page }) => {
  const green = process.env.KELIOS_EXPECTED_BRAND === 'green';
  await page.goto('/');
  await expect(page.getByLabel('Username',{exact:true})).toBeVisible();
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
    await expect(page.getByRole('button', { name: 'Sign in',exact:true })).toHaveCSS('background-color', 'rgb(36, 88, 211)');
    await expect(page.getByText('KELIOS', { exact: true })).toBeVisible();
  });
}

test('broken logo falls back without hiding the application', async ({ page }) => {
  await page.route('**/runtime/config.json', (route) => route.fulfill({ contentType: 'application/json', body: '{"schemaVersion":1,"logoUrl":"/runtime/logos/absent.svg"}' }));
  await page.goto('/');
  await expect(page.getByText('KELIOS', { exact: true })).toBeVisible();
  await expect(page.getByRole('img', { name: 'KELIOS logo' })).toHaveCount(0);
});
