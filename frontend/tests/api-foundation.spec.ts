import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',tenant='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const metadata={createdBy:id,createdAt:'2026-10-12T10:00:00Z',updatedBy:id,updatedAt:'2026-10-12T10:00:00Z',createdByDisplayName:null,updatedByDisplayName:null};
async function setup(page:Page,language:string,status:number,code?:string){
 let writes=0;
 await page.addInitScript(l=>localStorage.setItem('kelios.ui.language',l),language);
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url()),method=route.request().method();
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope:'GLOBAL',tenantId:null,role:'ADMIN',status:'ACTIVE',displayName:'Fixture'}}});
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'School',metadata}]});
  if(method==='PUT'){writes++;return route.fulfill({status,json:{type:'about:blank',status,detail:'PRIVATE provider-secret',...(code?{code}:{})}});}
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return ()=>writes;
}
for(const language of ['en','de'])for(const [status,key] of [[400,'INVALID_INPUT'],[401,'AUTHENTICATION_REQUIRED'],[403,'FORBIDDEN'],[404,'NOT_FOUND'],[409,'HTTP_CONFLICT'],[429,'THROTTLED'],[503,'SERVICE_UNAVAILABLE']] as const){
 test(`code-less ${status} preserves current form and uses safe ${language} status feedback`,async({page})=>{
  const count=await setup(page,language,status),r=language==='de'?de:en;
  await page.goto('/account/password');const form=page.getByRole('form',{name:r.ui.changeOwnPassword,exact:true});const input=form.getByLabel(r.ui.newPassword,{exact:true});
  await form.getByLabel(r.ui.currentPassword,{exact:true}).fill('Active session current');await input.fill('Retained active input');await input.evaluate(el=>(window as any).f0Input=el);await form.evaluate(el=>(window as any).f0Form=el);
  await form.getByRole('button',{name:r.ui.changeOwnPassword,exact:true}).click();await expect(form.getByRole('alert')).toContainText(r.errors[key]);await expect(input).toHaveValue('Retained active input');expect(await input.evaluate(el=>el===(window as any).f0Input)).toBe(true);expect(await form.evaluate(el=>el===(window as any).f0Form)).toBe(true);expect(count()).toBe(1);await expect(page.getByText(/PRIVATE|provider-secret/)).toHaveCount(0);
 });
}
for(const language of ['en','de'])test(`business code remains authoritative over HTTP fallback (${language})`,async({page})=>{
 const count=await setup(page,language,409,'REFERENCED'),r=language==='de'?de:en;
 await page.route('**/catalog/categories?**',route=>route.fulfill({json:{items:[{id,name:'Retained Category',visibility:'GLOBAL',tenantId:null,tenant:null,metadata}],page:0,size:20,totalElements:1}}));
 await page.goto('/categories');await page.getByRole('button',{name:r.ui.open,exact:true}).click();await page.getByRole('button',{name:r.ui.saveEntry,exact:true}).click();const f=page.getByRole('form',{name:r.ui.saveEntry,exact:true});await f.getByRole('button',{name:r.ui.saveEntry,exact:true}).click();await expect(f.getByRole('alert')).toContainText(r.errors.REFERENCED);expect(count()).toBe(1);
});
for(const language of ['en','de'])test(`code-less scoped read failure uses safe ${language} feedback`,async({page})=>{await setup(page,language,403);const r=language==='de'?de:en;await page.route('**/catalog/categories?**',route=>route.fulfill({status:403,json:{status:403,detail:'PRIVATE'}}));await page.goto('/categories');await expect(page.getByRole('alert')).toContainText(r.errors.FORBIDDEN);await expect(page.getByText('PRIVATE')).toHaveCount(0);});
