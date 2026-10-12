import { i18n } from './i18n';
export type Row = { id: string; [key: string]: unknown };
/** Legacy view rows remain until resource integration; Page itself follows the actual API. */
export type Page<T = Row> = import('./api-contracts').Page<T>;
export const lifecycleTargets: Record<string, string[]> = { REGISTERED: ['IN_STOCK', 'DEPLOYED', 'DEREGISTERED'], IN_STOCK: ['REGISTERED', 'DEPLOYED', 'DEREGISTERED'], DEPLOYED: ['REGISTERED', 'IN_STOCK', 'DEREGISTERED'], DEREGISTERED: [] };
export const conditionTargets: Record<string, string[]> = { UNKNOWN: ['WORKING', 'DEFECTIVE', 'UNUSABLE'], WORKING: ['DEFECTIVE', 'UNUSABLE'], DEFECTIVE: ['WORKING', 'UNUSABLE'], UNUSABLE: [] };
export function catalogInput(values: Record<string, string>): Record<string, unknown> {
  return values.productId ? { productId: values.productId } : { categoryId: values.categoryId, manufacturerId: values.manufacturerId };
}
export function nullable(value: string): string | null { return value.trim() ? value : null; }
export function display(value: unknown): string { return value == null ? '—' : typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value); }
export function assetQuery(page: number, search: string, sort: string, direction: string, filters: Record<string, string>): string {
  const q = new URLSearchParams({ page: String(page), size: '25', sort, direction });
  if (search) q.set('q', search);
  for (const [key, value] of Object.entries(filters)) if (value) q.set(key, value);
  return q.toString();
}
export function historyChanges(data: unknown): { field: string; before: string; after: string }[] {
  if (!data || typeof data !== 'object') return [];
  const record = data as Record<string, unknown>;
  const before = (record.before ?? {}) as Record<string, unknown>, after = (record.after ?? {}) as Record<string, unknown>;
  const fields = ['assetTag','localIdentifier','lifecycle','condition','loanable','serialNumber','externalInventoryNumber','note','loanedTo','loanedAt','expectedReturnAt','loanNote','category','manufacturer','product','location','owner'];
  const label = (value: unknown) => value && typeof value === 'object' && 'name' in value ? `${display(value.name)} · ${display('id' in value ? value.id : null)}` : display(value);
  return fields.filter(field => field in before || field in after).filter(field => JSON.stringify(before[field]) !== JSON.stringify(after[field]))
    .map(field => ({field,before:field in before ? label(before[field]) : String(i18n.t('ui.unavailable')),after:field in after ? label(after[field]) : String(i18n.t('ui.unavailable'))}));
}
