import { describe, expect, it } from 'vitest';
import { defaultTheme, validateBranding } from './branding';

describe('public runtime branding trust boundary', () => {
  const origin = 'https://kelios.example';
  it('accepts a mounted logo and allowlisted colors', () => {
    const result = validateBranding({ schemaVersion: 1, logoUrl: '/runtime/logos/logo.svg', theme: { primary: '#007766' } }, origin);
    expect(result.logoUrl).toBe(`${origin}/runtime/logos/logo.svg`);
    expect(result.theme.primary).toBe('#007766');
    expect(result.theme.background).toBe(defaultTheme.background);
  });
  it('keeps organization and legacy logos separate', () => {
    const result = validateBranding({ schemaVersion: 1, logoUrl: '/runtime/logos/logo.svg', organizationLogoUrl: '/runtime/logos/organization.png' }, origin);
    expect(result.logoUrl).toBe(`${origin}/runtime/logos/logo.svg`);
    expect(result.organizationLogoUrl).toBe(`${origin}/runtime/logos/organization.png`);
    expect(result.theme.primary).toBe('#b45309');
  });
  it.each([null, [], {}, { schemaVersion: 2 }, 'invalid'])('defaults unsupported configuration %j', (value) => {
    expect(validateBranding(value, origin)).toEqual({ schemaVersion: 1, theme: defaultTheme });
  });
  it.each(['javascript:alert(1)', 'data:image/svg+xml,evil', '//evil.example/logo.svg', 'https://user:pass@kelios.example/logo.svg'])('rejects unsafe logo %s', (logoUrl) => {
    expect(validateBranding({ schemaVersion: 1, logoUrl }, origin).logoUrl).toBeUndefined();
    expect(validateBranding({ schemaVersion: 1, organizationLogoUrl: logoUrl }, origin).organizationLogoUrl).toBeUndefined();
  });
  it('rejects CSS injection per field while preserving valid fields', () => {
    const result = validateBranding({ schemaVersion: 1, theme: { primary: 'url(https://evil.example)', foreground: '#112233', background: 42, unknown: '#000000' } }, origin);
    expect(result.theme).toEqual({ ...defaultTheme, foreground: '#112233' });
  });
});
