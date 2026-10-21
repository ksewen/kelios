export const defaultTheme = {
  background: '#f7f7f5', foreground: '#18181b', primary: '#cd3a06',
  primaryForeground: '#ffffff', accent: '#fff0eb', accentForeground: '#8f2904',
} as const;

export type Theme = { -readonly [K in keyof typeof defaultTheme]: string };
export type Branding = { schemaVersion: 1; logoUrl?: string; organizationLogoUrl?: string; theme: Theme };
const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

export function validateBranding(value: unknown, origin: string): Branding {
  const result: Branding = { schemaVersion: 1, theme: { ...defaultTheme } };
  if (!isObject(value) || value.schemaVersion !== 1) return result;
  if (isObject(value.theme)) {
    for (const key of Object.keys(defaultTheme) as (keyof Theme)[]) {
      const color = value.theme[key];
      if (typeof color === 'string' && /^#[0-9a-fA-F]{6}$/.test(color)) result.theme[key] = color;
    }
  }
  for (const key of ['logoUrl', 'organizationLogoUrl'] as const) {
    const supplied = value[key];
    if (typeof supplied !== 'string' || supplied.length > 2048) continue;
    try {
      const logo = new URL(supplied, `${origin}/`);
      if (['http:', 'https:'].includes(logo.protocol) && logo.origin === origin && !logo.username && !logo.password) {
        result[key] = logo.href;
      }
    } catch { /* Invalid logo uses the text fallback. */ }
  }
  return result;
}

export async function loadBranding(): Promise<Branding> {
  try {
    const response = await fetch('/runtime/config.json', { cache: 'no-store', signal: AbortSignal.timeout(3000) });
    if (!response.ok) throw new Error('Branding unavailable');
    const text = await response.text();
    if (new TextEncoder().encode(text).byteLength > 32768) throw new Error('Branding too large');
    return validateBranding(JSON.parse(text), window.location.origin);
  } catch {
    return validateBranding(null, window.location.origin);
  }
}

export function applyBranding(branding: Branding): void {
  for (const [key, value] of Object.entries(branding.theme)) {
    document.documentElement.style.setProperty(`--${key.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`)}`, value);
  }
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', branding.theme.primary);
}
