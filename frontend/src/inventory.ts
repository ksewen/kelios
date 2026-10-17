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
export const PAGE_SIZES = [10,20,30,40,50,100] as const;
export const normalizeSearch = (text: string): string => text.trim();
export const hasNextPage = (page: Page<unknown>): boolean => (page.page + 1) * page.size < page.totalElements;
export function assetQuery(page: number, search: string, sort: string, direction: string, filters: Record<string, string>, size = 20): string {
  const q = new URLSearchParams({ page: String(page), size: String(size), sort, direction });
  if (normalizeSearch(search)) q.set('q', normalizeSearch(search));
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

export type CatalogCriteria = {name:string;visibility:string;categoryId:string;manufacturerId:string;manufacturerNumber:string};
export type UserCriteria = {name:string;scope:string;role:string;status:string;tenantId:string};
/** Query text only; never mutation fields or passwords. */
export function normalizeCriteria<T extends Record<string,string>>(criteria:T):T {
  return Object.fromEntries(Object.entries(criteria).map(([key,value])=>[key,normalizeSearch(value)])) as T;
}
/** Resource-owned query allowlists, not a generic query mapper. */
export function catalogQuery(kind:string,page:number,size:number,criteria:CatalogCriteria,sort:string,direction:string):string {
  const q=new URLSearchParams({page:String(page),size:String(size)});
  if(criteria.name)q.set('q',criteria.name);
  const catalog=['categories','manufacturers','products'].includes(kind);
  if(catalog&&criteria.visibility)q.set('visibility',criteria.visibility);
  if(kind==='products')for(const key of ['categoryId','manufacturerId','manufacturerNumber'] as const)if(criteria[key])q.set(key,criteria[key]);
  if(sort&&(['name',...(catalog?['visibility']:[]),...(kind==='categories'?['tenantName']:[]),...(kind==='products'?['manufacturerNumber']:[])]).includes(sort)){q.set('sort',sort);q.set('direction',direction);}
  return q.toString();
}
export function userQuery(page:number,size:number,criteria:UserCriteria,sort:string,direction:string,globalAdmin:boolean):string {
  const q=new URLSearchParams({page:String(page),size:String(size)});
  if(criteria.name)q.set('q',criteria.name);
  if(criteria.status)q.set('status',criteria.status);
  if(globalAdmin)for(const key of ['scope','role','tenantId'] as const)if(criteria[key])q.set(key,criteria[key]);
  if(['displayName','scope','role','status'].includes(sort)){q.set('sort',sort);q.set('direction',direction);}
  return q.toString();
}
