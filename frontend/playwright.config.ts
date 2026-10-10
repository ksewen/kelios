import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  testIgnore: process.env.KELIOS_TEST_TLS === 'true' ? [] : ['**/authentication.spec.ts', '**/inventory.spec.ts'],
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.KELIOS_BASE_URL ?? 'http://127.0.0.1:8080',
    browserName: 'chromium',
    // Only the local smoke fixture uses a self-signed certificate.
    ignoreHTTPSErrors: process.env.KELIOS_TEST_TLS === 'true',
    launchOptions: { chromiumSandbox: true },
  },
});
