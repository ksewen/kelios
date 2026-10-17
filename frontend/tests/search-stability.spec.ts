import {assetDetailFixture,assetItemFixture,labelFixture,tenantFixture} from './backend-fixtures';
import {test,expect,type Page,type Route} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',other='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const user={id:'cccccccc-cccc-cccc-cccc-cccccccccccc',scope:'GLOBAL',tenantId:null,role:'ADMIN',status:'ACTIVE',displayName:'UI fixture'};
const asset=assetItemFixture({assetTag:'20261011-001',localIdentifier:'Stable fixture'});
const row=labelFixture(asset.id,'Stable fixture',tenant);
const collection=(items:unknown[])=>({items,totalElements:items.length,page:0,size:20});
async function setup(page:Page, locale='en'){
 const held:{url:URL;route:Route}[]=[];let currentUser={...user};
 await page.addInitScript(language=>localStorage.setItem('kelios.ui.language',language),locale);
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url());
  if(url.pathname.endsWith('/session')&&route.request().method()==='DELETE')return route.fulfill({status:204});
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'UI-fixture'}});
  if(url.pathname.endsWith('/session/refresh')||url.pathname.endsWith('/local/login'))return route.fulfill({json:{accessToken:'UI-only-fixture',expiresIn:900,user:currentUser}});
  if(url.pathname==='/api/v1/tenants')return currentUser.scope==='GLOBAL'?route.fulfill({json:[tenantFixture(tenant,'Workspace A'),tenantFixture(other,'Workspace B')]}):route.fulfill({status:403,json:{status:403}});
  held.push({url,route});
 });
 async function request(path:string,q=''){
  await expect.poll(()=>held.some(r=>r.url.pathname===path&&(r.url.searchParams.get('q')??'')===q)).toBe(true);
  return held.find(r=>r.url.pathname===path&&(r.url.searchParams.get('q')??'')===q)!;
 }
 return {held,request,setUser(value:typeof user){currentUser=value;},async reply(path:string,q:string,items:unknown[]){const r=await request(path,q);await r.route.fulfill({json:collection(items)});}};
}
for(const language of ['en','de'] as const)test(`${language}: stable Asset table, focus, equivalent/empty/error responses and latest request wins`,async({page})=>{
 const t=language==='en'?en:de,fixture=await setup(page,language),path=`/api/v1/tenants/${tenant}/assets`;
 await page.goto(`/tenants/${tenant}/assets`);await fixture.request(path);
 await expect(page.locator('main .animate-pulse')).toBeVisible();await fixture.reply(path,'',[asset]);
 const input=page.getByLabel(t.ui.assetIdentifiers),table=page.locator('main table');await expect(table).toBeVisible();
 await table.evaluate(el=>(window as any).originalTable=el);
 await input.fill('a');await input.press('Enter');await fixture.request(path,'a');await expect(input).toBeFocused();
 await expect(page.locator('main .animate-pulse')).toHaveCount(0);await expect(page.getByText(t.redesign.updatingResults)).toHaveCount(0);
 await expect(page.getByRole('button',{name:t.ui.nextPage,exact:true})).toBeVisible();
 expect(await table.evaluate(el=>el===(window as any).originalTable)).toBe(true);
 await fixture.reply(path,'a',[asset]);await expect(page.getByText(t.redesign.updatingResults)).toHaveCount(0);
 expect(await table.evaluate(el=>el===(window as any).originalTable)).toBe(true);
 await input.fill('ab');await input.press('Enter');await fixture.request(path,'ab');await input.fill('abc');await input.press('Enter');await fixture.reply(path,'abc',[{...asset,localIdentifier:'Newest result'}]);
 await expect(page.getByText('Newest result',{exact:true})).toBeVisible();await fixture.reply(path,'ab',[{...asset,localIdentifier:'Wrong older result'}]);
 await expect(page.getByText('Wrong older result')).toHaveCount(0);
 await input.fill('empty');await input.press('Enter');await fixture.reply(path,'empty',[]);await expect(page.getByText(t.ui.noRecords)).toBeVisible();
 await input.fill('restore');await input.press('Enter');await fixture.reply(path,'restore',[asset]);await expect(table).toBeVisible();
 await input.fill('error');await input.press('Enter');const failed=await fixture.request(path,'error');await failed.route.fulfill({status:503,json:{code:'SERVICE_UNAVAILABLE'}});
 await expect(page.locator('main [role=alert]')).toBeVisible();await expect(table).toHaveCount(0);await expect(page.getByText(t.redesign.updatingResults)).toHaveCount(0);
});
for(const kind of ['categories','manufacturers','products','locations','owners'])test(`${kind}: pending search retains the actual resource table`,async({page})=>{
 const f=await setup(page),path=`/api/v1/tenants/${tenant}/${['locations','owners'].includes(kind)?'':'catalog/'}${kind}`;
 await page.goto(`/tenants/${tenant}/${kind}`);await f.reply(path,'',[row]);
 const table=page.locator('main table');await expect(table).toBeVisible();await table.evaluate(el=>(window as any).originalTable=el);
 const search=page.getByRole('toolbar').getByLabel(({categories:en.ui.categorySearch,manufacturers:en.ui.manufacturerSearch,products:en.ui.productSearch,locations:en.ui.locationSearch,owners:en.ui.ownerSearch} as Record<string,string>)[kind]);await search.fill('changed');await search.press('Enter');await f.request(path,'changed');
 await expect(page.locator('main .animate-pulse')).toHaveCount(0);expect(await table.evaluate(el=>el===(window as any).originalTable)).toBe(true);
 await f.reply(path,'changed',[row]);await expect(page.getByText(en.redesign.updatingResults)).toHaveCount(0);
});
test('Tenant/workspace change discards old rows before the new request finishes',async({page})=>{
 const f=await setup(page);await page.goto(`/tenants/${tenant}/assets`);await f.reply(`/api/v1/tenants/${tenant}/assets`,'',[asset]);await expect(page.getByText('Stable fixture')).toBeVisible();
 await page.getByRole('button',{name:en.ui.tenantContext,exact:true}).click();await page.getByRole('menuitem',{name:'Workspace B',exact:true}).click();
 await f.request(`/api/v1/tenants/${other}/assets`);await expect(page.getByText('Stable fixture')).toHaveCount(0);await expect(page.locator('main .animate-pulse')).toBeVisible();
});
test('reference picker keeps old options visible but disables selection until refreshed',async({page})=>{
 const f=await setup(page),path=`/api/v1/tenants/${tenant}/catalog/products`;
 await page.goto(`/tenants/${tenant}/assets/new`);await f.reply(path,'',[row]);
 const input=page.getByRole('combobox',{name:en.ui.product,exact:true});await input.focus();const option=page.getByRole('option',{name:/Stable fixture/});await expect(option).toHaveAttribute('aria-disabled','false');
 await input.fill('changed');await f.request(path,'changed');await expect(option).toHaveAttribute('aria-disabled','true');await expect(option).toBeVisible();await expect(page.getByRole('status')).toHaveCount(0);await expect(page.getByRole('button',{name:'Filter',exact:true})).toHaveCount(0);
 await f.reply(path,'changed',[{...row,name:'New option'}]);await expect(page.getByRole('option',{name:/New option/})).toHaveAttribute('aria-disabled','false');await expect(option).toHaveCount(0);
});
test('organization picker retains disabled results during its exact 150ms debounce',async({page})=>{
 await page.route('**/api/v1/auth/session/refresh',route=>route.fulfill({status:401,json:{}}));
 await page.route('**/api/v1/auth/csrf',route=>route.fulfill({json:{token:'UI-fixture'}}));
 const held:{q:string;route:Route}[]=[];await page.route('**/api/v1/auth/oidc/organizations?**',route=>{held.push({q:new URL(route.request().url()).searchParams.get('q')??'',route});});
 await page.goto('/login');await expect(page.getByRole('button',{name:en.ui.signInWithIServ,exact:true})).toBeVisible();await page.clock.install();await page.clock.pauseAt(new Date());await page.getByRole('button',{name:en.ui.signInWithIServ,exact:true}).click();await expect(page.getByRole('dialog').locator('.animate-pulse').first()).toBeVisible();
 await page.clock.runFor(150);await expect.poll(()=>held.length).toBe(1);await held[0].route.fulfill({json:{items:[{routingId:tenant,name:'Organization A'}],page:0,size:20,totalElements:1}});
 const option=page.getByRole('button',{name:'Organization A',exact:true});await expect(option).toBeEnabled();await page.getByLabel(en.redesign.searchOrganizations).fill('new');
 await expect(option).toBeDisabled();await expect(page.getByRole('dialog').locator('.animate-pulse')).toHaveCount(0);
 await page.clock.runFor(149);expect(held.length).toBe(1);await page.clock.runFor(1);await expect.poll(()=>held.length).toBe(2);
 await held[1].route.fulfill({json:{items:[{routingId:other,name:'Organization B'}],page:0,size:20,totalElements:1}});await expect(page.getByRole('button',{name:'Organization B',exact:true})).toBeEnabled();
});

test('read hook discards resource/workspace/session changes while its consumer stays mounted',async({page})=>{
 const f=await setup(page);await page.goto(`/tenants/${tenant}/assets`);await f.reply(`/api/v1/tenants/${tenant}/assets`,'',[asset]);await expect(page.getByText('Stable fixture')).toBeVisible();
 await page.evaluate(async()=>{
  // Test-only harness: keep the production hook mounted across context changes.
  const React=(await import('/node_modules/.vite/deps/react.js')).default;
  const {createRoot}=(await import('/node_modules/.vite/deps/react-dom_client.js')).default;
  const {useRead}=await import('/src/features/inventory/pages.tsx');
  const host=document.createElement('div');host.id='read-harness';document.body.append(host);const root=createRoot(host);
  function Read({path}:{path:string}){const state=useRead(path);return React.createElement('div',{'data-pending':state.pending},state.data?JSON.stringify(state.data):'No retained data');}
  (window as any).renderRead=(path:string)=>root.render(React.createElement(Read,{path}));
  (window as any).renderRead('/api/v1/tenants/A/categories');
 });
 await f.reply('/api/v1/tenants/A/categories','',[row]);await expect(page.locator('#read-harness')).toContainText('Stable fixture');
 for(const path of ['/api/v1/tenants/B/categories','/api/v1/tenants/B/products','/api/v1/audit-log?tenantId=A','/api/v1/audit-log?tenantId=B','/api/v1/audit-log?globalOnly=true']){
  const count=f.held.length;await page.evaluate(path=>(window as any).renderRead(path),path);await expect.poll(()=>f.held.length).toBe(count+1);await expect(page.locator('#read-harness')).toHaveText('No retained data');
  await f.held.at(-1)!.route.fulfill({json:collection([row])});await expect(page.locator('#read-harness')).toContainText('Stable fixture');
 }
 // Even re-login as the same user creates a distinct authentication context.
 let auditRequests=f.held.filter(r=>r.url.pathname==='/api/v1/audit-log').length;
 await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.login('UI fixture','UI-only test input');});
 await expect(page.locator('#read-harness')).toHaveText('No retained data');
 await expect.poll(()=>f.held.filter(r=>r.url.pathname==='/api/v1/audit-log').length).toBe(auditRequests+1);
 await f.held.filter(r=>r.url.pathname==='/api/v1/audit-log').at(-1)!.route.fulfill({json:collection([row])});await expect(page.locator('#read-harness')).toContainText('Stable fixture');
 f.setUser({...user,role:'USER'});auditRequests=f.held.filter(r=>r.url.pathname==='/api/v1/audit-log').length;
 await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.login('UI fixture','UI-only test input');});
 await expect(page.locator('#read-harness')).toHaveText('No retained data');
 await expect.poll(()=>f.held.filter(r=>r.url.pathname==='/api/v1/audit-log').length).toBe(auditRequests+1);
 await f.held.filter(r=>r.url.pathname==='/api/v1/audit-log').at(-1)!.route.fulfill({json:collection([row])});await expect(page.locator('#read-harness')).toContainText('Stable fixture');
 f.setUser({...user,id:'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'});
 await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.login('Other UI fixture','UI-only test input');});
 await expect(page.locator('#read-harness')).toHaveText('No retained data');
 await page.route('**/api/v1/auth/session/refresh',route=>route.fulfill({status:401,json:{}}));
 await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.logout();});
 await expect(page.locator('#read-harness')).toHaveText('No retained data');
});

test('Audit filter refresh keeps its table and paging mounted',async({page})=>{
 const f=await setup(page);await page.goto('/audit');await f.reply('/api/v1/audit-log','',[{...row,action:'TENANT_CREATED',actorDisplayName:'Fixture actor',targetType:'Tenant',occurredAt:'2026-10-11T00:00:00Z'}]);
 const table=page.locator('main table');await expect(table).toBeVisible();await table.evaluate(el=>(window as any).originalTable=el);
 await page.locator('main details summary').click();const count=f.held.length;await page.getByLabel(en.ui.auditAction).fill('USER_CREATED');await page.getByLabel(en.ui.auditAction).press('Tab');expect(f.held.length).toBe(count);await page.getByRole('button',{name:en.actions.filter,exact:true}).click();
 await expect.poll(()=>f.held.some(r=>r.url.searchParams.get('action')==='USER_CREATED')).toBe(true);
 expect(await table.evaluate(el=>el===(window as any).originalTable)).toBe(true);await expect(page.locator('main .animate-pulse')).toHaveCount(0);
 await f.held.find(r=>r.url.searchParams.get('action')==='USER_CREATED')!.route.fulfill({json:collection([])});await expect(page.getByText(en.ui.noRecords)).toBeVisible();
});

test('language and sidebar layout changes preserve navigation and dashboard metric identity',async({page})=>{
 const f=await setup(page);await page.goto(`/tenants/${tenant}/dashboard`);await expect.poll(()=>f.held.length).toBe(7);
 for(const r of f.held)await r.route.fulfill({json:r.url.pathname==='/api/v1/users'?collection([]):collection(r.url.pathname.endsWith('/assets')?[asset]:[])});
 await expect(page.locator('main .animate-pulse')).toHaveCount(0);
 const metrics=page.locator('main section > .grid').first().locator('a');await expect(metrics).toHaveCount(4);
 const navigation=page.locator('aside').first().getByRole('link',{name:en.fields.assets,exact:true});
 await navigation.evaluate(el=>(window as any).originalNavigation=el);
 await metrics.evaluateAll(nodes=>(window as any).originalMetrics=nodes);
 await page.getByRole('button',{name:en.common.language,exact:true}).click();await page.getByRole('menuitem',{name:/Deutsch/}).click();
 await expect(page.getByRole('heading',{name:de.redesign.dashboard,exact:true})).toBeVisible();
 expect(await metrics.evaluateAll(nodes=>nodes.every((node,i)=>node===(window as any).originalMetrics[i]))).toBe(true);
 const translated=page.locator('aside').first().getByRole('link',{name:de.fields.assets,exact:true});expect(await translated.evaluate(el=>el===(window as any).originalNavigation)).toBe(true);
 expect(f.held.length).toBe(7);
 await page.getByRole('button',{name:de.redesign.collapseSidebar}).click();expect(await translated.evaluate(el=>el===(window as any).originalNavigation)).toBe(true);
});

test('equivalent responses preserve row identity, table scroll, selection and page scroll',async({page})=>{
 await page.setViewportSize({width:390,height:500});const f=await setup(page),path=`/api/v1/tenants/${tenant}/assets`;
 const rows=Array.from({length:20},(_,i)=>({...asset,id:`dddddddd-dddd-dddd-dddd-${String(i).padStart(12,'0')}`,assetTag:`20261011-${i}`,localIdentifier:'Long operational label '.repeat(8)}));
 await page.goto(`/tenants/${tenant}/assets`);await f.reply(path,'',rows);const table=page.locator('main table');await expect(table).toBeVisible();
 await page.getByLabel(en.ui.assetIdentifiers).fill('unchanged');await page.getByRole('toolbar').getByLabel(en.ui.assetIdentifiers).press('Enter');await f.request(path,'unchanged');
 const before=await table.evaluate(el=>{
  const frame=el.parentElement!;frame.scrollLeft=100;window.scrollTo(0,400);
  const first=el.querySelector('tbody tr')!;(window as any).retainedRow=first;
  const selection=window.getSelection()!,range=document.createRange();range.selectNodeContents(first.querySelector('td')!);selection.removeAllRanges();selection.addRange(range);
  return {left:frame.scrollLeft,top:window.scrollY,selection:selection.toString()};
 });
 await f.reply(path,'unchanged',rows);await expect(page.getByText(en.redesign.updatingResults)).toHaveCount(0);
 expect(await table.evaluate(el=>({same:el.querySelector('tbody tr')===(window as any).retainedRow,left:el.parentElement!.scrollLeft,top:window.scrollY,selection:window.getSelection()!.toString()}))).toEqual({same:true,...before});
});

test('unchanged token renewal does not reload the visible read context',async({page})=>{
 const f=await setup(page),path=`/api/v1/tenants/${tenant}/assets`;await page.goto(`/tenants/${tenant}/assets`);await f.reply(path,'',[asset]);
 const table=page.locator('main table');await expect(table).toBeVisible();await table.evaluate(el=>(window as any).originalTable=el);const count=f.held.length;
 await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.refresh();});
 expect(await table.evaluate(el=>el===(window as any).originalTable)).toBe(true);await expect(page.locator('main .animate-pulse')).toHaveCount(0);expect(f.held.length).toBe(count);
});

test('history field identity survives localization while the history tab stays selected',async({page})=>{
 const f=await setup(page),path=`/api/v1/assets/${asset.id}`;
 await page.goto(`/inventory/assets/${asset.id}`);await f.request(path);await f.held.find(r=>r.url.pathname===path)!.route.fulfill({json:assetDetailFixture({...asset,tenantId:tenant,categoryId:row.id,category:{id:row.id,name:row.name},manufacturerId:row.id,manufacturer:{id:row.id,name:row.name},locationId:row.id,location:{id:row.id,name:'Fixture Location'},loanable:false,version:0})});
 await f.reply(path+'/events','',[{id:row.id,type:'UPDATED',actorDisplayName:'Fixture',occurredAt:'2026-10-11T00:00:00Z',data:{before:{localIdentifier:'Before'},after:{localIdentifier:'After'}}}]);
 await page.getByRole('tab',{name:en.redesign.history,exact:true}).click();const field=page.locator('tbody tr').filter({hasText:en.fields.localIdentifier});await expect(field).toBeVisible();await field.evaluate(el=>(window as any).historyField=el);
 await page.getByRole('button',{name:en.common.language,exact:true}).click();await page.getByRole('menuitem',{name:/Deutsch/}).click();
 const translated=page.locator('tbody tr').filter({hasText:de.fields.localIdentifier});expect(await translated.evaluate(el=>el===(window as any).historyField)).toBe(true);await expect(page.getByRole('tab',{name:de.redesign.history,exact:true})).toHaveAttribute('aria-selected','true');
});
