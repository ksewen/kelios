import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
import {fixtureMetadata} from './backend-fixtures';
import {TOAST_LIFETIME_MS} from '../src/components/ui/toast';

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const otherTenant='eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee';
const duplicateTenant='ffffffff-ffff-ffff-ffff-ffffffffffff';
const globalId='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const localId='cccccccc-cccc-cccc-cccc-cccccccccccc';
const foreignId='99999999-9999-9999-9999-999999999999';
const userId='dddddddd-dddd-dddd-dddd-dddddddddddd';
const row=(id:string,name:string,local:boolean)=>({id,name,visibility:local?'TENANT':'GLOBAL',tenantId:local?tenant:null,tenant:local?{id:tenant,name:'School North'}:null,metadata:{...fixtureMetadata,createdByDisplayName:'Ada Admin',updatedByDisplayName:'Bert Admin'}});
async function setup(page:Page,role:'ADMIN'|'USER'='ADMIN',scope:'GLOBAL'|'TENANT'='GLOBAL'){
 const requests:URL[]=[];
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:userId,scope,role,status:'ACTIVE',tenantId:scope==='TENANT'?tenant:null,displayName:'Fixture'}}});
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'School North',metadata:fixtureMetadata},{id:otherTenant,name:'School South',metadata:fixtureMetadata},{id:duplicateTenant,name:'School South',metadata:fixtureMetadata}]});
  if(url.pathname===`/api/v1/tenants/${tenant}`)return route.fulfill({json:{id:tenant,name:'School North',metadata:fixtureMetadata}});
  if(url.pathname===`/api/v1/catalog/categories/${localId}`)return route.fulfill({json:{...row(localId,'Local category',true),deleted:false,deletedAt:null,deletedBy:null}});
  if(url.pathname.endsWith('/categories')){
   requests.push(url);
   const items=[row(globalId,'Global category',false),row(localId,'Local category',true)];
   if(url.searchParams.get('direction')==='desc')items.reverse();
   return route.fulfill({json:{items,totalElements:2,page:Number(url.searchParams.get('page')??0),size:Number(url.searchParams.get('size')??20)}});
  }
  return route.fulfill({json:{items:[],totalElements:0,page:0,size:20}});
 });
 return requests;
}
async function filterFixture(page:Page,scope:'GLOBAL'|'TENANT'){
 const requests:URL[]=[];
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());
  if(route.request().method()!=='GET'||!url.pathname.endsWith('/categories'))return route.fallback();
  requests.push(url);
  const effectiveTenant=scope==='TENANT'?tenant:url.pathname.match(/^\/api\/v1\/tenants\/([^/]+)\/catalog\/categories$/)?.[1]??null;
  const foreign={...row(foreignId,'South category',true),tenantId:otherTenant,tenant:{id:otherTenant,name:'School South'}};
  const visible=[row(globalId,'Global category',false),...(effectiveTenant===null||effectiveTenant===tenant?[row(localId,'North category',true)]:[]),...(effectiveTenant===null||effectiveTenant===otherTenant?[foreign]:[])];
  const visibility=url.searchParams.get('visibility');
  const q=url.searchParams.get('q')?.toLowerCase();
  const items=visible.filter(item=>(!visibility||item.visibility===visibility)&&(!q||item.name.toLowerCase().includes(q)));
  const size=Number(url.searchParams.get('size')??20),pageNumber=Number(url.searchParams.get('page')??0);
  return route.fulfill({json:{items:items.slice(pageNumber*size,(pageNumber+1)*size),totalElements:items.length,page:pageNumber,size}});
 });
 return requests;
}
for(const language of ['en','de'] as const)for(const width of [1440,390])test(`${language}/${width} Catalog Create with no Tenants has Global ownership and no empty helper`,async({page})=>{
 const t=language==='de'?de:en;await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);await setup(page);await page.route('**/api/v1/tenants',route=>route.fulfill({json:[]}));
 const writes:string[]=[];await page.route('**/api/v1/catalog/categories',route=>{if(route.request().method()!=='POST')return route.fallback();writes.push(new URL(route.request().url()).pathname);return route.fulfill({status:201,json:{id:localId}});});
 await page.setViewportSize({width,height:900});await page.goto('/categories');await page.getByRole('toolbar').getByRole('button',{name:t.actions.create,exact:true}).click();const dialog=page.getByRole('dialog');
 await expect(dialog.getByLabel(t.fields.tenantName)).toHaveValue('');await expect(dialog.getByText(/Keine Mandanten verfügbar|No Tenants available/i)).toHaveCount(0);
 await dialog.getByLabel(t.ui.name).fill('Global entry');await dialog.getByRole('button',{name:t.actions.create,exact:true}).click();await expect(dialog).toHaveCount(0);expect(writes).toEqual(['/api/v1/catalog/categories']);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('GLOBAL ADMIN sees all Categories at root and a bounded set in each Tenant context',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','de'));
 await setup(page);const requests=await filterFixture(page,'GLOBAL');
 await page.goto('/categories');
 const toolbar=page.getByRole('toolbar',{name:de.fields.categories}),rows=toolbar.getByLabel(de.ui.categoryRows,{exact:true});
 await expect(rows).toHaveValue('');
 await expect(rows.locator('option')).toHaveText(['Alle','Nur Globale','Mit Mandant']);
 await expect(toolbar.getByRole('button',{name:de.actions.moreFilters})).toHaveCount(0);
 await expect(page.getByRole('dialog')).toHaveCount(0);
 await expect(page.locator('tbody')).toContainText('Global category');
 await expect(page.locator('tbody')).toContainText('North category');
 await expect(page.locator('tbody')).toContainText('South category');
 expect(requests.at(-1)?.pathname).toBe('/api/v1/catalog/categories');
 await rows.selectOption('GLOBAL');
 await expect(page.locator('tbody')).toContainText('Global category');
 await expect(page.locator('tbody')).not.toContainText('North category');
 expect(requests.at(-1)?.searchParams.get('visibility')).toBe('GLOBAL');
 await rows.selectOption('TENANT');
 await expect(page.locator('tbody')).toContainText('North category');
 await expect(page.locator('tbody')).toContainText('South category');
 await expect(page.locator('tbody')).not.toContainText('Global category');
 expect(requests.at(-1)?.searchParams.get('visibility')).toBe('TENANT');
 await toolbar.getByRole('textbox',{name:de.ui.categorySearch}).fill('South');
 await toolbar.getByRole('button',{name:de.actions.filter,exact:true}).click();
 await expect(page.locator('tbody')).toContainText('South category');
 await expect(page.locator('tbody')).not.toContainText('North category');
 expect(requests.at(-1)?.searchParams.get('q')).toBe('South');
 await page.goto(`/tenants/${tenant}/categories`);
 await expect(rows).toHaveValue('');
 await expect(rows.locator('option')).toHaveText(['Alle','Nur Globale','Mit Mandant']);
 await expect(page.locator('tbody')).toContainText('Global category');
 await expect(page.locator('tbody')).toContainText('North category');
 await expect(page.locator('tbody')).not.toContainText('South category');
 await rows.selectOption('TENANT');
 await expect(page.locator('tbody')).toContainText('North category');
 await expect(page.locator('tbody')).not.toContainText('Global category');
 expect(requests.at(-1)?.searchParams.get('visibility')).toBe('TENANT');
 await page.goto(`/tenants/${otherTenant}/categories`);
 await expect(page.locator('tbody')).toContainText('Global category');
 await expect(page.locator('tbody')).toContainText('South category');
 await expect(page.locator('tbody')).not.toContainText('North category');
 expect(requests.at(-1)?.searchParams.has('visibility')).toBe(false);
});
for(const width of [1440,390])test(`Global Tenant trigger has stable width and readable long names at ${width}px`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page);
 const north='Karoline Breitinger Schule',south='Kaufmännische Schule Künzelsau';
 await page.route('**/api/v1/tenants',route=>route.fulfill({json:[{id:tenant,name:north,metadata:fixtureMetadata},{id:otherTenant,name:south,metadata:fixtureMetadata}]}));
 await page.setViewportSize({width,height:900});await page.goto('/categories');
 const trigger=page.getByRole('button',{name:en.ui.tenantContext,exact:true}),account=page.getByRole('button',{name:en.redesign.accountMenu,exact:true});
 const before=await trigger.boundingBox(),accountBefore=await account.boundingBox();
 for(const [id,name] of [[tenant,north],[otherTenant,south]] as const){
  await trigger.click();const option=page.getByRole('menuitem',{name,exact:true});await expect(option).toBeVisible();
  const popup=page.getByRole('menu');expect((await popup.boundingBox())?.width).toBeGreaterThan(before!.width);
  await option.click();await expect(page).toHaveURL(new RegExp(`/tenants/${id}/categories$`));
  await expect(trigger).toHaveAttribute('title',name);
  expect((await trigger.boundingBox())?.width).toBe(before?.width);
  expect((await account.boundingBox())?.x).toBe(accountBefore?.x);
 }
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('TENANT ADMIN can narrow own Categories without seeing another Tenant',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 await setup(page,'ADMIN','TENANT');const requests=await filterFixture(page,'TENANT');
 await page.goto(`/tenants/${tenant}/categories`);
 const toolbar=page.getByRole('toolbar',{name:en.fields.categories}),rows=toolbar.getByLabel(en.ui.categoryRows,{exact:true});
 await expect(rows.locator('option')).toHaveText([en.ui.categoryRowsAll,en.ui.categoryRowsGlobal,en.ui.categoryRowsTenant]);
 await expect(page.locator('tbody')).toContainText('Global category');
 await expect(page.locator('tbody')).toContainText('North category');
 await expect(page.locator('tbody')).not.toContainText('South category');
 await rows.selectOption('TENANT');
 await expect(page.locator('tbody')).toContainText('North category');
 await expect(page.locator('tbody')).not.toContainText('Global category');
 await expect(page.locator('tbody')).not.toContainText('South category');
 expect(requests.at(-1)?.searchParams.get('visibility')).toBe('TENANT');
});
test('GLOBAL ADMIN Category Create owns its draft independently of operating context',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 await page.setViewportSize({width:1440,height:900});
 const reads=await setup(page);const posts:{path:string;body:unknown}[]=[];
 await page.route('**/api/v1/**',route=>{if(route.request().method()!=='POST'||!route.request().url().includes('/catalog/categories'))return route.fallback();posts.push({path:new URL(route.request().url()).pathname,body:route.request().postDataJSON()});return route.fulfill({status:201,json:{id:globalId}});});
 await page.goto(`/tenants/${tenant}/categories`);const contextButton=page.getByRole('button',{name:en.ui.tenantContext});await expect(contextButton).toBeVisible();
 const open=async()=>{await page.getByRole('toolbar').getByRole('button',{name:en.actions.create}).click();return page.getByRole('dialog');};
 let dialog=await open(),owner=dialog.getByLabel(en.fields.tenantName);
 await expect(owner).toHaveValue('');await expect(owner.locator('option').first()).toHaveText(en.ui.globalContext);
 await expect(owner.locator(`option[value="${otherTenant}"]`)).toContainText('School South');
 await expect(owner.locator(`option[value="${otherTenant}"]`)).toContainText(otherTenant);
 await owner.selectOption(otherTenant);await dialog.getByLabel(en.ui.name).fill('Abandoned');await dialog.getByRole('button',{name:en.redesign.cancel}).click();
 await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm}).click();expect(posts).toHaveLength(0);
 dialog=await open();owner=dialog.getByLabel(en.fields.tenantName);await expect(owner).toHaveValue('');await expect(dialog.getByLabel(en.ui.name)).toHaveValue('');
 await owner.selectOption(otherTenant);await dialog.getByLabel(en.ui.name).fill('Laptop');const before=reads.length;await dialog.getByRole('button',{name:en.actions.create}).click();
 await expect(dialog).toHaveCount(0);expect(posts).toEqual([{path:`/api/v1/tenants/${otherTenant}/catalog/categories`,body:{name:'Laptop'}}]);expect(reads.length).toBe(before);
 await expect(page.getByRole('status').filter({hasText:'Category created for School South.'})).toBeVisible();await expect(page).toHaveURL(new RegExp(`/tenants/${tenant}/categories$`));await expect(contextButton).toBeVisible();
 dialog=await open();await expect(dialog.getByLabel(en.fields.tenantName)).toHaveValue('');
 await dialog.getByLabel(en.ui.name).fill('Global Laptop');await dialog.getByRole('button',{name:en.actions.create}).click();
 expect(posts.at(-1)).toEqual({path:'/api/v1/catalog/categories',body:{name:'Global Laptop'}});await expect.poll(()=>reads.length).toBeGreaterThan(before);
 await expect(page.getByRole('status').filter({hasText:'Category created for Global.'})).toBeVisible();await expect(page).toHaveURL(new RegExp(`/tenants/${tenant}/categories$`));
 const afterGlobal=reads.length;dialog=await open();await dialog.getByLabel(en.fields.tenantName).selectOption(tenant);await dialog.getByLabel(en.ui.name).fill('Own context');await dialog.getByRole('button',{name:en.actions.create}).click();
 expect(posts.at(-1)).toEqual({path:`/api/v1/tenants/${tenant}/catalog/categories`,body:{name:'Own context'}});await expect.poll(()=>reads.length).toBeGreaterThan(afterGlobal);
});
test('GLOBAL ADMIN starts at Global in Global context and acknowledges the selected Tenant in German',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','de'));await page.setViewportSize({width:390,height:900});const reads=await setup(page);const posts:string[]=[];
 await page.route('**/api/v1/**',route=>{if(route.request().method()!=='POST'||!route.request().url().includes('/catalog/categories'))return route.fallback();posts.push(new URL(route.request().url()).pathname);return route.fulfill({status:201,json:{id:localId}});});
 await page.goto('/categories');await page.getByRole('toolbar').getByRole('button',{name:de.actions.create}).click();const dialog=page.getByRole('dialog');
 await expect(dialog.getByLabel(de.fields.tenantName)).toHaveValue('');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await dialog.getByLabel(de.fields.tenantName).selectOption(tenant);await dialog.getByLabel(de.ui.name).fill('Laptop');const before=reads.length;await dialog.getByRole('button',{name:de.actions.create}).click();
 expect(posts).toEqual([`/api/v1/tenants/${tenant}/catalog/categories`]);await expect.poll(()=>reads.length).toBeGreaterThan(before);await expect(page.getByRole('status').filter({hasText:'Kategorie für School North erstellt.'})).toBeVisible();await expect(page).toHaveURL(/\/categories$/);
});
test('Category success Toast is transient, focus-safe and does not reflow the 390px collection',async({page})=>{
 await page.clock.install();await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','de'));
 await page.setViewportSize({width:390,height:900});await setup(page);
 const posts:string[]=[];await page.route('**/api/v1/**',route=>{if(route.request().method()!=='POST'||!route.request().url().includes('/catalog/categories'))return route.fallback();posts.push(new URL(route.request().url()).pathname);return route.fulfill({status:201,json:{id:localId}});});
 await page.goto(`/tenants/${tenant}/categories`);
 const toolbar=page.getByRole('toolbar',{name:de.fields.categories}),table=page.locator('.table-frame');
 const before={toolbar:await toolbar.boundingBox(),table:await table.boundingBox(),scroll:await page.evaluate(()=>scrollY)};
 await toolbar.getByRole('button',{name:de.actions.create}).click();const dialog=page.getByRole('dialog');
 await dialog.getByLabel(de.fields.tenantName).selectOption(otherTenant);await dialog.getByLabel(de.ui.name).fill('Laptop');await dialog.getByRole('button',{name:de.actions.create}).click();
 const toast=page.getByRole('status').filter({hasText:'Kategorie für School South erstellt.'});
 await expect(toast).toBeVisible();await expect(toast).not.toContainText(otherTenant);expect(posts).toEqual([`/api/v1/tenants/${otherTenant}/catalog/categories`]);
 const rect=await toast.boundingBox();expect(rect).not.toBeNull();expect(rect!.x).toBeGreaterThanOrEqual(0);expect(rect!.x+rect!.width).toBeLessThanOrEqual(390);
 await expect.poll(async()=>({toolbar:await toolbar.boundingBox(),table:await table.boundingBox(),scroll:await page.evaluate(()=>scrollY)})).toEqual(before);
 expect(await page.evaluate(()=>!!document.activeElement?.closest('[role="status"]'))).toBe(false);
 await toolbar.getByRole('button',{name:de.actions.create}).click();const reopened=page.getByRole('dialog');
 const dialogBefore=await reopened.boundingBox();
 expect(await toast.evaluate(node=>Number(getComputedStyle(node.parentElement!).zIndex))).toBeLessThan(await reopened.evaluate(node=>Number(getComputedStyle(node).zIndex)));
 await page.clock.fastForward(TOAST_LIFETIME_MS+1);await expect(toast).toHaveCount(0);await expect(reopened).toHaveCount(1);
 expect(await reopened.boundingBox()).toEqual(dialogBefore);
 await reopened.getByRole('button',{name:de.redesign.cancel}).click();
 await expect.poll(async()=>({toolbar:await toolbar.boundingBox(),table:await table.boundingBox(),scroll:await page.evaluate(()=>scrollY)})).toEqual(before);
});
test('Category Toasts stack at three while a failed mutation stays contextual',async({page})=>{
 await page.clock.install();await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page);
 let fail=false;await page.route('**/api/v1/**',route=>{if(route.request().method()!=='POST'||!route.request().url().includes('/catalog/categories'))return route.fallback();return route.fulfill(fail?{status:409,json:{code:'CONCURRENT_OPERATION'}}:{status:201,json:{id:localId}});});
 await page.goto(`/tenants/${tenant}/categories`);const open=()=>page.getByRole('toolbar').getByRole('button',{name:en.actions.create}).click();
 for(let i=0;i<4;i++){await open();const dialog=page.getByRole('dialog');await dialog.getByLabel(en.ui.name).fill(`Category ${i}`);await dialog.getByRole('button',{name:en.actions.create}).click();await expect(dialog).toHaveCount(0);}
 const toasts=page.getByRole('status').filter({hasText:'Category created for Global.'});await expect(toasts).toHaveCount(3);
 const boxes=await toasts.evaluateAll(nodes=>nodes.map(node=>node.getBoundingClientRect().toJSON()));expect(boxes[0].bottom).toBeLessThan(boxes[1].top);expect(boxes[1].bottom).toBeLessThan(boxes[2].top);
 fail=true;await open();const dialog=page.getByRole('dialog');await dialog.getByLabel(en.ui.name).fill('Keep draft');await dialog.getByRole('button',{name:en.actions.create}).click();
 const error=dialog.getByRole('alert');await expect(error).toBeVisible();await expect(dialog.getByLabel(en.ui.name)).toHaveValue('Keep draft');
 await page.clock.fastForward(TOAST_LIFETIME_MS+1);await expect(toasts).toHaveCount(0);await expect(error).toBeVisible();await expect(dialog.getByLabel(en.ui.name)).toHaveValue('Keep draft');
});
test('TENANT ADMIN sees signed ownership and cannot choose Global or another Tenant',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page,'ADMIN','TENANT');const posts:{path:string;body:unknown}[]=[];
 await page.route('**/api/v1/**',route=>{if(route.request().method()!=='POST'||!route.request().url().includes('/catalog/categories'))return route.fallback();posts.push({path:new URL(route.request().url()).pathname,body:route.request().postDataJSON()});return route.fulfill({status:201,json:{id:localId}});});
 await page.goto(`/tenants/${tenant}/categories`);await page.getByRole('toolbar').getByRole('button',{name:en.actions.create}).click();const dialog=page.getByRole('dialog');
 const owner=dialog.getByLabel(en.fields.tenantName);await expect(owner).toHaveValue('School North');await expect(owner).toHaveAttribute('readonly','');await expect(dialog.locator('select')).toHaveCount(0);
 await dialog.getByLabel(en.ui.name).fill('Own category');await dialog.getByRole('button',{name:en.actions.create}).click();
 expect(posts).toEqual([{path:`/api/v1/tenants/${tenant}/catalog/categories`,body:{name:'Own category'}}]);await expect(page.getByRole('status').filter({hasText:'Category created for School North.'})).toBeVisible();
});
for(const [lang,text] of [['en',en],['de',de]] as const)for(const width of [1440,390])test(`Category ${lang} ${width}: toolbar, ownership, detail and sort`,async({page})=>{
 await page.addInitScript(language=>localStorage.setItem('kelios.ui.language',language),lang);
 const requests=await setup(page);await page.setViewportSize({width,height:900});await page.goto(`/tenants/${tenant}/categories`);
 const toolbar=page.getByRole('toolbar',{name:text.fields.categories});await expect(toolbar).toBeVisible();
 const order=await toolbar.locator('button,input,select').evaluateAll(nodes=>nodes.map(node=>node.tagName==='INPUT'?'search':node.getAttribute('aria-label')??node.textContent?.trim()));
 expect(order).toEqual([text.actions.create,text.actions.merge,'search',text.actions.filter,text.ui.categoryRows,text.actions.resetFilters]);
 const search=toolbar.getByRole('textbox',{name:text.ui.categorySearch});await expect(search).toHaveAttribute('placeholder',text.ui.categorySearchPlaceholder);
 const heights=await Promise.all([toolbar.getByRole('button',{name:text.actions.create}),toolbar.getByRole('button',{name:text.actions.merge}),search,toolbar.getByRole('button',{name:text.actions.filter,exact:true}),toolbar.getByLabel(text.ui.categoryRows,{exact:true})].map(async control=>(await control.boundingBox())?.height));
 expect(heights.every(height=>height!==undefined&&Math.abs(height-40)<1)).toBe(true);
 await expect(page.getByRole('columnheader',{name:text.fields.tenantName})).toBeVisible();
 await expect(page.getByRole('columnheader',{name:text.fields.createdAt})).toBeVisible();
 await expect(page.getByRole('columnheader',{name:text.fields.createdByName})).toBeVisible();
 for(const heading of [text.ui.name,text.fields.tenantName,text.fields.createdAt,text.fields.createdByName]){
  await expect(page.getByRole('columnheader',{name:heading})).toHaveCSS('font-weight','700');
  await expect(page.getByRole('columnheader',{name:heading})).toHaveCSS('text-align','center');
 }
 await expect(page.locator('.data-table tbody td').first()).toHaveCSS('text-align','left');
 await expect(page.locator('.data-table thead th').first()).toHaveCSS('border-bottom-width','1px');
 await expect(page.locator('.data-table thead th').nth(1)).toHaveCSS('border-left-width','1px');
 await expect(page.locator('.data-table tbody tr').nth(1).locator('td').first()).toHaveCSS('border-top-width','1px');
 await expect(page.locator('.data-table tbody td').nth(1)).toHaveCSS('border-left-width','1px');
 const pageSize=page.getByLabel(text.ui.pageSize),previous=page.getByRole('button',{name:text.ui.previousPage}),next=page.getByRole('button',{name:text.ui.nextPage});
 const pagerHeights=await Promise.all([pageSize,previous,next].map(async control=>(await control.boundingBox())?.height));
 expect(pagerHeights).toEqual([40,40,40]);
 const selectStyle=await toolbar.getByLabel(text.ui.categoryRows,{exact:true}).evaluate(node=>{const style=getComputedStyle(node);return {paddingEnd:Number.parseFloat(style.paddingInlineEnd),background:style.backgroundImage,position:style.backgroundPositionX};});
 expect(selectStyle.paddingEnd).toBeGreaterThanOrEqual(40);expect(selectStyle.background).not.toBe('none');expect(selectStyle.position).not.toBe('100%');
 await expect(page.getByRole('columnheader',{name:text.fields.createdAt}).getByRole('button')).toHaveCount(0);
 await expect(page.getByRole('columnheader',{name:text.fields.createdByName}).getByRole('button')).toHaveCount(0);
 await expect(page.getByRole('columnheader',{name:text.fields.visibility})).toHaveCount(0);
 await expect(page.getByRole('columnheader',{name:text.ui.open})).toHaveCount(0);
 const body=page.locator('tbody');await expect(body.getByText('School North')).toBeVisible();await expect(body.getByText('Global',{exact:true})).toBeVisible();await expect(body).not.toContainText(tenant);
 await expect(body.getByText('Ada Admin')).toHaveCount(2);await expect(body).not.toContainText(fixtureMetadata.createdBy);
 await expect(body).toContainText(new Intl.DateTimeFormat(lang,{year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',timeZoneName:'short'}).format(new Date(fixtureMetadata.createdAt)));
 const header=page.getByRole('columnheader',{name:text.fields.tenantName});const sort=header.getByRole('button');const before=await sort.boundingBox();
 await sort.click();await expect(header).toHaveAttribute('aria-sort','ascending');await expect.poll(()=>requests.at(-1)?.searchParams.get('sort')).toBe('tenantName');expect(requests.at(-1)?.searchParams.get('direction')).toBe('asc');
 await sort.click();await expect(header).toHaveAttribute('aria-sort','descending');expect(requests.at(-1)?.searchParams.get('direction')).toBe('desc');expect((await sort.boundingBox())?.width).toBe(before?.width);
 await expect(sort).toHaveCSS('font-weight','700');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 const link=body.getByRole('link',{name:'Local category'});await link.focus();await expect(link).toBeFocused();await link.press('Enter');await expect(page).toHaveURL(new RegExp(`/categories/${localId}$`));await expect(page.getByRole('dialog')).toBeVisible();
});
for(const [lang,text] of [['en',en],['de',de]] as const)for(const width of [1440,390])test(`Category ${lang} ${width}: readable audit and explicit detail editing`,async({page})=>{
 await page.addInitScript(language=>localStorage.setItem('kelios.ui.language',language),lang);
 await setup(page);await page.setViewportSize({width,height:900});
 const created='2026-10-12T12:00:00Z';let saved='Local category',fail=false;
 const writes:unknown[]=[];
 await page.route(`**/api/v1/catalog/categories/${localId}`,route=>{
  if(route.request().method()==='PUT'){
   writes.push(route.request().postDataJSON());
   if(fail)return route.fulfill({status:409,json:{code:'CONCURRENT_OPERATION',detail:'Unsafe raw detail'}});
   saved=(route.request().postDataJSON() as {name:string}).name;return route.fulfill({status:204,body:''});
  }
  if(route.request().method()==='GET')return route.fulfill({json:{...row(localId,saved,true),metadata:{...fixtureMetadata,createdAt:created,updatedAt:created,createdByDisplayName:'Ada Admin',updatedByDisplayName:'Bert Admin'},deleted:false,deletedAt:null,deletedBy:null}});
  return route.fallback();
 });
 await page.goto(`/categories/${localId}`);const detail=page.getByRole('article');
 await expect(detail.getByText('School North',{exact:true})).toBeVisible();await expect(detail.getByText('Ada Admin')).toBeVisible();await expect(detail.getByText('Bert Admin')).toBeVisible();
 await expect(detail.getByText(new Intl.DateTimeFormat(lang,{year:'numeric',month:'short',day:'numeric',hour:'2-digit',minute:'2-digit',second:'2-digit',timeZoneName:'short'}).format(new Date(created)))).toHaveCount(2);
 await expect(detail).not.toContainText(tenant);await expect(detail).not.toContainText(fixtureMetadata.createdBy);
 await expect(detail.getByText(text.fields.visibility)).toHaveCount(0);await expect(detail.getByText(text.f4.metadata)).toHaveCount(0);
 await expect(detail.getByRole('button',{name:text.ui.reloadLatestState})).toHaveCount(0);
 await expect(detail.getByRole('button',{name:text.ui.editCategory})).toBeVisible();await expect(detail.getByRole('button',{name:text.ui.exitDetail})).toBeVisible();
 await detail.getByRole('button',{name:text.ui.editCategory}).click();const input=detail.getByLabel(text.ui.name);
 await expect(input).toHaveValue(saved);await expect(detail.getByRole('button',{name:text.ui.exitDetail})).toHaveCount(0);
 await detail.getByRole('button',{name:text.redesign.cancel}).click();await expect(input).toHaveCount(0);
 await detail.getByRole('button',{name:text.ui.editCategory}).click();await input.fill('Unsent draft');await detail.getByRole('button',{name:text.redesign.cancel}).click();
 const confirm=page.getByRole('alertdialog');await expect(confirm).toContainText(text.ui.discardCategoryEdit);
 await confirm.getByRole('button',{name:text.ui.continueEditing}).click();await expect(input).toHaveValue('Unsent draft');
 await detail.getByRole('button',{name:text.redesign.cancel}).click();await confirm.getByRole('button',{name:text.ui.discardChanges}).click();
 await detail.getByRole('button',{name:text.ui.editCategory}).click();await expect(input).toHaveValue(saved);
 fail=true;await input.fill('Failed name');await detail.getByRole('button',{name:text.ui.save}).click();await expect(input).toHaveValue('Failed name');await expect(detail.getByRole('alert')).toBeVisible();
 expect(writes).toEqual([{name:'Failed name'}]);fail=false;await detail.getByRole('button',{name:text.ui.save}).click();
 await expect(detail.getByRole('button',{name:text.ui.editCategory})).toBeVisible();await expect(detail.getByText('Failed name',{exact:true})).toBeVisible();expect(writes).toEqual([{name:'Failed name'},{name:'Failed name'}]);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await detail.getByRole('button',{name:text.ui.exitDetail}).click();await expect(page).toHaveURL(/\/categories$/);
});
test('Category actor fallback never exposes unresolved or system UUIDs',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page);
 await page.route(`**/api/v1/catalog/categories/${localId}`,route=>route.fulfill({json:{...row(localId,'Fallback',true),metadata:{...fixtureMetadata,createdByDisplayName:null,updatedByDisplayName:'KELIOS system/seed'},deleted:false,deletedAt:null,deletedBy:null}}));
 await page.goto(`/categories/${localId}`);const detail=page.getByRole('article');
 await expect(detail.getByText(en.ui.unresolvedActor)).toBeVisible();await expect(detail.getByText('KELIOS system/seed')).toBeVisible();await expect(detail).not.toContainText(fixtureMetadata.createdBy);
});
test('Category edit holds publication until Save and exposes explicit guarded Delete',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page);
 const writes:{method:string;body:any}[]=[];
 await page.route(`**/api/v1/catalog/categories/${localId}`,route=>{const method=route.request().method();if(method==='PUT'||method==='DELETE'){writes.push({method,body:route.request().postDataJSON()});return route.fulfill({status:204,body:''});}return route.fallback();});
 await page.goto(`/categories/${localId}`);const detail=page.getByRole('article');
 await expect(detail.getByRole('button',{name:en.ui.editCategory})).toBeVisible();await expect(detail.getByRole('button',{name:en.ui.exitDetail})).toBeVisible();
 const deletion=detail.getByRole('button',{name:en.ui.deleteEntry});await expect(deletion).toBeVisible();await expect(deletion).toHaveClass(/bg-destructive/);
 await detail.getByRole('button',{name:en.ui.editCategory}).click();const form=detail.getByRole('form',{name:en.ui.editCategory});await form.getByRole('checkbox',{name:en.ui.publishGlobally}).check();expect(writes).toHaveLength(0);
 await form.getByRole('button',{name:en.redesign.cancel}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.ui.discardChanges}).click();expect(writes).toHaveLength(0);
 await detail.getByRole('button',{name:en.ui.editCategory}).click();await form.getByRole('checkbox',{name:en.ui.publishGlobally}).check();await form.getByRole('button',{name:en.ui.save}).click();await expect.poll(()=>writes.length).toBe(1);expect(writes[0]).toMatchObject({method:'PUT',body:{name:'Local category',publishGlobally:true}});
 await deletion.click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();expect(writes).toHaveLength(1);
});
test('Category toolbar actions remain authorized and isolated',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page);await page.goto(`/tenants/${tenant}/categories`);
 const toolbar=page.getByRole('toolbar',{name:en.fields.categories});await toolbar.getByRole('button',{name:en.actions.create}).click();await expect(page.getByRole('dialog')).toHaveCount(1);await expect(page.getByRole('dialog')).toHaveAccessibleName('Create Categories');await page.getByRole('dialog').getByRole('button',{name:en.redesign.cancel}).click();await toolbar.getByRole('button',{name:en.actions.merge}).click();await expect(page.getByRole('dialog')).toHaveAccessibleName(en.ui.mergeTitle.replace('{{resource}}',en.ui.category));await page.getByRole('dialog').getByRole('button',{name:en.redesign.close}).click();
 await toolbar.getByRole('textbox',{name:en.ui.categorySearch}).fill('Local');await toolbar.getByRole('button',{name:en.actions.filter,exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);
});
test('Category reader has search and detail but no create or merge',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page,'USER');await page.goto(`/tenants/${tenant}/categories`);
 const toolbar=page.getByRole('toolbar',{name:en.fields.categories});await expect(toolbar.getByRole('button',{name:en.actions.create})).toHaveCount(0);await expect(toolbar.getByRole('button',{name:en.actions.merge})).toHaveCount(0);await page.locator('tbody').getByRole('link',{name:'Local category'}).click();await expect(page.getByRole('article').getByRole('button',{name:en.ui.editCategory})).toHaveCount(0);await expect(page.getByRole('article').getByRole('button',{name:en.ui.categoryActions})).toHaveCount(0);
});
test('Category Mandant sort stays server-owned across page navigation',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));await setup(page);
 const requests:URL[]=[];
 await page.route(`**/api/v1/tenants/${tenant}/catalog/categories?**`,route=>{
  const url=new URL(route.request().url());requests.push(url);
  const pageNumber=Number(url.searchParams.get('page')??0);
  const items=Array.from({length:pageNumber===0?20:1},(_,index)=>row(`00000000-0000-0000-0000-${String(pageNumber*20+index+1).padStart(12,'0')}`,`Category ${pageNumber*20+index+1}`,false));
  return route.fulfill({json:{items,totalElements:21,page:pageNumber,size:20}});
 });
 await page.goto(`/tenants/${tenant}/categories`);
 await page.getByRole('columnheader',{name:en.fields.tenantName}).getByRole('button').click();
 await expect.poll(()=>requests.at(-1)?.searchParams.get('sort')).toBe('tenantName');
 await page.getByRole('button',{name:en.ui.nextPage}).click();
 await expect.poll(()=>requests.at(-1)?.searchParams.get('page')).toBe('1');
 expect(requests.at(-1)?.searchParams.get('sort')).toBe('tenantName');
 expect(requests.at(-1)?.searchParams.get('direction')).toBe('asc');
});
