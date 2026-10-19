import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',other='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const id='cccccccc-cccc-cccc-cccc-cccccccccccc',actor='dddddddd-dddd-dddd-dddd-dddddddddddd';
const metadata={createdBy:actor,createdAt:'2026-10-12T12:00:00Z',updatedBy:actor,updatedAt:'2026-10-12T12:00:00Z',createdByDisplayName:'Ada Admin',updatedByDisplayName:'Ada Admin'};
type Kind='manufacturers'|'locations'|'owners';
async function fixture(page:Page,kind:Kind,scope:'GLOBAL'|'TENANT'='GLOBAL',role:'ADMIN'|'USER'='ADMIN'){
 const requests:{method:string;url:URL;body:unknown}[]=[];let failing=false;
 let record={id,name:'Readable entry',tenantId:tenant,tenant:{id:tenant,name:'School North'},visibility:'TENANT',note:'Saved note',deleted:false,deletedAt:null,deletedBy:null,metadata};
 await page.route('**/api/v1/**',route=>{
  const request=route.request(),url=new URL(request.url()),method=request.method(),body=request.postData()?request.postDataJSON():null;
  requests.push({method,url,body});
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:actor,scope,role,status:'ACTIVE',tenantId:scope==='TENANT'?tenant:null,displayName:'Fixture'}}});
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'School North',metadata},{id:other,name:'School South',metadata}]});
  if(url.pathname===`/api/v1/tenants/${tenant}`)return route.fulfill({json:{id:tenant,name:'School North',metadata}});
  if(method!=='GET'){
   if(failing)return route.fulfill({status:409,json:{code:'CONCURRENT_OPERATION'}});
   if(method==='PUT'){record={...record,...body};return route.fulfill({status:204,body:''});}
   if(method==='POST')return route.fulfill({status:201,json:{id}});
   return route.fulfill({status:204,body:''});
  }
  if(url.pathname.endsWith(`/${kind}/${id}`))return route.fulfill({json:record});
  if(url.pathname.endsWith(`/${kind}`)){
   const global={...record,id:other,name:'Global entry',visibility:'GLOBAL',tenantId:null,tenant:null};
   const entries=kind==='manufacturers'?[global,record]:url.pathname===`/api/v1/${kind}`?[record,{...record,id:other,name:'Other Tenant entry',tenantId:other,tenant:{id:other,name:'School South'}}]:[record];
   const visibility=url.searchParams.get('visibility');const q=url.searchParams.get('q')?.toLowerCase();
   const items=entries.filter(item=>(!visibility||item.visibility===visibility)&&(!q||item.name.toLowerCase().includes(q)));
   return route.fulfill({json:{items,totalElements:items.length,page:Number(url.searchParams.get('page')??0),size:Number(url.searchParams.get('size')??20)}});
  }
  return route.fulfill({json:{items:[],totalElements:0,page:0,size:20}});
 });
 return {requests,setFail(value:boolean){failing=value;}};
}

for(const language of ['en','de'] as const)for(const width of [1440,390])test(`${language}/${width} required Tenant Create stays blocked without Tenant options`,async({page})=>{
 const t=language==='de'?de:en;await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);const f=await fixture(page,'locations');await page.route('**/api/v1/tenants',route=>route.fulfill({json:[]}));await page.setViewportSize({width,height:900});await page.goto('/locations');
 await page.getByRole('button',{name:t.actions.create,exact:true}).click();const dialog=page.getByRole('dialog'),owner=dialog.getByLabel(t.fields.tenantName),save=dialog.getByRole('button',{name:t.actions.create,exact:true});
 await expect(owner).toHaveValue('');await expect(owner.locator('option')).toHaveCount(1);await expect(save).toBeDisabled();await expect(dialog.getByText(/Keine Mandanten verfügbar|No Tenants available/i)).toHaveCount(0);
 await dialog.getByLabel(t.ui.name).fill('Cannot submit');await expect(save).toBeDisabled();expect(f.requests.filter(request=>request.method==='POST'&&request.url.pathname.endsWith('/locations'))).toHaveLength(0);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
for(const kind of ['locations','owners'] as const)for(const width of [1440,390])test(`${kind} Global collection and concrete Create at ${width}px`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 const f=await fixture(page,kind);await page.setViewportSize({width,height:900});await page.goto(`/${kind}`);
 await expect(page.locator('tbody tr')).toHaveCount(2);
 await expect(page.locator('tbody')).toContainText('School North');await expect(page.locator('tbody')).toContainText('School South');
 await expect(page.getByLabel(en.ui.catalogRows,{exact:true})).toHaveCount(0);
 await page.getByRole('button',{name:en.actions.create,exact:true}).click();
 const dialog=page.getByRole('dialog'),owner=dialog.getByLabel(en.fields.tenantName),save=dialog.getByRole('button',{name:en.actions.create,exact:true});
 await expect(owner).toHaveValue('');await expect(save).toBeDisabled();await owner.selectOption(other);
 await dialog.getByLabel(en.ui.name,{exact:true}).fill('New entry');await save.click();
 await expect.poll(()=>f.requests.findLast(item=>item.method==='POST')?.url.pathname).toBe(`/api/v1/tenants/${other}/${kind}`);
 await expect(page).toHaveURL(new RegExp(`/${kind}$`));
 await page.goto(`/tenants/${tenant}/${kind}`);await expect(page.locator('tbody tr')).toHaveCount(1);
 await page.getByRole('button',{name:en.actions.create,exact:true}).click();
 const fixed=page.getByRole('dialog').getByLabel(en.fields.tenantName);await expect(fixed).toHaveAttribute('readonly','');await expect(fixed).toHaveValue('School North');
 await page.getByRole('dialog').getByLabel(en.ui.name,{exact:true}).fill('Concrete entry');await page.getByRole('dialog').getByRole('button',{name:en.actions.create,exact:true}).click();
 await expect.poll(()=>f.requests.findLast(item=>item.method==='POST')?.url.pathname).toBe(`/api/v1/tenants/${tenant}/${kind}`);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
for(const kind of ['locations','owners'] as const)test(`${kind} context switch keeps aggregate and concrete lists distinct`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 const f=await fixture(page,kind);await page.goto(`/${kind}`);await expect(page.locator('tbody tr')).toHaveCount(2);
 await page.getByRole('button',{name:en.ui.tenantContext,exact:true}).click();await page.getByRole('menuitem',{name:'School North',exact:true}).click();
 await expect(page).toHaveURL(`/tenants/${tenant}/${kind}`);await expect(page.locator('tbody tr')).toHaveCount(1);
 await page.getByRole('button',{name:en.ui.tenantContext,exact:true}).click();await page.getByRole('menuitem',{name:en.ui.globalContext,exact:true}).click();
 await expect(page).toHaveURL(`/${kind}`);await expect(page.locator('tbody tr')).toHaveCount(2);
 expect(f.requests.filter(item=>item.method==='GET'&&item.url.pathname.endsWith(`/${kind}`)).map(item=>item.url.pathname)).toContain(`/api/v1/tenants/${tenant}/${kind}`);
});

for(const kind of ['manufacturers','locations','owners'] as const)for(const width of [1440,390])test(`${kind} collection uses supported actions and readable audit at ${width}px`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','de'));
 const f=await fixture(page,kind);await page.setViewportSize({width,height:900});await page.goto(`/tenants/${tenant}/${kind}`);
 const toolbar=page.getByRole('toolbar',{name:de.fields[kind]});
 await expect(toolbar).toBeVisible();
 await expect(page.getByText(de.redesign.resourceHelp)).toHaveCount(0);
 const controls=await toolbar.locator('button,input,select').evaluateAll(nodes=>nodes.map(node=>node.tagName==='INPUT'?'search':node.tagName==='SELECT'?'visibility':node.getAttribute('aria-label')??node.textContent?.trim()));
 expect(controls).toEqual(kind==='manufacturers'?[de.actions.create,de.actions.merge,'search',de.actions.filter,'visibility',de.actions.resetFilters]:[de.actions.create,'search',de.actions.filter,de.actions.resetFilters]);
 const create=toolbar.getByRole('button',{name:de.actions.create,exact:true});await expect(create).toHaveClass(/bg-primary/);
 const search=toolbar.getByRole('textbox');await expect(search).toHaveAttribute('placeholder',de.ui[kind==='manufacturers'?'manufacturerSearchPlaceholder':kind==='locations'?'locationSearchPlaceholder':'ownerSearchPlaceholder']);
 const heights=await Promise.all([create,search,toolbar.getByRole('button',{name:de.actions.filter,exact:true})].map(async item=>(await item.boundingBox())?.height));expect(heights).toEqual([40,40,40]);
 const headers=page.locator('main table thead th');await expect(headers).toHaveCount(4);
 await expect(headers).toHaveText([de.ui.name,de.fields.tenantName,de.fields.createdAt,de.fields.createdByName]);
 for(const header of await headers.all()){await expect(header).toHaveCSS('font-weight','700');await expect(header).toHaveCSS('text-align','center');}
 await expect(page.locator('main table tbody td').first()).toHaveCSS('text-align','left');
 await expect(headers.nth(1)).toHaveCSS('border-left-width','1px');
 await expect(page.locator('tbody')).toContainText('Ada Admin');await expect(page.locator('tbody')).not.toContainText(actor);
 await expect(page.locator('tbody')).toContainText('School North');await expect(page.locator('tbody')).not.toContainText(tenant);
 await expect(toolbar.getByRole('button',{name:de.actions.moreFilters})).toHaveCount(0);
 const pager=[page.getByLabel(de.ui.pageSize),page.getByRole('button',{name:de.ui.previousPage}),page.getByRole('button',{name:de.ui.nextPage})];
 expect(await Promise.all(pager.map(async item=>(await item.boundingBox())?.height))).toEqual([40,40,40]);
 await search.fill('Readable');await toolbar.getByRole('button',{name:de.actions.filter,exact:true}).click();
 await expect.poll(()=>f.requests.filter(item=>item.url.pathname.endsWith(`/${kind}`)).at(-1)?.url.searchParams.get('q')).toBe('Readable');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('Manufacturer visibility and Create ownership retain catalog-only semantics',async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 const f=await fixture(page,'manufacturers');await page.goto(`/tenants/${tenant}/manufacturers`);
 const toolbar=page.getByRole('toolbar',{name:en.fields.manufacturers}),filter=toolbar.getByLabel(en.ui.catalogRows,{exact:true});
 await expect(filter.locator('option')).toHaveText([en.ui.categoryRowsAll,en.ui.categoryRowsGlobal,en.ui.categoryRowsTenant]);
 await filter.selectOption('GLOBAL');await expect.poll(()=>f.requests.filter(item=>item.url.pathname.endsWith('/manufacturers')).at(-1)?.url.searchParams.get('visibility')).toBe('GLOBAL');
 await filter.selectOption('TENANT');await expect(page.locator('tbody')).toContainText('Readable entry');await expect(page.locator('tbody')).not.toContainText('Global entry');
 await toolbar.getByRole('button',{name:en.actions.create,exact:true}).click();
 const dialog=page.getByRole('dialog'),owner=dialog.getByLabel(en.fields.tenantName);
 await expect(owner).toHaveValue('');await owner.selectOption(other);await dialog.getByLabel(en.ui.name,{exact:true}).fill('New manufacturer');
 await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();
 await expect.poll(()=>f.requests.findLast(item=>item.method==='POST')?.url.pathname).toBe(`/api/v1/tenants/${other}/catalog/manufacturers`);
 await expect(page).toHaveURL(new RegExp(`/tenants/${tenant}/manufacturers$`));
 await expect(page.getByRole('status').filter({hasText:'Manufacturer created for School South.'})).toBeVisible();
 await page.goto('/manufacturers');await expect(page.getByLabel(en.ui.catalogRows,{exact:true})).toHaveValue('');
 await expect(page.locator('tbody')).toContainText('Global entry');await expect(page.locator('tbody')).toContainText('Readable entry');
});

for(const kind of ['manufacturers','locations','owners'] as const)test(`${kind} Create keeps Tenant authority and errors contextual`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 const f=await fixture(page,kind,'TENANT');await page.goto(`/tenants/${tenant}/${kind}`);
 await page.getByRole('button',{name:en.actions.create,exact:true}).click();
 const dialog=page.getByRole('dialog');const owner=dialog.getByLabel(en.fields.tenantName);
 await expect(owner).toHaveValue('School North');await expect(owner).toHaveAttribute('readonly','');
 await dialog.getByLabel(en.ui.name,{exact:true}).fill('New entry');
 if(kind!=='manufacturers')await dialog.getByLabel(en.fields.note).fill('Operator note');
 f.setFail(true);await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();
 await expect(dialog.getByRole('alert')).toBeVisible();await expect(dialog.getByLabel(en.ui.name,{exact:true})).toHaveValue('New entry');
 f.setFail(false);await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();await expect(dialog).toHaveCount(0);
 const writes=f.requests.filter(item=>item.method==='POST'&&item.url.pathname.endsWith(`/${kind}`));expect(writes).toHaveLength(2);
 expect(writes[1].url.pathname).toBe(kind==='manufacturers'?`/api/v1/tenants/${tenant}/catalog/manufacturers`:`/api/v1/tenants/${tenant}/${kind}`);
 expect(writes[1].body).toEqual(kind==='manufacturers'?{name:'New entry'}:{name:'New entry',note:'Operator note'});
 await expect(page.getByRole('status').filter({hasText:'School North'})).toBeVisible();
});

for(const kind of ['manufacturers','locations','owners'] as const)test(`${kind} reader gets search and detail without write actions`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 await fixture(page,kind,'TENANT','USER');await page.goto(`/tenants/${tenant}/${kind}`);
 const toolbar=page.getByRole('toolbar',{name:en.fields[kind]});await expect(toolbar.getByRole('button',{name:en.actions.create})).toHaveCount(0);
 await expect(toolbar.getByRole('button',{name:en.actions.merge})).toHaveCount(0);
 await expect(toolbar.getByRole('textbox')).toBeVisible();
 await page.locator('tbody').getByRole('link',{name:'Readable entry'}).click();
 await expect(page.getByRole('button',{name:en.ui.editCategory})).toHaveCount(0);
});

for(const kind of ['manufacturers','locations','owners'] as const)test(`${kind} detail edits only supported fields and discards changed drafts`,async({page})=>{
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 const f=await fixture(page,kind);await page.goto(`/${kind}/${id}`);
 const article=page.getByRole('article');await expect(article).toContainText('School North');await expect(article).toContainText('Ada Admin');
 await expect(article).not.toContainText(actor);await expect(article).not.toContainText(tenant);
 await expect(article.getByRole('button',{name:en.ui.reloadLatestState})).toHaveCount(0);
 await expect(article.getByRole('button',{name:en.ui.editCategory})).toBeVisible();await expect(article.getByRole('button',{name:en.ui.exitDetail})).toBeVisible();
 await article.getByRole('button',{name:en.ui.editCategory}).click();const form=article.getByRole('form',{name:en.ui.editCategory});
 await expect(form.getByLabel(en.fields.tenantName)).toHaveCount(0);
 await expect(form.getByLabel(en.fields.note)).toHaveCount(kind==='manufacturers'?0:1);
 await form.getByRole('button',{name:en.redesign.cancel}).click();await expect(form).toHaveCount(0);
 await article.getByRole('button',{name:en.ui.editCategory}).click();await form.getByLabel(en.ui.name,{exact:true}).fill('Draft');
 await form.getByRole('button',{name:en.redesign.cancel}).click();
 await page.getByRole('alertdialog').getByRole('button',{name:en.ui.continueEditing}).click();await expect(form.getByLabel(en.ui.name,{exact:true})).toHaveValue('Draft');
 await form.getByRole('button',{name:en.redesign.cancel}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.ui.discardChanges}).click();
 await expect(article).toContainText('Readable entry');await article.getByRole('button',{name:en.ui.editCategory}).click();await expect(form.getByLabel(en.ui.name,{exact:true})).toHaveValue('Readable entry');
 f.setFail(true);await form.getByLabel(en.ui.name,{exact:true}).fill('Retry');await form.getByRole('button',{name:en.ui.save}).click();
 await expect(form.getByRole('alert')).toBeVisible();await expect(form.getByLabel(en.ui.name,{exact:true})).toHaveValue('Retry');
 f.setFail(false);await form.getByRole('button',{name:en.ui.save}).click();await expect(form).toHaveCount(0);
 await expect(article).toContainText('Retry');
 expect(f.requests.filter(item=>item.method==='PUT').map(item=>item.body)).toEqual(kind==='manufacturers'?[{name:'Retry'},{name:'Retry'}]:[{name:'Retry',note:'Saved note'},{name:'Retry',note:'Saved note'}]);
});
