import type { AssetItem, AssetDetail, Reference } from './api-contracts';
import { errorMessage, i18n } from './i18n';
import type { FeedbackError } from './i18n';
/** Explicit Asset read-to-view projection. Command State is deliberately not accepted. */
export function assetView<T extends AssetItem | AssetDetail>(a:T) {
  return {...a,tenantName:a.tenant?.name??null,categoryName:a.category?.name??null,manufacturerName:a.manufacturer?.name??null,
    productName:a.product?.name??null,locationName:a.location?.name??null,ownerName:a.owner?.name??null};
}
export function candidateContext(tenant:Reference|null|undefined,id:string):string {
  return `${tenant?.name??i18n.t('ui.globalContext')} · ${id}`;
}
export function referenceEndpoint(path:string,id:string):string|null {
  const kind=path.split('/').at(-1);
  if(['categories','manufacturers','products'].includes(kind??''))return `/api/v1/catalog/${kind}/${encodeURIComponent(id)}`;
  if(['locations','owners'].includes(kind??''))return `/api/v1/${kind}/${encodeURIComponent(id)}`;
  return null;
}
export const passwordChecks=(value:string)=>({minimum:Array.from(value).length>=12,maximum:Array.from(value).length<=128,
  uppercase:/\p{Uppercase}/u.test(value),lowercase:/\p{Lowercase}/u.test(value),digit:/\p{Nd}/u.test(value),special:/[^\p{L}\p{Nd}]/u.test(value)});
export const validPassword=(value:string)=>Object.values(passwordChecks(value)).every(Boolean);
export type MutationResource='asset'|'product'|'catalog'|'place'|'user'|'oidc';
export function mutationFeedback(error:FeedbackError,resource?:MutationResource) {
  const status=typeof error==='object'?error.status:undefined,code=typeof error==='object'?error.code:error;
  const uncertain=status===0&&!['AUTH_UNAVAILABLE','AUTHENTICATION_REQUIRED','INVALID_AUTH_RESPONSE'].includes(code??'')||code==='UNCERTAIN'||code==='REQUEST_FAILED';
  const guard=['REFERENCED','PRODUCT_EDIT_FROZEN','PROTECTED_UNKNOWN','DELETED_TARGET','INVALID_DEPENDENCY','INVALID_TRANSITION','OUTSTANDING_LOAN','NOT_LOANABLE'].includes(code??'');
  // B2 is not repaired here: retain the backend status/code, avoid asserting an Asset conflict for Product.
  const productMismatch=resource==='product'&&code==='STALE_ASSET_VERSION';
  const message=productMismatch?i18n.t('errors.HTTP_CONFLICT'):errorMessage(error);
  return {message:String(message),uncertain,guard,review:uncertain||!guard&&status===409};
}
