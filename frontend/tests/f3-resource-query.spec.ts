import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',ref='cccccccc-cccc-cccc-cccc-cccccccccccc';
async function setup(page:Page,scope='GLOBAL',locale='en'){
 const requests:URL[]=[];
 await page.addInitScript(language=>localStorage.setItem('kelios.ui.language',language),locale);
 await page.route('**/api/v1/**',async r=>{const u=new URL(r.request().url());requests.push(u);const p=u.pathname;
 if(p.endsWith('/csrf'))return r.fulfill({json:{token:'fixture'}});
 if(p.endsWith('/session/refresh'))return r.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope,role:'ADMIN',status:'ACTIVE',tenantId:scope==='TENANT'?tenant:null,displayName:'Fixture'}}});
 if(p==='/api/v1/tenants'&&scope==='TENANT')return r.fulfill({status:403,json:{status:403}});
 if(p==='/api/v1/tenants')return r.fulfill({json:[{id:tenant,name:'Workspace'}]});
 if(p===`/api/v1/tenants/${tenant}`)return r.fulfill({json:{id:tenant,name:'Workspace',metadata:{}}});
 let items:any[]=[{id:ref,name:'Z server first',displayName:'Z server first',visibility:'GLOBAL',tenantId:null,scope:'TENANT',role:'USER',status:'PENDING',assetTag:'Z server first'},{id,name:'A server second',displayName:'A server second',visibility:'TENANT',tenantId:tenant,scope:'GLOBAL',role:'USER',status:'ACTIVE',assetTag:'A server second'}];
 const metadata={createdBy:id,createdAt:'2026-10-12T00:00:00Z',updatedBy:id,updatedAt:'2026-10-12T00:00:00Z',createdByDisplayName:null,updatedByDisplayName:null};
 if(p==='/api/v1/users')items=items.map(row=>({id:row.id,displayName:row.displayName,scope:u.searchParams.get('scope')||'TENANT',role:u.searchParams.get('role')||'USER',status:u.searchParams.get('status')||row.status,tenantId:(u.searchParams.get('scope')||'TENANT')==='GLOBAL'?null:tenant,tenant:(u.searchParams.get('scope')||'TENANT')==='GLOBAL'?null:{id:tenant,name:'Workspace'},metadata}));
 else if(p.endsWith('/assets'))items=items.map(row=>({id:row.id,tenantId:tenant,assetTag:row.assetTag,categoryId:ref,manufacturerId:ref,productId:null,locationId:ref,ownerId:null,tenant:{id:tenant,name:'Workspace'},category:{id:ref,name:'Category'},manufacturer:{id:ref,name:'Manufacturer'},product:null,location:{id:ref,name:'Location'},owner:null,lifecycle:'REGISTERED',condition:'UNKNOWN',loanable:false,serialNumber:null,externalInventoryNumber:null,localIdentifier:null,version:0,loanedTo:null,loanedAt:null,expectedReturnAt:null,...metadata}));
 else if(p.includes('/catalog/'))items=items.filter(row=>!u.searchParams.has('visibility')||row.visibility===u.searchParams.get('visibility')).map(row=>({id:row.id,name:row.name,visibility:row.visibility,tenantId:row.tenantId,tenant:row.tenantId?{id:tenant,name:'Workspace'}:null,metadata,...(p.endsWith('/products')?{manufacturerNumber:null,categoryId:ref,category:{id:ref,name:'Category'},manufacturerId:ref,manufacturer:{id:ref,name:'Manufacturer'}}:{})}));
 else items=items.map(row=>({id:row.id,name:row.name,tenantId:tenant,tenant:{id:tenant,name:'Workspace'},metadata}));
 if(u.searchParams.get('size')==='1')items=items.slice(0,1);
 return r.fulfill({json:{items,page:Number(u.searchParams.get('page')),size:Number(u.searchParams.get('size')||20),totalElements:91}});
 });return requests;
}
for(const kind of ['categories','manufacturers','products','locations','owners','users'])test(`${kind}: server sort toggle, applied/pending/page/size and node identity`,async({page})=>{
 const req=await setup(page);const route=['locations','owners'].includes(kind)?`/tenants/${tenant}/${kind}`:kind==='users'?'/users':`/catalog/${kind}`;
 await page.goto(route);await expect(page.locator('tbody tr')).toHaveCount(2);
 const input=page.getByLabel(kind==='users'?en.ui.name:kind==='products'?en.ui.productSearch:kind==='categories'?en.ui.categorySearch:kind==='manufacturers'?en.ui.manufacturerSearch:kind==='locations'?en.ui.locationSearch:kind==='owners'?en.ui.ownerSearch:en.ui.name,{exact:true});const handle=await input.elementHandle();
 await input.fill('HP');await input.press('Enter');const endpoint=req.filter(u=>u.searchParams.get('q')==='HP').at(-1)!.pathname;
 const list=()=>req.filter(u=>u.pathname===endpoint);await expect.poll(()=>list().at(-1)?.searchParams.get('q')).toBe('HP');
 await page.getByRole('button',{name:en.ui.nextPage,exact:true}).click();await expect.poll(()=>list().at(-1)?.searchParams.get('page')).toBe('1');
 const count=list().length;await input.fill('Dell');await expect.poll(()=>list().length).toBe(count);
 const sort=page.locator('thead button').first();await sort.click();await expect.poll(()=>list().at(-1)?.searchParams.get('sort')).toBe(kind==='users'?'displayName':'name');expect(Object.fromEntries(list().at(-1)!.searchParams)).toMatchObject({q:'HP',direction:'asc',page:'0'});
 await expect(page.locator('tbody tr').first()).toContainText('Z server first');await expect(sort.locator('..')).toHaveAttribute('aria-sort','ascending');await sort.click();await expect.poll(()=>list().at(-1)?.searchParams.get('direction')).toBe('desc');
 await page.getByLabel(en.ui.pageSize).selectOption('40');await expect.poll(()=>list().at(-1)?.searchParams.get('size')).toBe('40');expect(list().at(-1)?.searchParams.get('q')).toBe('HP');expect(await handle!.evaluate(n=>n===document.activeElement||n.isConnected)).toBe(true);
 await input.fill('');await input.press('Enter');await expect.poll(()=>list().at(-1)?.searchParams.has('q')).toBe(false);expect(list().at(-1)?.searchParams.get('page')).toBe('0');expect(list().at(-1)?.searchParams.get('direction')).toBe('desc');
});
for(const [lang,width] of [['en',1440],['de',390]] as const)test(`${lang}/${width} shared business empty state is concise`,async({page})=>{
 const t=lang==='de'?de:en;await setup(page,'GLOBAL',lang);await page.setViewportSize({width,height:900});
 await page.route('**/api/v1/**',route=>{const path=new URL(route.request().url()).pathname;return ['/api/v1/catalog/categories','/api/v1/users',`/api/v1/tenants/${tenant}/assets`].includes(path)?route.fulfill({json:{items:[],page:0,size:20,totalElements:0}}):route.fallback();});
 for(const route of ['/categories','/users',`/tenants/${tenant}/assets`]){await page.goto(route);await expect(page.locator('.empty-state')).toHaveText(t.ui.noRecords);await expect(page.locator('.empty-state p')).toHaveCount(1);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);}
});
test('Product Category selection immediately combines applied search, ownership and sort',async({page})=>{
 const req=await setup(page);await page.goto('/catalog/products');await expect(page.locator('tbody tr')).toHaveCount(2);
 const reads=()=>req.filter(u=>u.pathname==='/api/v1/catalog/products');
 await page.getByLabel(en.ui.catalogRows).selectOption('TENANT');
 const search=page.getByRole('textbox',{name:en.ui.productSearch});await search.fill('HP');await search.press('Enter');
 await page.getByRole('columnheader',{name:en.ui.name}).getByRole('button').click();
 await page.getByRole('button',{name:en.ui.nextPage}).click();
 await search.fill('unsubmitted');
 const category=page.getByRole('toolbar').getByRole('combobox',{name:en.fields.categories});
 const before=reads().length;await category.fill('Z');await expect.poll(()=>reads().length).toBe(before);
 await page.getByRole('listbox').getByRole('option').first().click();
 await expect.poll(()=>reads().at(-1)?.searchParams.get('categoryId')).toBe(ref);
 expect(Object.fromEntries(reads().at(-1)!.searchParams)).toMatchObject({q:'HP',visibility:'TENANT',categoryId:ref,sort:'name',direction:'asc',page:'0'});
 expect(reads().at(-1)?.searchParams.has('manufacturerId')).toBe(false);expect(reads().at(-1)?.searchParams.has('manufacturerNumber')).toBe(false);
 await page.getByRole('toolbar').getByRole('button',{name:en.actions.clearField.replace('{{field}}',en.fields.categories)}).click();
 await expect.poll(()=>reads().at(-1)?.searchParams.has('categoryId')).toBe(false);
 expect(reads().at(-1)?.searchParams.get('q')).toBe('HP');await expect(search).toHaveValue('unsubmitted');
 expect(await page.locator('thead button').count()).toBe(2);
});
for(const language of ['en','de'] as const)for(const width of [1440,390])test(`${language}/${width} field X clears only applied Product search and ownership`,async({page})=>{
 const t=language==='en'?en:de,requests=await setup(page,'GLOBAL',language);await page.setViewportSize({width,height:900});await page.goto('/catalog/products');
 const reads=()=>requests.filter(url=>url.pathname==='/api/v1/catalog/products');
 const search=page.getByRole('textbox',{name:t.ui.productSearch});await search.fill('HP');await search.press('Enter');
 await page.getByRole('toolbar').getByLabel(t.ui.catalogRows).selectOption('TENANT');
 await page.getByRole('button',{name:t.ui.nextPage}).click();await search.fill('draft');
 const clearSearch=page.getByRole('button',{name:t.actions.clearField.replace('{{field}}',t.ui.productSearch)});
 const inputBox=await search.boundingBox(),clearBox=await clearSearch.boundingBox();expect(inputBox&&clearBox).toBeTruthy();expect(clearBox!.x).toBeGreaterThanOrEqual(inputBox!.x);expect(clearBox!.x+clearBox!.width).toBeLessThanOrEqual(inputBox!.x+inputBox!.width);
 await clearSearch.focus();await expect(clearSearch).toBeFocused();await clearSearch.press('Enter');
 await expect(search).toBeFocused();await expect(search).toHaveValue('');
 await expect.poll(()=>reads().at(-1)?.searchParams.has('q')).toBe(false);
 expect(Object.fromEntries(reads().at(-1)!.searchParams)).toMatchObject({visibility:'TENANT',page:'0'});
 await page.getByRole('button',{name:t.actions.clearField.replace('{{field}}',t.ui.catalogRows)}).click();
 await expect.poll(()=>reads().at(-1)?.searchParams.has('visibility')).toBe(false);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
test('Asset filter X clears one applied criterion and keeps another',async({page})=>{
 const requests=await setup(page);await page.goto(`/tenants/${tenant}/assets`);await page.getByRole('button',{name:en.actions.moreFilters}).click();
 const dialog=page.getByRole('dialog');await dialog.getByLabel(en.fields.lifecycle).selectOption('REGISTERED');await dialog.getByLabel(en.fields.condition).selectOption('UNKNOWN');await dialog.getByRole('button',{name:en.redesign.confirm,exact:true}).click();
 const reads=()=>requests.filter(url=>url.pathname.endsWith('/assets'));await expect.poll(()=>reads().at(-1)?.searchParams.get('lifecycle')).toBe('REGISTERED');
 await page.getByRole('button',{name:en.actions.moreFilters}).click();const before=reads().length;await dialog.getByRole('button',{name:en.actions.clearField.replace('{{field}}',en.fields.lifecycle)}).click();
 expect(reads()).toHaveLength(before);await dialog.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>reads().at(-1)?.searchParams.has('lifecycle')).toBe(false);expect(reads().at(-1)?.searchParams.get('condition')).toBe('UNKNOWN');
 await page.getByRole('button',{name:en.actions.moreFilters}).click();
 await dialog.getByRole('button',{name:en.actions.clearField.replace('{{field}}',en.fields.condition)}).click();
 expect(reads().at(-1)?.searchParams.get('condition')).toBe('UNKNOWN');await dialog.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>reads().at(-1)?.searchParams.has('condition')).toBe(false);
});
test('User filter X clears applied status without changing scope or Tenant',async({page})=>{
 const requests=await setup(page);await page.goto('/users');await page.getByRole('button',{name:en.actions.moreFilters}).click();const dialog=page.getByRole('dialog');
 await dialog.getByLabel(en.fields.status).selectOption('DISABLED');await dialog.getByLabel(en.fields.scope).selectOption('TENANT');await dialog.getByLabel(en.ui.tenantContext).selectOption(tenant);await dialog.getByRole('button',{name:en.redesign.confirm,exact:true}).click();
 const reads=()=>requests.filter(url=>url.pathname==='/api/v1/users');await expect.poll(()=>reads().at(-1)?.searchParams.get('status')).toBe('DISABLED');
 await page.getByRole('button',{name:en.actions.moreFilters}).click();const before=reads().length;await dialog.getByRole('button',{name:en.actions.clearField.replace('{{field}}',en.fields.status)}).click();
 expect(reads()).toHaveLength(before);await dialog.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>reads().at(-1)?.searchParams.has('status')).toBe(false);expect(Object.fromEntries(reads().at(-1)!.searchParams)).toMatchObject({scope:'TENANT',tenantId:tenant});
});
for(const [kind,lang,width,dismiss] of [
 ['assets','en',1440,'cancel'],['assets','de',390,'escape'],['users','en',390,'close'],['users','de',1440,'outside'],
] as const)test(`${kind} ${lang}/${width} dirty advanced filters require discard on ${dismiss}`,async({page})=>{
 const t=lang==='de'?de:en,requests=await setup(page,'GLOBAL',lang),route=kind==='assets'?`/tenants/${tenant}/assets`:'/users';
 await page.setViewportSize({width,height:900});await page.goto(route);
 const reads=()=>requests.filter(url=>url.pathname===`/api/v1/${kind==='assets'?`tenants/${tenant}/assets`:'users'}`);
 const open=page.getByRole('button',{name:t.actions.moreFilters});await open.click();const dialog=page.getByRole('dialog');
 const field=dialog.getByLabel(kind==='assets'?t.fields.localIdentifier:t.fields.status);
 await expect(dialog.getByRole('button',{name:t.actions.resetFilters})).toHaveCount(0);
 await expect(dialog.getByRole('button',{name:t.actions.filter,exact:true})).toHaveCount(0);
 if(kind==='assets')await field.fill('draft');else await field.selectOption('DISABLED');
 const before=reads().length;const close=async()=>{if(dismiss==='cancel')await dialog.getByRole('button',{name:t.redesign.cancel,exact:true}).click();else if(dismiss==='escape')await page.keyboard.press('Escape');else if(dismiss==='close')await dialog.getByRole('button',{name:t.redesign.close,exact:true}).click();else await page.mouse.click(4,4);};
 await close();const warning=page.getByRole('alertdialog');await expect(warning).toBeVisible();await warning.getByRole('button',{name:t.redesign.cancel,exact:true}).click();await expect(dialog).toBeVisible();expect(reads()).toHaveLength(before);
 await dialog.getByRole('button',{name:t.redesign.cancel,exact:true}).click();await warning.getByRole('button',{name:t.redesign.confirm,exact:true}).click();await expect(dialog).toHaveCount(0);expect(reads()).toHaveLength(before);
 await expect(open).toBeFocused();await open.press('Enter');await expect(field).toHaveValue('');await dialog.getByRole('button',{name:t.redesign.cancel,exact:true}).click();await expect(page.getByRole('alertdialog')).toHaveCount(0);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
for(const kind of ['assets','users'] as const)test(`${kind} Confirm combines advanced draft with applied search and resets paging`,async({page})=>{
 const requests=await setup(page),route=kind==='assets'?`/tenants/${tenant}/assets`:'/users',endpoint=kind==='assets'?`/api/v1/tenants/${tenant}/assets`:'/api/v1/users';
 await page.goto(route);const reads=()=>requests.filter(url=>url.pathname===endpoint),search=page.getByRole('textbox',{name:kind==='assets'?en.ui.assetIdentifiers:en.ui.name});
 await search.fill('HP');await search.press('Enter');await expect.poll(()=>reads().at(-1)?.searchParams.get('q')).toBe('HP');
 await page.getByRole('button',{name:en.ui.nextPage}).click();await expect.poll(()=>reads().at(-1)?.searchParams.get('page')).toBe('1');
 await search.fill('unsubmitted');await page.getByRole('button',{name:en.actions.moreFilters}).click();const dialog=page.getByRole('dialog'),before=reads().length;
 if(kind==='assets')await dialog.getByLabel(en.fields.lifecycle).selectOption('REGISTERED');else await dialog.getByLabel(en.fields.status).selectOption('DISABLED');
 expect(reads()).toHaveLength(before);await dialog.getByRole('button',{name:en.redesign.confirm,exact:true}).click();
 await expect.poll(()=>reads().at(-1)?.searchParams.get(kind==='assets'?'lifecycle':'status')).toBe(kind==='assets'?'REGISTERED':'DISABLED');
 expect(Object.fromEntries(reads().at(-1)!.searchParams)).toMatchObject({q:'HP',page:'0'});await expect(search).toHaveValue('unsubmitted');await expect(page.getByRole('alertdialog')).toHaveCount(0);
});
test('clearing an unapplied Product text draft does not fetch',async({page})=>{
 const requests=await setup(page);await page.goto('/catalog/products');await expect(page.locator('tbody tr')).toHaveCount(2);
 const reads=()=>requests.filter(url=>url.pathname==='/api/v1/catalog/products');const count=reads().length;
 const input=page.getByRole('textbox',{name:en.ui.productSearch});await input.fill('draft');
 await page.getByRole('button',{name:en.actions.clearField.replace('{{field}}',en.ui.productSearch)}).click();
 await expect(input).toHaveValue('');expect(reads()).toHaveLength(count);
});
for(const kind of ['categories','manufacturers','locations','owners','users','assets'] as const)test(`${kind} collection text X clears its applied query`,async({page})=>{
 const requests=await setup(page);const path=['locations','owners','assets'].includes(kind)?`/tenants/${tenant}/${kind}`:`/${kind}`;await page.goto(path);
 const name=kind==='categories'?en.ui.categorySearch:kind==='manufacturers'?en.ui.manufacturerSearch:kind==='locations'?en.ui.locationSearch:kind==='owners'?en.ui.ownerSearch:kind==='users'?en.ui.name:en.ui.assetIdentifiers;
 const input=page.getByRole('textbox',{name,exact:true});await input.fill('HP');await input.press('Enter');
 const endpoint=requests.filter(url=>url.searchParams.get('q')==='HP').at(-1)!.pathname,reads=()=>requests.filter(url=>url.pathname===endpoint);
 await page.getByRole('button',{name:en.actions.clearField.replace('{{field}}',name)}).click();await expect(input).toHaveValue('');
 await expect.poll(()=>reads().at(-1)?.searchParams.has('q')).toBe(false);
});
for(const scope of ['GLOBAL','TENANT'])test(`${scope} User controls and Dashboard authoritative scoped PENDING count`,async({page})=>{
 const req=await setup(page,scope);await page.goto('/');await expect(page.getByText('91',{exact:true})).toHaveCount(5);const count=req.find(u=>u.pathname==='/api/v1/users')!;expect(Object.fromEntries(count.searchParams)).toMatchObject({status:'PENDING',size:'1',page:'0'});expect(count.searchParams.has('tenantId')).toBe(false);
 await page.goto('/users');await page.getByRole('button',{name:en.actions.moreFilters}).click();const modal=page.getByRole('dialog');await modal.getByLabel(en.fields.status).selectOption('DISABLED');if(scope==='GLOBAL'){await modal.getByLabel(en.fields.scope).selectOption('TENANT');await modal.getByLabel(en.fields.role).selectOption('USER');await modal.getByLabel(en.ui.tenantContext).selectOption(tenant);}else{await expect(modal.getByLabel(en.ui.tenantContext)).toHaveCount(0);await expect(modal.getByLabel(en.fields.scope)).toHaveCount(0);expect(req.some(u=>u.pathname==='/api/v1/tenants')).toBe(false);}await modal.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>req.filter(u=>u.pathname==='/api/v1/users').at(-1)?.searchParams.get('status')).toBe('DISABLED');const last=req.filter(u=>u.pathname==='/api/v1/users').at(-1)!;if(scope==='GLOBAL')expect(Object.fromEntries(last.searchParams)).toMatchObject({scope:'TENANT',role:'USER',tenantId:tenant});else for(const key of ['scope','role','tenantId'])expect(last.searchParams.has(key)).toBe(false);
});
test('Asset combined readable references and identifiers, explicit apply retains wire tag',async({page})=>{
 const req=await setup(page);await page.goto(`/tenants/${tenant}/assets`);await page.getByRole('button',{name:en.actions.moreFilters}).click();const modal=page.getByRole('dialog');await modal.getByLabel(en.ui.loanable,{exact:true}).selectOption('false');await modal.getByLabel(en.ui.loaned).selectOption('true');for(const key of ['localIdentifier','serialNumber','externalInventoryNumber'] as const)await modal.getByLabel(en.fields[key],{exact:true}).fill('%_');for(const key of ['category','manufacturer','product','location','owner'] as const){await modal.getByRole('combobox',{name:en.fields[key],exact:true}).fill('Z');await modal.getByRole('listbox').getByRole('option').first().click();}await modal.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>req.filter(u=>u.pathname.endsWith('/assets')).at(-1)?.searchParams.get('ownerId')).toBe(ref);expect(Object.fromEntries(req.filter(u=>u.pathname.endsWith('/assets')).at(-1)!.searchParams)).toMatchObject({loanable:'false',loaned:'true',categoryId:ref,manufacturerId:ref,productId:ref,locationId:ref,ownerId:ref,localIdentifier:'%_',serialNumber:'%_',externalInventoryNumber:'%_'});
 await page.locator('thead button').first().click();await expect.poll(()=>req.filter(u=>u.pathname.endsWith('/assets')).at(-1)?.searchParams.get('sort')).toBe('tag');
});
for(const locale of ['en','de'])for(const width of [1440,1024,768,390])test(`${locale}/${width} F3 filters responsive keyboard`,async({page})=>{await setup(page,'GLOBAL',locale);await page.setViewportSize({width,height:900});await page.goto('/catalog/products');const text=locale==='de'?de:en;await expect(page.getByRole('button',{name:text.actions.moreFilters})).toHaveCount(0);const category=page.getByRole('toolbar').getByRole('combobox',{name:text.fields.categories});await category.focus();await category.press('ArrowDown');await category.press('Enter');await expect(category).toHaveValue('Z server first');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 for(const kind of ['categories','manufacturers','products','locations','owners','assets','users']){await page.goto(['locations','owners','assets'].includes(kind)?`/tenants/${tenant}/${kind}`:`/${kind}`);await expect(page.locator('tbody tr')).toHaveCount(2);await page.getByRole('button',{name:text.actions.moreFilters}).count().then(async count=>{if(count){await page.getByRole('button',{name:text.actions.moreFilters}).click();await expect(page.getByRole('dialog')).toBeVisible();}});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);await page.screenshot({path:`../.cache/frontend-f3/${locale}-${width}-${kind}.png`});if(await page.getByRole('dialog').count())await page.getByRole('dialog').getByRole('button',{name:text.redesign.close,exact:true}).click();}
});
for(const [locale,width] of [['en',390],['de',1440]] as const)test(`${locale}/${width} collection search actions share icon and accessible label`,async({page})=>{
 const text=locale==='de'?de:en;await setup(page,'GLOBAL',locale);await page.setViewportSize({width,height:900});
 for(const resource of ['assets','categories','manufacturers','products','locations','owners','users','audit']){
  await page.goto(['locations','owners'].includes(resource)?`/tenants/${tenant}/${resource}`:`/${resource}`);
  if(resource==='audit')await page.getByRole('button',{name:text.actions.moreFilters,exact:true}).click();
  const submit=page.getByRole('button',{name:text.actions.filter,exact:true}).first();await expect(submit).toHaveAttribute('type','submit');await expect(submit).toHaveAttribute('title',text.actions.filter);await expect(submit.locator('svg')).toHaveCount(1);
 }
 for(const resource of ['assets','users']){await page.goto(`/${resource}`);await page.getByRole('button',{name:text.actions.moreFilters}).click();const submit=page.getByRole('dialog').getByRole('button',{name:text.redesign.confirm,exact:true});await expect(submit).toHaveAttribute('type','submit');await expect(page.getByRole('dialog').getByRole('button',{name:text.actions.filter,exact:true})).toHaveCount(0);}
});
for(const kind of ['categories','manufacturers'])test(`${kind}: visibility combined with applied name and supported server sorting`,async({page})=>{const req=await setup(page);await page.goto(`/tenants/${tenant}/${kind}`);const input=page.getByLabel(kind==='categories'?en.ui.categorySearch:en.ui.manufacturerSearch,{exact:true});await input.fill('HP');await input.press('Enter');await page.getByLabel(kind==='categories'?en.ui.categoryRows:en.ui.catalogRows).selectOption('TENANT');await page.getByRole('columnheader',{name:kind==='categories'?en.fields.tenantName:en.ui.name}).getByRole('button').click();await expect.poll(()=>req.filter(u=>u.pathname.endsWith(kind)).at(-1)?.searchParams.get('sort')).toBe(kind==='categories'?'tenantName':'name');expect(Object.fromEntries(req.filter(u=>u.pathname.endsWith(kind)).at(-1)!.searchParams)).toMatchObject({q:'HP',visibility:'TENANT',direction:'asc',page:'0'});});
test('GLOBAL selected business context Dashboard counts narrowed PENDING server population',async({page})=>{const req=await setup(page);await page.goto(`/tenants/${tenant}/dashboard`);await expect.poll(()=>req.find(u=>u.pathname==='/api/v1/users')?.searchParams.get('tenantId')).toBe(tenant);expect(Object.fromEntries(req.find(u=>u.pathname==='/api/v1/users')!.searchParams)).toEqual({page:'0',size:'1',status:'PENDING',tenantId:tenant});await expect(page.getByText('91',{exact:true})).toHaveCount(5);});
test('GLOBAL Assets use selected application Tenant context before Place lookup',async({page})=>{const req=await setup(page);await page.goto('/assets');await page.getByRole('button',{name:en.actions.moreFilters}).click();let modal=page.getByRole('dialog');await expect(modal.getByLabel(en.ui.tenantContext)).toHaveCount(0);await expect(modal.getByRole('combobox',{name:en.fields.location,exact:true})).toHaveCount(0);await modal.getByRole('button',{name:en.redesign.close,exact:true}).click();await page.getByRole('button',{name:en.ui.tenantContext,exact:true}).click();await page.getByRole('menuitem',{name:'Workspace',exact:true}).click();await expect(page).toHaveURL(`/tenants/${tenant}/assets`);await page.getByRole('button',{name:en.actions.moreFilters}).click();modal=page.getByRole('dialog');const location=modal.getByRole('combobox',{name:en.fields.location,exact:true});await location.fill('Z');await modal.getByRole('listbox').getByRole('option').first().click();await modal.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>req.filter(u=>u.pathname===`/api/v1/tenants/${tenant}/assets`).at(-1)?.searchParams.get('locationId')).toBe(ref);expect(req.some(u=>u.pathname==='/api/v1/locations')).toBe(false);expect(req.some(u=>u.pathname===`/api/v1/tenants/${tenant}/locations`)).toBe(true);expect(req.filter(u=>u.pathname==='/api/v1/assets').every(u=>!u.searchParams.has('tenantId'))).toBe(true);});
test('Catalog stale sort response cannot replace a newer page; pending input remains unapplied',async({page})=>{
 await setup(page);const held:any[]=[];
 const category=(name:string)=>({id,name,visibility:'GLOBAL',tenantId:null,tenant:null,metadata:{createdBy:id,createdAt:'2026-10-12T00:00:00Z',updatedBy:id,updatedAt:'2026-10-12T00:00:00Z',createdByDisplayName:'Fixture Admin',updatedByDisplayName:'Fixture Admin'}});
 await page.route('**/api/v1/catalog/categories?**',r=>{const u=new URL(r.request().url());if(!u.searchParams.has('sort'))return r.fulfill({json:{items:[category('Initial')],page:0,size:20,totalElements:91}});held.push({r,u});});
 await page.goto('/categories');await expect(page.getByText('Initial',{exact:true})).toBeVisible();const input=page.getByLabel(en.ui.categorySearch,{exact:true});await input.fill('unapplied');await page.locator('thead button').first().click();await expect.poll(()=>held.length).toBe(1);await page.getByRole('button',{name:en.ui.nextPage,exact:true}).click();await expect.poll(()=>held.length).toBe(2);
 for(const item of held)expect(item.u.searchParams.has('q')).toBe(false);
 await held[1].r.fulfill({json:{items:[category('New page')],page:1,size:20,totalElements:91}});await held[0].r.fulfill({json:{items:[category('Old sort')],page:0,size:20,totalElements:91}});
 await expect(page.getByText('New page',{exact:true})).toBeVisible();await expect(page.getByText('Old sort',{exact:true})).toHaveCount(0);await expect(input).toHaveValue('unapplied');
});
test('GLOBAL Assets preserve distinct Tenant UUIDs in the authoritative context selector',async({page})=>{const req=await setup(page),other='dddddddd-dddd-dddd-dddd-dddddddddddd';await page.route('**/api/v1/tenants',r=>r.fulfill({json:[{id:tenant,name:'Same Tenant'},{id:other,name:'Same Tenant'}]}));await page.goto('/assets');await page.getByRole('button',{name:en.ui.tenantContext,exact:true}).click();await page.getByRole('menuitem',{name:`Same Tenant · ${other}`,exact:true}).click();await expect(page).toHaveURL(`/tenants/${other}/assets`);await expect.poll(()=>req.some(u=>u.pathname===`/api/v1/tenants/${other}/assets`)).toBe(true);});
test('GLOBAL User administration retains its own UUID Tenant criterion',async({page})=>{const req=await setup(page),other='dddddddd-dddd-dddd-dddd-dddddddddddd';await page.route('**/api/v1/tenants',r=>r.fulfill({json:[{id:tenant,name:'Same Tenant'},{id:other,name:'Same Tenant'}]}));await page.goto('/users');await page.getByRole('button',{name:en.actions.moreFilters}).click();const modal=page.getByRole('dialog'),selector=modal.getByLabel(en.ui.tenantContext);await expect(selector.getByRole('option',{name:`Same Tenant · ${tenant}`,exact:true})).toHaveCount(1);await expect(selector.getByRole('option',{name:`Same Tenant · ${other}`,exact:true})).toHaveCount(1);await selector.selectOption(other);await modal.getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect.poll(()=>req.filter(u=>u.pathname==='/api/v1/users').at(-1)?.searchParams.get('tenantId')).toBe(other);});

for(const [locale,width] of [['en',1440],['de',390]] as const)test(`${locale}/${width} collection reset restores each initial request without changing context`,async({page},info)=>{
 const text=locale==='de'?de:en,requests=await setup(page,'GLOBAL',locale);await page.setViewportSize({width,height:900});
 for(const kind of ['categories','manufacturers','products','locations','owners','assets','users']){
  const route=kind==='assets'?`/tenants/${tenant}/assets?q=URL-seed&lifecycle=DEPLOYED&condition=WORKING&loaned=true`:['locations','owners'].includes(kind)?`/tenants/${tenant}/${kind}`:`/${kind}`;
  const endpoint=kind==='users'?'/api/v1/users':kind==='assets'?`/api/v1/tenants/${tenant}/assets`:['locations','owners'].includes(kind)?`/api/v1/tenants/${tenant}/${kind}`:`/api/v1/catalog/${kind}`;
  await page.goto(route);const reads=()=>requests.filter(u=>u.pathname===endpoint);await expect(page.locator('tbody tr')).toHaveCount(2);
  const input=page.getByLabel(kind==='users'?text.ui.name:kind==='assets'?text.ui.assetIdentifiers:kind==='products'?text.ui.productSearch:kind==='categories'?text.ui.categorySearch:kind==='manufacturers'?text.ui.manufacturerSearch:kind==='locations'?text.ui.locationSearch:text.ui.ownerSearch,{exact:true});
  await input.fill('HP');await input.press('Enter');await expect.poll(()=>reads().at(-1)?.searchParams.get('q')).toBe('HP');
  if(['categories','manufacturers','products'].includes(kind))await page.getByLabel(kind==='categories'?text.ui.categoryRows:text.ui.catalogRows).selectOption('TENANT');
  if(kind==='products'){const category=page.getByRole('toolbar').getByRole('combobox',{name:text.fields.categories});await category.fill('Z');await page.getByRole('listbox').getByRole('option').first().click();await expect.poll(()=>reads().at(-1)?.searchParams.get('categoryId')).toBe(ref);}
  if(kind==='users'){await page.getByRole('button',{name:text.actions.moreFilters}).click();await page.getByRole('dialog').getByLabel(text.fields.status).selectOption('DISABLED');await page.getByRole('dialog').getByRole('button',{name:text.redesign.confirm,exact:true}).click();await expect.poll(()=>reads().at(-1)?.searchParams.get('status')).toBe('DISABLED');}
  await page.locator('thead button').first().click();await page.getByLabel(text.ui.pageSize).selectOption('40');await page.getByRole('button',{name:text.ui.nextPage}).click();await expect.poll(()=>reads().at(-1)?.searchParams.get('page')).toBe('1');
  await input.fill('unsent draft');expect(reads().at(-1)?.searchParams.get('q')).toBe('HP');
  if(['users','assets'].includes(kind)){await page.getByRole('button',{name:text.actions.moreFilters}).click();await page.getByRole('dialog').getByRole('button',{name:text.redesign.cancel,exact:true}).click();await expect(page.getByRole('dialog')).toHaveCount(0);}
  await page.getByRole('button',{name:text.actions.resetFilters}).click();
  const defaults=kind==='assets'?{page:'0',size:'20',sort:'createdAt',direction:'desc'}:{page:'0',size:'20'};
  await expect.poll(()=>Object.fromEntries(reads().at(-1)?.searchParams??[])).toEqual(defaults);
  await expect(input).toHaveValue('');await expect(page.getByLabel(text.ui.pageSize)).toHaveValue('20');
  if(['categories','manufacturers','products'].includes(kind))await expect(page.getByLabel(kind==='categories'?text.ui.categoryRows:text.ui.catalogRows)).toHaveValue('');
  if(kind==='products')await expect(page.getByRole('toolbar').getByRole('combobox',{name:text.fields.categories})).toHaveValue('');
  if(kind==='assets'){await expect(page).toHaveURL(`/tenants/${tenant}/assets`);await page.reload();await expect.poll(()=>Object.fromEntries(reads().at(-1)?.searchParams??[])).toEqual(defaults);}
  else await expect(page).toHaveURL(new RegExp(`/${kind}$`));
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:info.outputPath(`${locale}-${width}-${kind}-reset.png`)});
 }
});

for(const [locale,width] of [['en',1440],['de',390]] as const)test(`${locale}/${width} reset is keyboard-operable with visible focus and no confirmation`,async({page})=>{
 const text=locale==='de'?de:en,requests=await setup(page,'GLOBAL',locale);await page.setViewportSize({width,height:900});await page.goto(`/tenants/${tenant}/products`);
 const input=page.getByRole('textbox',{name:text.ui.productSearch});await input.fill('HP');await input.press('Enter');await expect.poll(()=>requests.filter(u=>u.pathname===`/api/v1/tenants/${tenant}/catalog/products`).at(-1)?.searchParams.get('q')).toBe('HP');
 const reset=page.getByRole('button',{name:text.actions.resetFilters});await reset.focus();await expect(reset).toBeFocused();expect(await reset.evaluate(el=>getComputedStyle(el).outlineStyle)).toBe('solid');await reset.press('Enter');
 await expect.poll(()=>Object.fromEntries(requests.filter(u=>u.pathname===`/api/v1/tenants/${tenant}/catalog/products`).at(-1)?.searchParams??[])).toEqual({page:'0',size:'20'});
 await expect(reset).toBeFocused();await expect(page.getByRole('alertdialog')).toHaveCount(0);await expect(page).toHaveURL(`/tenants/${tenant}/products`);
});

for(const [locale,width] of [['en',1440],['de',390]] as const)test(`${locale}/${width} Audit reset clears criteria and validation error`,async({page})=>{
 const text=locale==='de'?de:en,requests=await setup(page,'GLOBAL',locale);await page.setViewportSize({width,height:900});await page.goto('/audit');await page.getByRole('button',{name:text.actions.moreFilters,exact:true}).click();
 const form=page.getByRole('form',{name:text.redesign.filters});const reads=()=>requests.filter(u=>u.pathname==='/api/v1/audit-log');
 await form.getByLabel(text.ui.auditAction).fill('CATEGORY_CREATED');await form.getByLabel(text.ui.tenantContext).selectOption(tenant);
 await form.getByLabel(text.f5.from,{exact:true}).fill('2026-10-12T00:00');await form.getByRole('button',{name:text.actions.filter,exact:true}).click();
 await expect.poll(()=>reads().at(-1)?.searchParams.get('tenantId')).toBe(tenant);
 await page.locator('thead button').first().click();await page.getByLabel(text.ui.pageSize).selectOption('40');await page.getByRole('button',{name:text.ui.nextPage}).click();await expect.poll(()=>reads().at(-1)?.searchParams.get('page')).toBe('1');
 await form.getByLabel(text.f5.from,{exact:true}).fill('2026-10-12T10:00');await form.getByLabel(text.f5.until,{exact:true}).fill('2026-10-11T10:00');await form.getByRole('button',{name:text.actions.filter,exact:true}).click();await expect(form.getByRole('alert')).toBeVisible();
 await form.getByRole('button',{name:text.actions.resetFilters}).click();
 await expect.poll(()=>Object.fromEntries(reads().at(-1)?.searchParams??[])).toEqual({page:'0',size:'20',sort:'occurredAt',direction:'desc'});
 await expect(form.getByRole('alert')).toHaveCount(0);await form.getByRole('button',{name:text.actions.moreFilters,exact:true}).click();await expect(form.getByLabel(text.ui.auditAction,{exact:true})).toHaveValue('');await expect(form.getByLabel(text.ui.tenantContext,{exact:true})).toHaveValue('');await expect(form.getByLabel(text.f5.from,{exact:true})).toHaveValue('');await expect(form.getByLabel(text.f5.until,{exact:true})).toHaveValue('');await expect(form.getByRole('checkbox')).toHaveCount(0);await expect(page).toHaveURL(/\/audit$/);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
