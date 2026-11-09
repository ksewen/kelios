import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};

const own='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const other='cccccccc-cccc-cccc-cccc-cccccccccccc';
async function fixture(page:Page,{scope='GLOBAL',role='ADMIN',status='ACTIVE',language='en',empty=false,noTenants=false,tenantError=false,duplicateNames=false}={}){
 const requests:string[]=[],writes:string[]=[],listQueries:string[]=[];let current={id:'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',scope,role,status,tenantId:scope==='TENANT'?own:null,displayName:'Administrator'};
 await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url()),path=url.pathname;requests.push(path);if(['PUT','DELETE'].includes(route.request().method()))writes.push(path);
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:current}});
  if(path==='/api/v1/tenants')return tenantError?route.fulfill({status:503,json:{status:503,detail:'Tenant list unavailable'}}):route.fulfill({json:noTenants?[]:[{id:own,name:'Own school'},{id:other,name:duplicateNames?'Own school':'Other school'}]});
  if(path===`/api/v1/tenants/${own}`)return route.fulfill({json:{id:own,name:'Own school'}});
  if(path==='/api/v1/oidc-configurations'){
   if(current.scope!=='GLOBAL'||current.role!=='ADMIN'||current.status!=='ACTIVE')return route.fulfill({status:403,json:{status:403}});
   listQueries.push(url.search);
   const items=empty?[]:Array.from({length:21},(_,index)=>({tenantId:index===0?own:index===1?other:`${String(index).padStart(8,'0')}-0000-0000-0000-000000000000`,tenant:{id:index===0?own:index===1?other:`${String(index).padStart(8,'0')}-0000-0000-0000-000000000000`,name:index===0?'Own school':index===1?(duplicateNames?'Own school':'Other school'):`School ${index}`},issuer:`https://issuer-${index}.example`,clientId:`client-${index}`,enabled:index!==1,version:index}));
   const q=(url.searchParams.get('q')??'').toLowerCase(),enabled=url.searchParams.get('enabled');const filtered=items.filter(item=>item.tenant.name.toLowerCase().includes(q)&&(enabled===null||String(item.enabled)===enabled));
   const p=Number(url.searchParams.get('page')??0),size=Number(url.searchParams.get('size')??20);return route.fulfill({json:{items:filtered.slice(p*size,(p+1)*size),page:p,size,totalElements:filtered.length}});
  }
  if(path.endsWith('/oidc'))return route.fulfill({json:{tenantId:own,tenant:{id:own,name:'Own school'},issuer:'https://issuer.example',clientId:'client',enabled:false,secretConfigured:true,version:1,groupMappings:[],callbackUri:'https://callback.example',metadata:{createdBy:current.id,createdAt:'2026-10-18T10:00:00Z',updatedBy:current.id,updatedAt:'2026-10-18T10:00:00Z'}}});
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return {requests,writes,listQueries,demote:()=>{current={...current,role:'USER'};}};
}
for(const language of ['en','de'] as const)for(const width of [1440,390])test(`GLOBAL ADMIN IServ collection ${language} ${width}`,async({page})=>{
 const t=language==='de'?de:en,f=await fixture(page,{language});await page.setViewportSize({width,height:900});await page.goto('/oidc');
 await expect(page.getByRole('heading',{name:t.ui.oidcConfigurations})).toBeVisible();await expect(page.getByRole('columnheader',{name:t.fields.tenantName})).toBeVisible();
 await expect(page.getByRole('columnheader',{name:'Version'})).toHaveCount(0);await expect(page.getByRole('button',{name:t.ui.configureIServ})).toHaveClass(/bg-primary/);
 await expect(page.getByRole('row',{name:/Other school/})).toContainText(t.fields.false);await expect(page.locator('tbody tr')).toHaveCount(20);
 const first=page.locator('tbody tr').first().getByRole('button');await first.focus();await expect(first).toBeFocused();await first.press('Enter');await expect(page).toHaveURL(`/tenants/${own}/oidc`);
 await expect(page.getByRole('heading',{name:'Own school'})).toBeVisible();if(width===390){await page.getByRole('button',{name:t.redesign.toggleNavigation}).click();await page.getByRole('dialog').getByRole('link',{name:t.redesign.navigationIserv}).click();}else await page.locator('#desktop-navigation').getByRole('link',{name:t.redesign.navigationIserv}).click();await expect(page).toHaveURL('/oidc');
 await page.getByRole('button',{name:t.ui.nextPage}).click();await expect(page.locator('tbody tr')).toHaveCount(1);expect(f.requests.filter(path=>path==='/api/v1/oidc-configurations').length).toBeGreaterThan(1);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
for(const language of ['en','de'] as const)test(`Tenant search and enabled filter compose before paging and reset together (${language})`,async({page})=>{
 const t=language==='de'?de:en,f=await fixture(page,{language});await page.goto('/oidc');const filter=page.getByRole('combobox',{name:t.ui.iservEnabledFilter});
 await expect(filter.locator('option')).toHaveText([t.ui.iservFilterAll,t.ui.iservFilterEnabled,t.ui.iservFilterDisabled]);
 expect(new URLSearchParams(f.listQueries.at(-1)).has('enabled')).toBe(false);
 await page.getByRole('button',{name:t.ui.nextPage}).click();await expect(page.locator('tbody tr')).toHaveCount(1);
 await page.getByRole('textbox',{name:t.fields.tenantName}).fill('Other school');await page.getByRole('textbox',{name:t.fields.tenantName}).press('Enter');await expect(page.locator('tbody tr')).toHaveCount(1);await expect(page.getByRole('row',{name:/Other school/})).toBeVisible();
 await filter.selectOption('true');await expect(page.locator('tbody tr')).toHaveCount(0);
 expect(new URLSearchParams(f.listQueries.at(-1)).get('enabled')).toBe('true');expect(new URLSearchParams(f.listQueries.at(-1)).get('q')).toBe('Other school');expect(new URLSearchParams(f.listQueries.at(-1)).get('page')).toBe('0');
 await filter.selectOption('false');await expect(page.locator('tbody tr')).toHaveCount(1);expect(new URLSearchParams(f.listQueries.at(-1)).get('enabled')).toBe('false');
 await page.getByRole('button',{name:t.actions.resetFilters}).click();await expect(page.locator('tbody tr')).toHaveCount(20);expect(new URLSearchParams(f.listQueries.at(-1)).has('q')).toBe(false);expect(new URLSearchParams(f.listQueries.at(-1)).has('enabled')).toBe(false);
});
test('Tenant UUID appears only when names repeat across the full Tenant list',async({page})=>{
 await fixture(page,{duplicateNames:true});await page.goto('/oidc');await expect(page.getByRole('row',{name:new RegExp(own)})).toContainText('Own school');await expect(page.getByRole('row',{name:new RegExp(other)})).toContainText('Own school');
 await page.getByRole('button',{name:en.ui.configureIServ}).click();await expect(page.getByRole('menuitem',{name:`Own school · ${own}`})).toBeVisible();await expect(page.getByRole('menuitem',{name:`Own school · ${other}`})).toBeVisible();
});
test('empty GLOBAL collection still offers Tenant selection for configuration',async({page})=>{
 const empty=await fixture(page,{empty:true});await page.goto('/oidc');await expect(page.getByText(en.ui.noRecords,{exact:true})).toBeVisible();expect(empty.requests).toContain('/api/v1/oidc-configurations');
 await page.getByRole('button',{name:en.ui.configureIServ}).click();await page.getByRole('menuitem',{name:'Own school'}).click();await expect(page).toHaveURL(`/tenants/${own}/oidc`);await expect(page.getByRole('heading',{name:'Own school'})).toBeVisible();expect(empty.writes).toHaveLength(0);
});
test('GLOBAL ADMIN without a Tenant can reach Tenant creation',async({page})=>{
 await fixture(page,{empty:true,noTenants:true});await page.goto('/oidc');await page.getByRole('link',{name:en.ui.createTenant}).click();await expect(page).toHaveURL('/tenants');
});
test('Tenant list failure does not offer an unspecified configuration target',async({page})=>{
 await fixture(page,{empty:true,tenantError:true});await page.goto('/oidc');await expect(page.getByRole('alert')).toBeVisible();await expect(page.getByRole('button',{name:en.ui.configureIServ})).toHaveCount(0);await expect(page.getByRole('link',{name:en.ui.createTenant})).toHaveCount(0);
});
test('TENANT ADMIN sees only own singleton; USER direct route and demotion do not fetch list',async({page})=>{
 const ownFixture=await fixture(page,{scope:'TENANT'});await page.goto('/oidc');await expect(page.getByRole('heading',{name:'Own school'})).toBeVisible();expect(ownFixture.requests).toContain(`/api/v1/tenants/${own}/oidc`);expect(ownFixture.requests).not.toContain('/api/v1/oidc-configurations');
 await expect(page.getByText('https://issuer.example')).toBeVisible();await expect(page.getByText('https://callback.example')).toBeVisible();
 await expect(page.getByRole('form',{name:en.ui.editCategory})).toHaveCount(0);
 await expect(page.getByRole('button',{name:en.ui.deleteEntry})).toHaveCount(0);expect(ownFixture.writes).toHaveLength(0);
 await page.goto('/dashboard');const userFixture=await fixture(page,{role:'USER'});await page.goto('/oidc');await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable})).toBeVisible();expect(userFixture.requests).not.toContain('/api/v1/oidc-configurations');
});
test('GLOBAL ADMIN demotion hides list after identity refresh',async({page})=>{
 const f=await fixture(page,{});await page.goto('/oidc');await expect(page.getByRole('heading',{name:en.ui.oidcConfigurations})).toBeVisible();const count=f.requests.filter(path=>path==='/api/v1/oidc-configurations').length;f.demote();await page.reload();await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable})).toBeVisible();expect(f.requests.filter(path=>path==='/api/v1/oidc-configurations')).toHaveLength(count);
});
