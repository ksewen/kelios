import {assetDetailFixture,assetStateFixture,labelFixture,tenantFixture,fixtureMetadata} from './backend-fixtures';
import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',cat='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',man='cccccccc-cccc-cccc-cccc-cccccccccccc';
async function setup(page:Page){
 const posts:any[]=[],requests:{method:string;url:URL;body:unknown}[]=[];let fail=false;
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());requests.push({method:route.request().method(),url,body:route.request().postData()?route.request().postDataJSON():null});
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:man,scope:'GLOBAL',role:'ADMIN',status:'ACTIVE',tenantId:null,displayName:'Fixture'}}});
  if(route.request().method()==='POST'){posts.push(route.request().postDataJSON());if(fail)return route.fulfill({status:400,json:{code:'INVALID_INPUT'}});if(url.pathname==='/api/v1/tenants')return route.fulfill({json:cat});if(url.pathname.endsWith('/assets'))return route.fulfill({status:201,json:assetStateFixture({id:cat,tenantId:tenant})});if(url.pathname==='/api/v1/users/global')return route.fulfill({json:{id:cat,scope:'GLOBAL',tenantId:null,role:'USER',status:'ACTIVE',displayName:route.request().postDataJSON().displayName}});return route.fulfill(/\/(categories|manufacturers|products|locations|owners)$/.test(url.pathname)?{status:201,json:{id:cat}}:{status:200,body:''});}
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[tenantFixture(tenant,'Workspace')]});
  if(url.pathname==='/api/v1/users')return route.fulfill({json:{items:[],totalElements:0,page:Number(url.searchParams.get('page')??0),size:Number(url.searchParams.get('size')??20)}});
  const referenceTenant=url.searchParams.get('visibility')==='GLOBAL'?null:tenant;
  const items=url.pathname.endsWith('/categories')?[labelFixture(cat,'Category fixture',referenceTenant)]:url.pathname.endsWith('/manufacturers')?[labelFixture(man,'Manufacturer fixture',referenceTenant)]:url.pathname.endsWith('/products')?[labelFixture(cat,'Product fixture',tenant)]:[];
  return route.fulfill({json:{items,totalElements:items.length,page:0,size:20}});
 });
 return {posts,requests,setFail(value:boolean){fail=value;}};
}
for(const kind of ['categories','manufacturers','locations','owners'])test(`${kind}: success clears create draft; cancel/error preserve it`,async({page})=>{
 const f=await setup(page);await page.goto(`/tenants/${tenant}/${kind}`);
 const title=en.common.create.replace('{{kind}}',en.fields[kind as keyof typeof en.fields]);const trigger=page.getByRole('button',{name:en.actions.create,exact:true});await trigger.click();
 let dialog=page.getByRole('dialog');await dialog.getByLabel(en.ui.name,{exact:true}).fill('Draft');await dialog.getByRole('button',{name:en.redesign.cancel,exact:true}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel,exact:true}).click();await expect(dialog.getByLabel(en.ui.name,{exact:true})).toHaveValue('Draft');
 f.setFail(true);await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();await expect(dialog.getByRole('alert')).toBeVisible();await expect(dialog.getByLabel(en.ui.name,{exact:true})).toHaveValue('Draft');
 f.setFail(false);await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();await expect(dialog).toHaveCount(0);await trigger.click();await expect(dialog.getByLabel(en.ui.name,{exact:true})).toHaveValue('');await expect(dialog.getByRole('alert')).toHaveCount(0);
});
test('Product has two server-backed comboboxes and successful creation clears all values',async({page})=>{
 const f=await setup(page);await page.goto(`/tenants/${tenant}/products`);const title=en.common.create.replace('{{kind}}',en.fields.products),trigger=page.getByRole('button',{name:en.actions.create,exact:true});await trigger.click();const dialog=page.getByRole('dialog');await expect(dialog.getByLabel(en.fields.tenantName)).toHaveValue('');await expect(dialog.locator('input[role="combobox"]')).toHaveCount(2);
 await dialog.getByLabel(en.ui.name,{exact:true}).fill('Product draft');const category=dialog.getByRole('combobox',{name:en.fields.category,exact:true});await category.focus();await category.evaluate(el=>(window as any).category=el);await category.press('ArrowDown');await category.press('Enter');await expect(category).toHaveValue('Category fixture');expect(await category.evaluate(el=>el===(window as any).category)).toBe(true);
 await dialog.getByRole('combobox',{name:en.fields.manufacturer,exact:true}).click();await dialog.getByRole('option',{name:/Manufacturer fixture/}).click();await dialog.getByLabel(en.ui.manufacturerNumber,{exact:true}).fill('M-1');await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();await expect(dialog).toHaveCount(0);expect(f.posts.at(-1)).toEqual({name:'Product draft',categoryId:cat,manufacturerId:man,manufacturerNumber:'M-1'});await trigger.click();await expect(dialog.getByLabel(en.ui.name,{exact:true})).toHaveValue('');await expect(dialog.getByLabel(en.ui.manufacturerNumber,{exact:true})).toHaveValue('');for(const input of await dialog.locator('input[role="combobox"]').all())await expect(input).toHaveValue('');
});
test('Product Create ownership is independent of operating context and routes UUID-backed references',async({page})=>{
 const f=await setup(page);await page.goto(`/tenants/${tenant}/products`);
 const create=page.getByRole('button',{name:en.actions.create,exact:true});await create.click();let dialog=page.getByRole('dialog');
 const owner=dialog.getByLabel(en.fields.tenantName);await expect(owner).toHaveValue('');
 await expect.poll(()=>f.requests.some(item=>item.url.pathname==='/api/v1/catalog/categories'&&item.url.searchParams.get('visibility')==='GLOBAL')).toBe(true);
 await expect.poll(()=>f.requests.some(item=>item.url.pathname==='/api/v1/catalog/manufacturers'&&item.url.searchParams.get('visibility')==='GLOBAL')).toBe(true);
 await owner.selectOption(tenant);
 await expect(page).toHaveURL(new RegExp(`/tenants/${tenant}/products$`));
 await expect.poll(()=>f.requests.some(item=>item.url.pathname===`/api/v1/tenants/${tenant}/catalog/categories`)).toBe(true);
 await expect.poll(()=>f.requests.some(item=>item.url.pathname===`/api/v1/tenants/${tenant}/catalog/manufacturers`)).toBe(true);
 await dialog.getByLabel(en.ui.name,{exact:true}).fill('Tenant Product');
 await dialog.getByRole('combobox',{name:en.fields.category,exact:true}).click();await dialog.getByRole('option',{name:/Category fixture/}).click();
 await dialog.getByRole('combobox',{name:en.fields.manufacturer,exact:true}).click();await dialog.getByRole('option',{name:/Manufacturer fixture/}).click();
 await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();
 await expect(dialog).toHaveCount(0);
 const write=f.requests.findLast(item=>item.method==='POST'&&item.url.pathname.endsWith('/products'));
 expect(write?.url.pathname).toBe(`/api/v1/tenants/${tenant}/catalog/products`);
 expect(write?.body).toEqual({name:'Tenant Product',categoryId:cat,manufacturerId:man,manufacturerNumber:null});
 await create.click();dialog=page.getByRole('dialog');await expect(dialog.getByLabel(en.fields.tenantName)).toHaveValue('');
 await dialog.getByRole('button',{name:en.redesign.cancel,exact:true}).click();
 await page.goto('/products');await create.click();dialog=page.getByRole('dialog');await expect(dialog.getByLabel(en.fields.tenantName)).toHaveValue('');
 await dialog.getByLabel(en.ui.name,{exact:true}).fill('Global Product');
 await dialog.getByRole('combobox',{name:en.fields.category,exact:true}).click();await dialog.getByRole('option',{name:/Category fixture/}).click();
 await dialog.getByRole('combobox',{name:en.fields.manufacturer,exact:true}).click();await dialog.getByRole('option',{name:/Manufacturer fixture/}).click();
 await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();
 const rootWrite=f.requests.findLast(item=>item.method==='POST'&&item.url.pathname.endsWith('/products'));
 expect(rootWrite?.url.pathname).toBe('/api/v1/catalog/products');
 expect(rootWrite?.body).toEqual({name:'Global Product',categoryId:cat,manufacturerId:man,manufacturerNumber:null});
});
for(const width of [1440,390])test(`Product ownership choice narrows the server collection at ${width}px`,async({page})=>{
 await setup(page);await page.setViewportSize({width,height:900});const productReads:URL[]=[];
 await page.route('**/api/v1/catalog/products?**',route=>{
  const url=new URL(route.request().url());productReads.push(url);const global={...labelFixture(cat,'Global Product',null),manufacturerNumber:null};const local={...labelFixture(man,'Tenant Product',tenant),manufacturerNumber:null};
  const visibility=url.searchParams.get('visibility');const items=[global,local].filter(item=>!visibility||item.visibility===visibility);
  return route.fulfill({json:{items,totalElements:items.length,page:0,size:20}});
 });
 await page.goto('/products');const choice=page.getByLabel(en.ui.catalogRows,{exact:true});
 await expect(choice.locator('option')).toHaveText([en.ui.categoryRowsAll,en.ui.categoryRowsGlobal,en.ui.categoryRowsTenant]);
 await expect(page.locator('tbody')).toContainText('Global Product');await expect(page.locator('tbody')).toContainText('Tenant Product');
 await choice.selectOption('GLOBAL');await expect(page.locator('tbody')).toContainText('Global Product');await expect(page.locator('tbody')).not.toContainText('Tenant Product');
 await choice.selectOption('TENANT');await expect(page.locator('tbody')).toContainText('Tenant Product');await expect(page.locator('tbody')).not.toContainText('Global Product');
 expect(productReads.at(-1)?.searchParams.get('visibility')).toBe('TENANT');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('shared Product reference combobox opens without moving its dialog or form fields',async({page})=>{
 await setup(page);await page.setViewportSize({width:390,height:900});await page.goto(`/tenants/${tenant}/products`);await page.getByRole('button',{name:en.actions.create,exact:true}).click();
 const dialog=page.getByRole('dialog'),category=dialog.getByRole('combobox',{name:en.fields.category,exact:true}),manufacturer=dialog.getByRole('combobox',{name:en.fields.manufacturer,exact:true});await category.scrollIntoViewIfNeeded();
 const before=await dialog.boundingBox(),neighbor=await manufacturer.boundingBox(),formHeight=await dialog.getByRole('form').evaluate(el=>el.getBoundingClientRect().height);
 await category.click();await expect(dialog.getByRole('listbox',{name:en.fields.category,exact:true})).toBeVisible();expect(await dialog.boundingBox()).toEqual(before);expect(await manufacturer.boundingBox()).toEqual(neighbor);expect(await dialog.getByRole('form').evaluate(el=>el.getBoundingClientRect().height)).toBe(formHeight);
 await category.press('Escape');await expect(dialog.getByRole('listbox',{name:en.fields.category,exact:true})).toHaveCount(0);
});
test('Tenant create success clears its name',async({page})=>{await setup(page);await page.goto('/tenants');const trigger=page.getByRole('button',{name:en.actions.create,exact:true});await trigger.click();const dialog=page.getByRole('dialog');await dialog.getByLabel(en.ui.tenantName).fill('Tenant draft');await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();await expect(dialog).toHaveCount(0);await trigger.click();await expect(dialog.getByLabel(en.ui.tenantName)).toHaveValue('');});
test('GLOBAL User create success clears identity and credential fields',async({page})=>{await setup(page);await page.goto('/users');const trigger=page.getByRole('button',{name:en.actions.create,exact:true});await trigger.click();const dialog=page.getByRole('dialog');await dialog.getByLabel(en.redesign.email).fill('fixture-user@example.test');await dialog.getByLabel(en.ui.displayName,{exact:true}).fill('Fixture User');await dialog.getByLabel(en.ui.initialPassword).fill('Disposable-Test!42');await dialog.getByRole('button',{name:en.actions.create,exact:true}).click();await expect(dialog).toHaveCount(0);await trigger.click();for(const input of await dialog.locator('input').all())await expect(input).toHaveValue('');});
test('Asset registration success explicitly resets mounted fields and references',async({page})=>{
 await setup(page);await page.route('**/locations?**',route=>route.fulfill({json:{items:[{id:cat,name:'Location fixture'}],totalElements:1,page:0,size:20}}));await page.goto(`/tenants/${tenant}/assets`);
 await page.evaluate(async tenant=>{const React=(await import('/node_modules/.vite/deps/react.js')).default;const {createRoot}=(await import('/node_modules/.vite/deps/react-dom_client.js')).default;const {Registration}=await import('/src/features/inventory/pages.tsx');const {ToastProvider}=await import('/src/components/ui/toast.tsx');const host=document.createElement('div');host.id='registration-harness';document.body.append(host);createRoot(host).render(React.createElement(ToastProvider,null,React.createElement(Registration,{tenant,open:()=>{(window as any).created=true;}})));},tenant);
 const form=page.locator('#registration-harness');const name=form.getByLabel(en.fields.localIdentifier,{exact:true});await name.fill('R421');await name.evaluate(el=>(window as any).field=el);
 for(const label of [en.ui.product,en.fields.location]){const input=form.getByRole('combobox',{name:label,exact:true});await input.focus();await input.press('ArrowDown');await input.press('Enter');}
 await form.getByLabel(en.ui.loanable,{exact:true}).selectOption('false');await form.getByRole('button',{name:en.ui.registerAsset,exact:true}).click();await expect.poll(()=>page.evaluate(()=>(window as any).created)).toBe(true);await expect(name).toHaveValue('');expect(await name.evaluate(el=>el===(window as any).field)).toBe(true);for(const input of await form.locator('input[role=combobox]').all())await expect(input).toHaveValue('');await expect(form.getByLabel(en.ui.catalogSelection)).toHaveCount(0);await expect(form.getByLabel(en.ui.loanable)).toHaveValue('');
});
for(const kind of ['categories','manufacturers'] as const)test(`Product ${kind} search keeps UUID through paging, disables stale options, and rejects late results`,async({page})=>{
 await setup(page);const held:any[]=[];await page.route(`**/api/v1/catalog/${kind}?**`,route=>{held.push(route);});await page.goto(`/tenants/${tenant}/products`);await page.getByRole('button',{name:en.actions.create,exact:true}).click();const dialog=page.getByRole('dialog'),input=dialog.getByRole('combobox',{name:kind==='categories'?en.fields.category:en.fields.manufacturer,exact:true});const reply=async(q:string,n:number,name:string)=>{await expect.poll(()=>held.some(r=>{const u=new URL(r.request().url());return (u.searchParams.get('q')??'')===q&&Number(u.searchParams.get('page'))===n;})).toBe(true);const route=held.find(r=>{const u=new URL(r.request().url());return (u.searchParams.get('q')??'')===q&&Number(u.searchParams.get('page'))===n;});held.splice(held.indexOf(route),1);await route.fulfill({json:{items:[{id:cat,name}],totalElements:30,page:n,size:20}});};
 await reply('',0,'Original');await input.focus();await input.evaluate(el=>(window as any).field=el);await input.press('ArrowDown');await input.press('Enter');await input.click();await dialog.getByRole('button',{name:en.ui.loadMore,exact:true}).click();await reply('',1,'Other page');await expect(input).toHaveValue('Original');await input.fill('a');await expect(dialog.locator('li[role="option"]')).toHaveAttribute('aria-disabled','true');await input.fill('ab');await reply('ab',0,'Newest');await reply('a',0,'Obsolete');await expect(dialog.locator('li[role="option"]')).toContainText('Newest');await expect(input).toBeFocused();expect(await input.evaluate(el=>el===(window as any).field)).toBe(true);
});
test('Asset edit pristine reopen uses refreshed resource; action success clears local drafts',async({page})=>{
 await setup(page);let localIdentifier='Original';const asset=assetDetailFixture({id:cat,tenantId:tenant,assetTag:'Fixture',version:0,lifecycle:'DEPLOYED',condition:'WORKING',loanable:true});
 await page.route(`**/api/v1/assets/${cat}`,route=>route.fulfill({json:{...asset,localIdentifier}}));await page.route(`**/api/v1/assets/${cat}/events?**`,route=>route.fulfill({json:{items:[],totalElements:0,page:0,size:20}}));await page.goto(`/inventory/assets/${cat}`);
 const open=async(name:string)=>{await page.getByRole('button',{name:name===en.ui.saveMetadata?en.ui.editCategory:name,exact:true}).click();};
 await open(en.ui.saveMetadata);await expect(page.getByRole('dialog').getByLabel(en.fields.localIdentifier,{exact:true})).toHaveValue('Original');await page.getByRole('dialog').getByRole('button',{name:en.redesign.cancel,exact:true}).click();localIdentifier='Refreshed';await page.reload();await expect(page.locator('main').getByText('Refreshed',{exact:true}).first()).toBeVisible();await open(en.ui.saveMetadata);await expect(page.getByRole('dialog').getByLabel(en.fields.localIdentifier,{exact:true})).toHaveValue('Refreshed');await page.getByRole('dialog').getByRole('button',{name:en.redesign.cancel,exact:true}).click();
 await open(en.ui.loanAsset);let dialog=page.getByRole('dialog');await dialog.getByLabel(en.ui.borrower,{exact:true}).fill('Borrower draft');await dialog.getByLabel(en.fields.expectedReturnAt,{exact:true}).fill('2026-07-01');await dialog.getByLabel(en.ui.loanNote,{exact:true}).fill('Note draft');await dialog.getByRole('button',{name:en.ui.loanAsset,exact:true}).click();await expect(dialog).toHaveCount(0);await open(en.ui.loanAsset);await expect(dialog.getByLabel(en.ui.borrower,{exact:true})).toHaveValue('');await expect(dialog.getByLabel(en.fields.expectedReturnAt,{exact:true})).toHaveValue('');await expect(dialog.getByLabel(en.ui.loanNote,{exact:true})).toHaveValue('');await dialog.getByRole('button',{name:en.redesign.cancel,exact:true}).click();
 await open(en.ui.changeLifecycle);await dialog.getByLabel(en.ui.lifecycleTarget).selectOption('IN_STOCK');await dialog.getByLabel(en.ui.transitionNote).fill('Transition draft');await dialog.getByRole('button',{name:en.ui.changeLifecycle,exact:true}).click();await expect(dialog).toHaveCount(0);await open(en.ui.changeLifecycle);await expect(dialog.getByLabel(en.ui.lifecycleTarget)).toHaveValue('');await expect(dialog.getByLabel(en.ui.transitionNote)).toHaveValue('');
});
