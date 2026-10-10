export const defaultTheme = {
  background: '#f6f7fb', foreground: '#172033', primary: '#2458d3',
  primaryForeground: '#ffffff', accent: '#e7edff', accentForeground: '#243b75',
} as const;

export type Theme = { -readonly [K in keyof typeof defaultTheme]: string };
export type Branding = { schemaVersion: 1; logoUrl?: string; theme: Theme };
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
  if (typeof value.logoUrl === 'string' && value.logoUrl.length <= 2048) {
    try {
      const logo = new URL(value.logoUrl, `${origin}/`);
      if (['http:', 'https:'].includes(logo.protocol) && logo.origin === origin && !logo.username && !logo.password) {
        result.logoUrl = logo.href;
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
