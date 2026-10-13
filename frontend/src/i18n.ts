import {localTimestamp} from './local-time';
import i18next from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';
import de from './locales/de.json';

export const languageKey = 'kelios.ui.language';
export type Language = 'en' | 'de';
export function detectLanguage(preference: string | null, languages: readonly string[]): Language {
  if (preference === 'en' || preference === 'de') return preference;
  for (const language of languages) {
    const base = language.toLowerCase().split('-')[0];
    if (base === 'en' || base === 'de') return base;
  }
  return 'en';
}
function initialLanguage(): Language {
  let preference: string | null = null;
  try { preference = globalThis.localStorage?.getItem(languageKey) ?? null; } catch { /* Presentation persistence is optional. */ }
  return detectLanguage(preference, globalThis.navigator?.languages ?? [globalThis.navigator?.language ?? 'en']);
}
export const i18n = i18next.createInstance();
void i18n.use(initReactI18next).init({ resources: { en: { translation: en }, de: { translation: de } }, lng: initialLanguage(), supportedLngs: ['en', 'de'], fallbackLng: 'en', initAsync: false, interpolation: { escapeValue: false }, returnNull: false });
i18n.on('languageChanged', language => { if (globalThis.document) document.documentElement.lang = language; });
if (globalThis.document) document.documentElement.lang = i18n.language;
export async function selectLanguage(language: Language): Promise<void> {
  await i18n.changeLanguage(language);
  try { globalThis.localStorage?.setItem(languageKey, language); } catch { /* Continue in memory when storage is unavailable. */ }
}
export function label(key: string): string {
  if (Object.hasOwn(en.fields, key)) return String(i18n.t(`fields.${key}`));
  if (Object.hasOwn(en.enums, key)) return String(i18n.t(`enums.${key}`));
  return key;
}
const machineFields = new Set(['lifecycle', 'condition', 'role', 'scope', 'status', 'visibility', 'action', 'type', 'targetType']);
const dateFields = new Set(['createdAt', 'updatedAt', 'occurredAt', 'loanedAt', 'expectedReturnAt', 'emailVerifiedAt']);
export function valueLabel(field: string, value: unknown): string {
  if (value == null) return '—';
  if (machineFields.has(field)) return Object.hasOwn(en.enums, String(value)) ? label(String(value)) : String(i18n.t('ui.unavailable'));
  if (field === 'loanable') return label(String(value));
  if (dateFields.has(field) && typeof value === 'string' && !Number.isNaN(Date.parse(value))) return localTimestamp(value,i18n.language);
  return typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);
}
export type FeedbackError = string | { status?: number; code?: string };
export function errorMessage(error: unknown): string {
  const code = typeof error === 'string' ? error : error && typeof error === 'object' && 'code' in error && typeof error.code === 'string' ? error.code : undefined;
  if (code && Object.hasOwn(en.errors, code)) return String(i18n.t(`errors.${code}`));
  const status = error && typeof error === 'object' && 'status' in error && typeof error.status === 'number' ? error.status : undefined;
  const fallback = status === 400 ? 'INVALID_INPUT' : status === 401 ? 'AUTHENTICATION_REQUIRED'
    : status === 403 ? 'FORBIDDEN' : status === 404 ? 'NOT_FOUND' : status === 409 ? 'HTTP_CONFLICT'
    : status === 429 ? 'THROTTLED' : status === 503 ? 'SERVICE_UNAVAILABLE' : 'REQUEST_FAILED';
  return String(i18n.t(`errors.${fallback}`));
}

export function numberLabel(value: number): string { return new Intl.NumberFormat(i18n.language).format(value); }
