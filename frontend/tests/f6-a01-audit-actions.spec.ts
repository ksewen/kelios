import { test, expect, type Page } from '@playwright/test';
import en from '../src/locales/en.json' with { type: 'json' };
import de from '../src/locales/de.json' with { type: 'json' };
const actor='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',tenant='cccccccc-cccc-cccc-cccc-cccccccccccc';
const ids=['bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb','dddddddd-dddd-dddd-dddd-dddddddddddd'];
async function fixture(page:Page,language:string,unknown=false){
 const details:string[]=[],mutations:string[]=[];
 const actions=unknown?['FUTURE_UNKNOWN_ACTION']:['GLOBAL_EMAIL_CHANGED','OIDC_CONFIG_DELETED'];
 const items=actions.map((action,i)=>({id:ids[i],tenantId:i?tenant:null,actorId:actor,actorDisplayName:'Historical actor',action,targetType:i?'TENANT':'USER',targetId:i?tenant:actor,occurredAt:'2026-10-12T12:00:00Z',data:{}}));
 await page.addInitScript(l=>localStorage.setItem('kelios.ui.language',l),language);
 await page.route('**/api/v1/**',route=>{const request=route.request(),path=new URL(request.url()).pathname;
 if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
 if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:actor,scope:'GLOBAL',role:'ADMIN',status:'ACTIVE',tenantId:null,displayName:'Fixture'}}});
 if(request.method()!=='GET'){mutations.push(path);return route.fulfill({status:500,json:{}});}
 if(path==='/api/v1/tenants')return route.fulfill({json:[]});
 if(path==='/api/v1/audit-log')return route.fulfill({json:{items,page:0,size:20,totalElements:items.length}});
 const item=items.find(row=>path===`/api/v1/audit-log/${row.id}`);
 if(item){details.push(item.id);return route.fulfill({json:item});}
 return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });return{details,mutations};
}
for(const language of ['en','de'] as const)for(const width of [1440,1024,768,390])test(`${language}/${width} supported Audit actions localize list and independent detail`,async({page})=>{
 await page.setViewportSize({width,height:900});const f=await fixture(page,language),r=language==='de'?de:en;await page.goto('/audit');
 for(const [i,action] of (['GLOBAL_EMAIL_CHANGED','OIDC_CONFIG_DELETED'] as const).entries()){
 const row=page.locator('tbody tr').filter({hasText:r.enums[action]});await expect(row).toHaveCount(1);await row.locator('td').first().getByRole('button').click();
 await expect(page.getByRole('dialog').getByRole('heading',{name:r.enums[action],exact:true})).toBeVisible();await expect(page.getByRole('dialog')).toContainText('Historical actor');expect(f.details.at(-1)).toBe(ids[i]);
 await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);await expect(row).toBeVisible();}
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect(f.mutations).toEqual([]);
});
for(const language of ['en','de'] as const)test(`${language} unknown Audit action retains generic fallback`,async({page})=>{
 const f=await fixture(page,language,true),r=language==='de'?de:en;await page.goto('/audit');await expect(page.locator('tbody')).toContainText(r.ui.unavailable);await expect(page.locator('tbody')).not.toContainText('FUTURE_UNKNOWN_ACTION');
 await page.locator('tbody tr').first().locator('td').first().getByRole('button').click();await expect(page.getByRole('dialog').getByRole('heading',{name:r.ui.unavailable,exact:true})).toBeVisible();expect(f.details).toEqual([ids[0]]);expect(f.mutations).toEqual([]);
});
