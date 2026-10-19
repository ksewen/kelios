import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const uid='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',own='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',foreign='cccccccc-cccc-cccc-cccc-cccccccccccc';
const metadata={createdBy:uid,createdAt:'2026-10-12T10:00:00Z',updatedBy:uid,updatedAt:'2026-10-12T10:00:00Z',createdByDisplayName:null,updatedByDisplayName:null};
async function setup(page:Page,scope='GLOBAL',role='USER',status='ACTIVE',language='en'){
 const calls:{path:string;method:string;body:any}[]=[];let current={id:uid,scope,role,status,tenantId:scope==='TENANT'?own:null,displayName:'Reader'};
 await page.addInitScript(l=>localStorage.setItem('kelios.ui.language',l),language);
 await page.route('**/api/v1/**',async route=>{
  const req=route.request(),url=new URL(req.url());calls.push({path:url.pathname,method:req.method(),body:req.postData()?req.postDataJSON():null});
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh')&&current.status==='DISABLED')return route.fulfill({status:401,json:{status:401,detail:'No usable session'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:current}});
  if((url.pathname==='/api/v1/users'||url.pathname==='/api/v1/audit-log'||url.pathname.endsWith('/oidc'))&&(current.status!=='ACTIVE'||current.role!=='ADMIN'))return route.fulfill({status:403,json:{status:403}});
  if(url.pathname==='/api/v1/tenants'&&(current.status!=='ACTIVE'||current.scope!=='GLOBAL'||req.method()!=='GET'&&current.role!=='ADMIN'))return route.fulfill({status:403,json:{status:403}});
  if(url.pathname==='/api/v1/tenants'&&req.method()==='POST')return route.fulfill({json:foreign});
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[{id:own,name:'Own school',metadata},{id:foreign,name:'Other school',metadata}]});
  if(url.pathname.endsWith('/oidc')&&current.scope==='TENANT'&&!url.pathname.includes(`/${current.tenantId}/`))return route.fulfill({status:404,json:{status:404}});
  if(url.pathname.endsWith('/oidc')){
   if(req.method()==='PUT')return route.fulfill({json:{tenantId:own,issuer:'https://provider.example',clientId:'client',enabled:true,secretConfigured:true,version:1,allowedGroupIds:[uid],callbackUri:'https://fixture/callback',metadata,tenant:{id:own,name:'Own school'}}});
   return route.fulfill({json:{tenantId:own,issuer:'https://provider.example',clientId:'client',enabled:true,secretConfigured:true,version:0,allowedGroupIds:[uid],callbackUri:'https://fixture/callback',metadata,tenant:{id:own,name:'Own school'}}});
  }
  if(/^\/api\/v1\/tenants\/[^/]+$/.test(url.pathname)&&(current.status!=='ACTIVE'||current.scope==='TENANT'&&current.role!=='ADMIN'))return route.fulfill({status:403,json:{status:403}});
  if(/^\/api\/v1\/tenants\/[^/]+$/.test(url.pathname))return scope==='TENANT'&&url.pathname.endsWith(foreign)?route.fulfill({status:404,json:{status:404,detail:'Tenant not found in authorized scope'}}):route.fulfill({json:{id:url.pathname.split('/').at(-1),name:url.pathname.endsWith(foreign)?'Other school':'Own school',metadata}});
  if(url.pathname==='/api/v1/users')return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return {calls,demote:()=>{current={...current,role:'USER'};}};
}
for(const scope of ['GLOBAL','TENANT'])for(const role of ['ADMIN','USER'])test(`${scope} ${role}: request and navigation capabilities`,async({page})=>{
 const f=await setup(page,scope,role);await page.goto('/dashboard');await expect(page.locator('main h1')).toHaveText(en.redesign.dashboard);await page.waitForLoadState('networkidle');const nav=page.locator('#desktop-navigation');
 expect(f.calls.some(c=>c.path==='/api/v1/tenants')).toBe(scope==='GLOBAL');
 await expect(nav.getByRole('link',{name:en.fields.users,exact:true})).toHaveCount(role==='ADMIN'?1:0);await expect(nav.getByRole('link',{name:en.redesign.navigationIserv,exact:true})).toHaveCount(role==='ADMIN'?1:0);await expect(nav.getByRole('link',{name:en.redesign.navigationAudit,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 await expect(nav.getByRole('link',{name:en.fields.tenants,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 if(role==='ADMIN')await expect(nav.getByRole('link',{name:en.fields.tenants,exact:true})).toHaveAttribute('href',scope==='GLOBAL'?'/tenants':`/tenants/${own}`);
 else expect(f.calls.some(c=>/\/users|\/audit-log|\/oidc$/.test(c.path))).toBe(false);
 await expect(page.getByRole('button',{name:en.ui.tenantContext,exact:true})).toHaveCount(scope==='GLOBAL'?1:0);await expect(page.getByRole('alert')).toHaveCount(0);
});
for(const role of ['ADMIN','USER'])test(`GLOBAL ${role}: authoritative dropdown changes business context only`,async({page})=>{
 const f=await setup(page,'GLOBAL',role);await page.goto('/login');await expect(page).toHaveURL(/\/dashboard$/);const menu=page.getByRole('button',{name:en.ui.tenantContext,exact:true});await expect(menu).toContainText(en.ui.globalContext);await menu.click();await page.getByRole('menuitem',{name:'Other school',exact:true}).click();await expect(page).toHaveURL(new RegExp(`/tenants/${foreign}/dashboard$`));await expect(menu).toContainText('Other school');await expect.poll(()=>f.calls.some(c=>c.path===`/api/v1/tenants/${foreign}/assets`)).toBe(true);await page.reload();await expect(menu).toContainText('Other school');await menu.click();await page.getByRole('menuitem',{name:en.ui.globalContext,exact:true}).click();await expect(page).toHaveURL(/\/dashboard$/);await expect(menu).toContainText(en.ui.globalContext);
 expect(f.calls.some(c=>c.path.includes('/oidc/organizations'))).toBe(false);expect(f.calls.some(c=>c.method!=='GET'&&!c.path.endsWith('/refresh'))).toBe(false);
});
for(const scope of ['GLOBAL','TENANT'])test(`${scope} ADMIN: direct Tenant detail refresh is independent and not context switching`,async({page})=>{
 const f=await setup(page,scope,'ADMIN');await page.goto(`/tenants/${own}`);await expect(page.getByRole('heading',{name:'Own school',exact:true})).toBeVisible();await expect(page.locator('main')).toContainText(en.ui.unresolvedActor);await expect(page.locator('#desktop-navigation').getByRole('link',{name:en.fields.tenants,exact:true})).toHaveClass(/nav-active/);await expect(page.locator('#application-topbar > a')).toHaveAttribute('href',scope==='GLOBAL'?'/dashboard':`/tenants/${own}/dashboard`);await page.reload();await expect(page.getByRole('heading',{name:'Own school',exact:true})).toBeVisible();expect(f.calls.some(c=>c.path===`/api/v1/tenants/${own}`)).toBe(true);if(scope==='TENANT')expect(f.calls.some(c=>c.path==='/api/v1/tenants')).toBe(false);
});
test('GLOBAL USER safe detail has no administration actions',async({page})=>{await setup(page);await page.goto(`/tenants/${own}`);await expect(page.getByRole('heading',{name:'Own school',exact:true})).toBeVisible();await expect(page.locator('main button')).toHaveCount(0);await expect(page.locator('#desktop-navigation a[href="/tenants"]')).toHaveCount(0);});
test('TENANT USER cannot open own Tenant administration detail',async({page})=>{
 const f=await setup(page,'TENANT','USER');await page.goto(`/tenants/${own}`);
 await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable,exact:true})).toBeVisible();
 expect(f.calls.some(c=>c.path===`/api/v1/tenants/${own}`)).toBe(false);
});
test('TENANT ADMIN foreign detail is nondisclosing; foreign business URL redirects before request',async({page})=>{
 const f=await setup(page,'TENANT','ADMIN');await page.goto(`/tenants/${foreign}`);await expect(page.getByRole('alert')).toHaveText(en.errors.NOT_FOUND);await expect(page.getByText('Other school',{exact:true})).toHaveCount(0);await page.goto(`/tenants/${foreign}/locations`);await expect(page).toHaveURL(new RegExp(`/tenants/${own}/dashboard$`));expect(f.calls.some(c=>c.path===`/api/v1/tenants/${foreign}/locations`)).toBe(false);
});
for(const scope of ['GLOBAL','TENANT'])for(const target of ['/users','/audit','/oidc','/tenants'])test(`${scope} USER: blocked administration URL ${target}`,async({page})=>{const f=await setup(page,scope);await page.goto(target);await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable,exact:true})).toBeVisible();expect(f.calls.some(c=>/\/users|\/audit-log|\/oidc$/.test(c.path))).toBe(false);if(scope==='TENANT')expect(f.calls.some(c=>c.path.startsWith('/api/v1/tenants'))).toBe(false);});
for(const scope of ['GLOBAL','TENANT'])for(const target of [`/users/${foreign}`,`/tenants/${own}/oidc`,`/audit/${foreign}`])test(`${scope} USER: blocked direct administration URL ${target}`,async({page})=>{
 const f=await setup(page,scope);await page.goto(target);
 await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable,exact:true})).toBeVisible();
 expect(f.calls.some(c=>/\/users\/|\/audit-log|\/oidc$/.test(c.path))).toBe(false);
});
test('TENANT ADMIN reads own elevated User without management commands',async({page})=>{
 const f=await setup(page,'TENANT','ADMIN');
 await page.route('**/api/v1/users**',route=>{const path=new URL(route.request().url()).pathname;return route.fulfill({json:path.endsWith(foreign)?{id:foreign,scope:'TENANT',tenantId:own,role:'ADMIN',status:'ACTIVE',displayName:'Own elevated',tenant:{id:own,name:'Own school'},metadata,globalAccount:null}:{items:[{id:foreign,scope:'TENANT',tenantId:own,role:'ADMIN',status:'ACTIVE',displayName:'Own elevated',tenant:{id:own,name:'Own school'},metadata}],page:0,size:20,totalElements:1}});});
 await page.goto('/users');await expect(page.getByText('Own elevated',{exact:true})).toBeVisible();
 await page.goto(`/users/${foreign}`);await expect(page.getByRole('heading',{name:'Own elevated',exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:en.ui.disableUser,exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:en.ui.changeUserRole,exact:true})).toHaveCount(0);
 expect(f.calls.some(c=>c.path==='/api/v1/tenants')).toBe(false);
});
for(const language of ['en','de'] as const)for(const width of [1440,390])test(`${language}/${width} administration collection toolbar and readable User Tenant`,async({page})=>{
 const t=language==='de'?de:en,f=await setup(page,'GLOBAL','ADMIN','ACTIVE',language);await page.setViewportSize({width,height:900});
 await page.route('**/api/v1/tenants',route=>route.fulfill({json:[{id:own,name:'Same school',metadata},{id:foreign,name:'Same school',metadata}]}));
 await page.route('**/api/v1/users?*',route=>route.fulfill({json:{items:[
  {id:uid,scope:'GLOBAL',tenantId:null,tenant:null,role:'ADMIN',status:'ACTIVE',displayName:'Global reader',email:'global@example.test',metadata},
  {id:own,scope:'TENANT',tenantId:own,tenant:{id:own,name:'Same school'},role:'USER',status:'ACTIVE',displayName:'Tenant reader',email:null,metadata},
 ],page:0,size:20,totalElements:2}}));
 await page.goto('/users');const toolbar=page.getByRole('toolbar',{name:t.ui.users});const create=toolbar.getByRole('button',{name:t.actions.create,exact:true});
 await expect(create).toHaveClass(/bg-primary/);await expect(toolbar.getByRole('button').first()).toHaveText(t.actions.create);
 await expect(page.locator('thead th')).toHaveCount(7);for(const [index,name] of [t.fields.name,t.fields.email,t.fields.role,t.fields.tenantName,t.fields.status,t.fields.createdAt,t.fields.createdByName].entries())await expect(page.locator('thead th').nth(index)).toContainText(name);
 await expect(page.getByRole('row',{name:/Global reader/})).toContainText(t.enums.GLOBAL);
 await expect(page.getByRole('row',{name:/Global reader/})).toContainText('global@example.test');
 await expect(page.getByRole('row',{name:/Tenant reader/})).toContainText(`Same school · ${own}`);
 await expect(toolbar.getByRole('button',{name:t.actions.filter,exact:true})).toBeVisible();await expect(toolbar.getByRole('button',{name:t.actions.resetFilters})).toBeVisible();
 await create.focus();await expect(create).toBeFocused();expect(await create.evaluate(el=>getComputedStyle(el).outlineStyle)).toBe('solid');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.goto('/tenants');await expect(page.getByRole('toolbar',{name:t.ui.tenants}).getByRole('button',{name:t.actions.create,exact:true})).toHaveClass(/bg-primary/);
 expect(f.calls.filter(c=>c.method!=='GET'&&!c.path.endsWith('/refresh'))).toHaveLength(0);
});

for(const scope of ['GLOBAL','TENANT'] as const)for(const language of ['en','de'] as const)for(const width of [1440,390])test(`${scope} ${language}/${width} User table has the exact approved ordered headers and matching cells`,async({page})=>{
 await setup(page,scope,'ADMIN','ACTIVE',language);await page.setViewportSize({width,height:900});
 const rows=[{id:uid,scope:'GLOBAL',tenantId:null,tenant:null,role:'ADMIN',status:'ACTIVE',displayName:'Global reader',email:'global@example.test',metadata},
  {id:foreign,scope:'TENANT',tenantId:own,tenant:{id:own,name:'Own school'},role:'USER',status:'ACTIVE',displayName:'Tenant reader',email:null,metadata}];
 await page.route('**/api/v1/users?*',route=>{const items=scope==='GLOBAL'?rows:rows.slice(1);return route.fulfill({json:{items,page:0,size:20,totalElements:items.length}});});
 await page.goto('/users');const table=page.locator('main .data-table');
 const expected=language==='de'?['Name','E-Mail','Rolle','Mandant','Status','Erstellt am','Erstellt von']:['Name','Email','Role','Tenant','Status','Created at','Created by'];
 await expect(table.locator('thead th')).toHaveText(expected);
 await expect(table.locator('thead th button svg')).toHaveCount(3);
 const cells=table.locator('tbody tr').first().locator('td');await expect(cells).toHaveCount(expected.length);
 const geometry=await table.evaluate(node=>({headers:Array.from(node.querySelectorAll('thead th'),cell=>({x:cell.getBoundingClientRect().x,width:cell.getBoundingClientRect().width})),cells:Array.from(node.querySelectorAll('tbody tr:first-child td'),cell=>({x:cell.getBoundingClientRect().x,width:cell.getBoundingClientRect().width}))}));
 for(let index=0;index<expected.length;index++){expect(Math.abs(geometry.headers[index].x-geometry.cells[index].x)).toBeLessThan(1);expect(Math.abs(geometry.headers[index].width-geometry.cells[index].width)).toBeLessThan(1);}
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('TENANT ADMIN own provider read/update uses signed Tenant and existing command body',async({page})=>{
 const f=await setup(page,'TENANT','ADMIN');await page.goto('/oidc');const issuer=page.getByLabel(en.ui.iservIssuer,{exact:true});await expect(issuer).toHaveValue('https://provider.example');await issuer.fill('https://changed.example');await page.getByRole('button',{name:en.ui.saveOIDCConfiguration,exact:true}).click();await expect.poll(()=>f.calls.filter(c=>c.method==='PUT').length).toBe(1);expect(f.calls.find(c=>c.method==='PUT')).toEqual({path:`/api/v1/tenants/${own}/oidc`,method:'PUT',body:{issuer:'https://changed.example',clientId:'client',clientSecret:null,enabled:true,version:0,allowedGroupIds:[uid]}});expect(f.calls.some(c=>c.path==='/api/v1/tenants')).toBe(false);
});
test('refresh demotion removes admin capabilities and rejects stale provider response',async({page})=>{
 const f=await setup(page,'TENANT','ADMIN');await page.goto('/oidc');await expect(page.getByLabel(en.ui.iservIssuer,{exact:true})).toHaveValue('https://provider.example');f.demote();await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.refresh();});await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable,exact:true})).toBeVisible();await expect(page.locator('#desktop-navigation a[href="/users"]')).toHaveCount(0);await expect(page.getByLabel(en.ui.iservIssuer,{exact:true})).toHaveCount(0);
});
for(const status of ['PENDING','DISABLED'])test(`${status}: no business/admin reads`,async({page})=>{const f=await setup(page,'GLOBAL','ADMIN',status);await page.goto('/tenants');if(status==='PENDING')await expect(page.getByText(en.ui.pendingHelp,{exact:true})).toBeVisible();else await expect(page.getByLabel(en.redesign.email,{exact:true})).toBeVisible();expect(f.calls.filter(c=>!c.path.startsWith('/api/v1/auth'))).toEqual([]);});
for(const language of ['en','de'])for(const width of [1440,1024,768,390])test(`${language} ${width}: context/detail navigation, focus and stable layout`,async({page})=>{
 const r=language==='de'?de:en;await page.setViewportSize({width,height:900});await setup(page,'GLOBAL','USER','ACTIVE',language);await page.goto(`/tenants/${own}`);await expect(page.getByRole('heading',{name:'Own school',exact:true})).toBeVisible();const trigger=page.getByRole('button',{name:r.ui.tenantContext,exact:true});await trigger.focus();await expect(trigger).toBeFocused();await trigger.press('Enter');await page.getByRole('menuitem',{name:'Own school',exact:true}).waitFor();await page.keyboard.press('Escape');await expect(trigger).toBeFocused();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.cache/frontend-f1b/context-${language}-${width}.png`});
});

test('GLOBAL ADMIN collection create stays available and row opens modal detail',async({page})=>{
 const f=await setup(page,'GLOBAL','ADMIN');await page.goto('/tenants');await page.getByRole('button',{name:en.actions.create,exact:true}).click();await page.getByLabel(en.ui.tenantName,{exact:true}).fill('Fixture school');await page.getByRole('dialog').getByRole('button',{name:en.actions.create,exact:true}).click();await expect.poll(()=>f.calls.filter(c=>c.method==='POST'&&c.path==='/api/v1/tenants').length).toBe(1);expect(f.calls.find(c=>c.method==='POST'&&c.path==='/api/v1/tenants')?.body).toEqual({name:'Fixture school'});await expect(page.getByRole('dialog')).toHaveCount(0);await page.getByRole('row').filter({hasText:'Own school'}).getByRole('button').click();await expect(page).toHaveURL(new RegExp(`/tenants/${own}$`));await expect(page.getByRole('dialog')).toBeVisible();await expect(page.getByRole('heading',{name:'Own school',exact:true})).toBeVisible();
});
test('TENANT ADMIN initial provider configuration uses signed-own UUID',async({page})=>{
 const f=await setup(page,'TENANT','ADMIN');await page.route(`**/api/v1/tenants/${own}/oidc`,async route=>{if(route.request().method()==='GET')return route.fulfill({status:404,json:{status:404,detail:'Not configured'}});const req=route.request();f.calls.push({path:`/api/v1/tenants/${own}/oidc`,method:req.method(),body:req.postDataJSON()});return route.fulfill({json:{tenantId:own,issuer:'https://new.example',clientId:'new',enabled:true,secretConfigured:true,version:0,allowedGroupIds:[uid],callbackUri:'https://fixture/callback',metadata,tenant:{id:own,name:'Own school'}}});});
 await page.goto('/oidc');await expect(page.getByText(en.redesign.configureProvider,{exact:true})).toBeVisible();await page.getByLabel(en.ui.iservIssuer,{exact:true}).fill('https://new.example');await page.getByLabel(en.ui.oidcClientID,{exact:true}).fill('new');await page.getByLabel(en.ui.oidcClientSecretWriteOnlyBlankRetainsExisting,{exact:true}).fill('intercepted-secret');await page.locator('textarea').fill(uid);await page.getByRole('button',{name:en.ui.saveOIDCConfiguration,exact:true}).click();await expect.poll(()=>f.calls.filter(c=>c.method==='PUT').length).toBe(1);expect(f.calls.find(c=>c.method==='PUT')?.body).toEqual({issuer:'https://new.example',clientId:'new',clientSecret:'intercepted-secret',enabled:true,version:null,allowedGroupIds:[uid]});await expect(page.getByLabel(en.ui.oidcClientSecretWriteOnlyBlankRetainsExisting,{exact:true})).toHaveValue('');
});
test('late singleton read cannot repopulate demoted administration',async({page})=>{
 const f=await setup(page,'TENANT','ADMIN');let release!:()=>void;const gate=new Promise<void>(resolve=>release=resolve);let started=false;await page.route(`**/api/v1/tenants/${own}/oidc`,async route=>{started=true;await gate;await route.fulfill({json:{tenantId:own,issuer:'https://late.example',clientId:'late',enabled:true,secretConfigured:true,version:0,allowedGroupIds:[uid],callbackUri:'https://fixture/callback',metadata,tenant:{id:own,name:'Own school'}}});});
 await page.goto('/oidc');await expect.poll(()=>started).toBe(true);f.demote();await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.refresh();});await expect(page.getByRole('heading',{name:en.redesign.pageUnavailable,exact:true})).toBeVisible();release();await page.waitForLoadState('networkidle');await expect(page.getByLabel(en.ui.iservIssuer,{exact:true})).toHaveCount(0);await expect(page.getByText('https://late.example',{exact:true})).toHaveCount(0);
});
for(const language of ['en','de'])test(`${language}: mobile signed Tenant administration destinations`,async({page})=>{
 const r=language==='de'?de:en;await page.setViewportSize({width:390,height:900});const f=await setup(page,'TENANT','ADMIN','ACTIVE',language);await page.goto(`/tenants/${own}/assets`);const toggle=page.getByRole('button',{name:r.redesign.toggleNavigation,exact:true});await toggle.click();await page.getByRole('dialog').getByRole('link',{name:r.fields.tenants,exact:true}).click();await expect(page).toHaveURL(new RegExp(`/tenants/${own}$`));await expect(page.getByRole('heading',{name:'Own school',exact:true})).toBeVisible();await toggle.click();await page.getByRole('dialog').getByRole('link',{name:r.redesign.navigationIserv,exact:true}).click();await expect(page.getByLabel(r.ui.iservIssuer,{exact:true})).toHaveValue('https://provider.example');await expect(page.getByRole('dialog')).toHaveCount(0);expect(f.calls.some(c=>c.path==='/api/v1/tenants')).toBe(false);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
for(const language of ['en','de'] as const)for(const width of [1440,390])test(`${language}/${width} Tenant columns, modal and semantic User breadcrumb`,async({page})=>{
 const t=language==='de'?de:en;await page.setViewportSize({width,height:900});await setup(page,'GLOBAL','ADMIN','ACTIVE',language);
 await page.route(`**/api/v1/users/${foreign}`,route=>route.fulfill({json:{id:foreign,scope:'GLOBAL',tenantId:null,role:'USER',status:'ACTIVE',displayName:'Review User',tenant:null,metadata,globalAccount:null}}));
 await page.goto('/tenants');for(const [index,name] of [t.fields.name,t.fields.createdAt,t.fields.createdByName].entries())await expect(page.locator('thead th').nth(index)).toContainText(name);
 await page.getByRole('row').filter({hasText:'Own school'}).getByRole('button').click();await expect(page.getByRole('dialog')).toBeVisible();await page.keyboard.press('Escape');await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page).toHaveURL(/\/tenants$/);
 await page.goto(`/users/${foreign}`);const breadcrumb=page.getByRole('navigation',{name:t.redesign.breadcrumb});await expect(breadcrumb.getByRole('link',{name:t.redesign.home})).toHaveAttribute('href','/dashboard');await expect(breadcrumb.getByRole('link',{name:t.fields.users})).toHaveAttribute('href','/users');await expect(breadcrumb.locator('[aria-current="page"]')).toHaveText('Review User');await breadcrumb.getByRole('link',{name:t.fields.users}).focus();await expect(breadcrumb.getByRole('link',{name:t.fields.users})).toBeFocused();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
