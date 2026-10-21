import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
import {assetDetailFixture,tenantFixture} from './backend-fixtures';

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
type State=ReturnType<typeof assetDetailFixture>;
async function setup(page:Page,{language='en',scope='GLOBAL',role='ADMIN',state={}}:{language?:'en'|'de';scope?:'GLOBAL'|'TENANT';role?:'ADMIN'|'USER';state?:Partial<State>}={}){
 let asset=assetDetailFixture({id,tenantId:tenant,version:3,...state}),failure:string|null=null;
 let pauseDetail=false,releaseDetail:(()=>void)|null=null;
 const writes:{path:string;method:string;body:any}[]=[];
 await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);
 await page.route('**/api/v1/**',async route=>{const url=new URL(route.request().url()),path=url.pathname,method=route.request().method();
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope,role,status:'ACTIVE',tenantId:scope==='TENANT'?tenant:null,displayName:'Fixture'}}});
  if(method!=='GET'){const body=route.request().postDataJSON();writes.push({path,method,body});if(failure)return route.fulfill({status:409,json:{code:failure}});const blocking=path.endsWith('/lifecycle-transitions')&&body.target==='DEREGISTERED'||path.endsWith('/condition-transitions')&&['DEFECTIVE','UNUSABLE'].includes(body.target);asset={...asset,...(path.endsWith('/loanability')?{loanable:body.loanable}:path.endsWith('/lifecycle-transitions')?{lifecycle:body.target}:path.endsWith('/condition-transitions')?{condition:body.target}:{}),loanable:blocking?false:path.endsWith('/loanability')?body.loanable:asset.loanable,version:asset.version+1};return route.fulfill({json:asset});}
  if(path==='/api/v1/tenants')return route.fulfill({json:[tenantFixture(tenant,'Fixture Tenant')]});
  if(path===`/api/v1/tenants/${tenant}`)return route.fulfill({json:tenantFixture(tenant,'Fixture Tenant')});
  if(path===`/api/v1/assets/${id}`){if(pauseDetail&&writes.length)await new Promise<void>(resolve=>{releaseDetail=resolve;});return route.fulfill({json:asset});}
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return {writes,set:(next:Partial<State>)=>{asset={...asset,...next};},fail:(code:string|null)=>{failure=code;},holdDetail:()=>{pauseDetail=true;return ()=>{pauseDetail=false;releaseDetail?.();releaseDetail=null;};}};
}

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} Asset status colors and guarded entry points`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language});await page.setViewportSize({width,height:900});await page.goto(`/inventory/assets/${id}`);
 const states=[['lifecycle','REGISTERED',true,'rgb(243, 244, 246)'],['lifecycle','IN_STOCK',true,'rgb(219, 234, 254)'],['lifecycle','DEPLOYED',true,'rgb(220, 252, 231)'],['lifecycle','DEREGISTERED',false,'rgb(255, 237, 213)'],['condition','UNKNOWN',true,'rgb(243, 244, 246)'],['condition','WORKING',true,'rgb(220, 252, 231)'],['condition','DEFECTIVE',true,'rgb(254, 243, 199)'],['condition','UNUSABLE',false,'rgb(254, 226, 226)']] as const;
 for(const [kind,state,actionable,tone] of states){f.set({[kind]:state});await page.reload();
  const label=text.enums[state],action=kind==='lifecycle'?text.ui.changeLifecycle:text.ui.changeCondition;
  const indicator=page.getByRole('tabpanel',{name:text.redesign.overview}).getByText(label,{exact:true});await expect(indicator).toBeVisible();await expect(indicator).toHaveCSS('background-color',tone);
  const trigger=page.getByRole('button',{name:`${action}: ${label}`,exact:true});await expect(trigger).toHaveCount(actionable?1:0);
  if(actionable){await trigger.focus();await page.keyboard.press('Enter');await expect(page.getByRole('dialog',{name:action})).toBeVisible();await page.getByRole('dialog').getByRole('button',{name:text.redesign.cancel,exact:true}).click();await expect(trigger).toBeFocused();}
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 }
});

test('selected condition uses the semantic tone without styling native option menus',async({page})=>{
 await setup(page,{state:{condition:'WORKING'}});await page.goto(`/inventory/assets/${id}`);
 await page.getByRole('button',{name:`${en.ui.changeCondition}: ${en.enums.WORKING}`}).click();
 const selection=page.getByRole('dialog',{name:en.ui.changeCondition}).getByLabel(en.ui.conditionTarget);
 await selection.selectOption('DEFECTIVE');await expect(selection).toHaveCSS('background-color','rgb(254, 243, 199)');
 await selection.selectOption('UNUSABLE');await expect(selection).toHaveCSS('background-color','rgb(254, 226, 226)');
 await selection.focus();await expect(selection).toBeFocused();
});

for(const [scope,role] of [['GLOBAL','ADMIN'],['TENANT','ADMIN'],['GLOBAL','USER'],['TENANT','USER']] as const)test(`${scope} ${role} Asset status authority and outstanding-loan guard`,async({page})=>{
 const f=await setup(page,{scope,role,state:{lifecycle:'DEPLOYED',condition:'WORKING',loanable:true}});await page.goto(`/inventory/assets/${id}`);
 for(const name of [`${en.ui.changeLifecycle}: ${en.enums.DEPLOYED}`,`${en.ui.changeCondition}: ${en.enums.WORKING}`,`${en.ui.changeLoanability}: ${en.fields.true}`])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 f.set({loanedTo:'Borrower'});await page.reload();await expect(page.getByRole('button',{name:`${en.ui.changeLoanability}: ${en.fields.true}`,exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:`${en.ui.changeLifecycle}: ${en.enums.DEPLOYED}`,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 await expect(page.getByRole('button',{name:`${en.ui.changeCondition}: ${en.enums.WORKING}`,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 expect(f.writes).toHaveLength(0);
});

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} Asset loanability confirmation, cancel, success and stale conflict`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language,state:{loanable:false}});await page.setViewportSize({width,height:900});await page.goto(`/inventory/assets/${id}`);
 const card=page.getByRole('heading',{name:text.fields.loanable,exact:true}).locator('..');
 const trigger=(value:string)=>card.getByRole('button',{name:`${text.ui.changeLoanability}: ${value}`,exact:true});
 await expect(trigger(text.fields.false)).toHaveText(text.fields.false);await expect(trigger(text.fields.false)).toHaveCSS('background-color','rgb(254, 226, 226)');await expect(trigger(text.fields.false)).toHaveClass(/min-h-10/);
 await trigger(text.fields.false).click();let dialog=page.getByRole('dialog',{name:text.ui.changeLoanability});await expect(dialog).toContainText(text.ui.notLoanable);await expect(dialog).toContainText(text.ui.loanable);expect(f.writes).toHaveLength(0);
 await dialog.getByRole('button',{name:text.redesign.cancel,exact:true}).click();expect(f.writes).toHaveLength(0);await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.fields.false}`})).toBeFocused();
 await trigger(text.fields.false).press('Space');dialog=page.getByRole('dialog',{name:text.ui.changeLoanability});await expect(dialog).toBeVisible();await dialog.getByRole('button',{name:text.redesign.cancel,exact:true}).click();await expect(trigger(text.fields.false)).toBeFocused();
 await trigger(text.fields.false).press('Enter');dialog=page.getByRole('dialog',{name:text.ui.changeLoanability});await dialog.getByRole('button',{name:text.redesign.confirm,exact:true}).click();await expect(dialog).toHaveCount(0);
 await expect(trigger(text.fields.true)).toBeVisible();await expect(trigger(text.fields.true)).toHaveCSS('background-color','rgb(220, 252, 231)');await expect(trigger(text.fields.true)).toBeFocused();
 expect(f.writes).toEqual([{path:`/api/v1/assets/${id}/loanability`,method:'PUT',body:{expectedVersion:3,loanable:true}}]);
 f.fail('CONCURRENT_OPERATION');await trigger(text.fields.true).press('Enter');dialog=page.getByRole('dialog',{name:text.ui.changeLoanability});await expect(dialog).toContainText(text.ui.notLoanable);await dialog.getByRole('button',{name:text.redesign.confirm,exact:true}).click();
 await expect(dialog.getByRole('alert')).toContainText(text.errors.CONCURRENT_OPERATION);await expect(dialog).toBeVisible();expect(f.writes[1].body).toEqual({expectedVersion:4,loanable:false});
 await dialog.getByRole('button',{name:text.redesign.cancel,exact:true}).click();expect(f.writes).toHaveLength(2);await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.fields.true}`,exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

for(const language of ['de','en'] as const)for(const width of [390,1440])for(const target of ['DEREGISTERED','DEFECTIVE','UNUSABLE'] as const)test(`${language}/${width} ${target} transition immediately disables loanability`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language,state:{lifecycle:'DEPLOYED',condition:'WORKING',loanable:true}});
 await page.setViewportSize({width,height:900});await page.goto(`/inventory/assets/${id}`);
 const release=f.holdDetail(),kind=target==='DEREGISTERED'?'Lifecycle':'Condition';
 const action=text.ui[`change${kind}`],before=text.enums[kind==='Lifecycle'?'DEPLOYED':'WORKING'];
 await page.getByRole('button',{name:`${action}: ${before}`}).click();const dialog=page.getByRole('dialog',{name:action});
 await dialog.getByLabel(text.ui[kind==='Lifecycle'?'lifecycleTarget':'conditionTarget']).selectOption(target);
 if(target==='DEREGISTERED')await dialog.getByLabel(text.ui.deregistrationReason).selectOption('LOST');
 await dialog.getByRole('button',{name:action,exact:true}).click();
 if(target==='DEREGISTERED')await page.getByRole('alertdialog').getByRole('button',{name:text.redesign.confirm,exact:true}).click();
 const card=page.getByRole('heading',{name:text.fields.loanable,exact:true}).locator('..');
 await expect(dialog).toHaveCount(0);await expect(card.getByText(text.fields.false,{exact:true})).toBeVisible();
 await expect(card.getByText(text.ui.loanabilityBlocked)).toBeVisible();
 await expect(card.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.fields.false}`})).toHaveCount(0);
 await expect(page.getByText(text.enums[target],{exact:true}).first()).toBeVisible();
 expect(f.writes.map(write=>write.path)).toEqual([`/api/v1/assets/${id}/${kind.toLowerCase()}-transitions`]);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 release();await expect(card.getByText(text.fields.false,{exact:true})).toBeVisible();
});

for(const language of ['de','en'] as const)test(`${language} repaired Asset remains not loanable until explicit keyboard change`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language,state:{lifecycle:'DEPLOYED',condition:'DEFECTIVE',loanable:false}});await page.goto(`/inventory/assets/${id}`);
 const card=page.getByRole('heading',{name:text.fields.loanable,exact:true}).locator('..');
 await expect(card.getByRole('button')).toHaveCount(0);await expect(card.getByText(text.ui.loanabilityBlocked)).toBeVisible();
 await page.getByRole('button',{name:`${text.ui.changeCondition}: ${text.enums.DEFECTIVE}`}).click();const dialog=page.getByRole('dialog',{name:text.ui.changeCondition});
 await dialog.getByLabel(text.ui.conditionTarget).selectOption('WORKING');await dialog.getByRole('button',{name:text.ui.changeCondition,exact:true}).click();
 const trigger=card.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.fields.false}`});
 await expect(trigger).toBeVisible();await expect(card.getByText(text.ui.loanabilityBlocked)).toHaveCount(0);
 expect(f.writes).toHaveLength(1);expect(f.writes[0].body).toEqual({expectedVersion:3,target:'WORKING'});
 await trigger.focus();await page.keyboard.press('Enter');const loanability=page.getByRole('dialog',{name:text.ui.changeLoanability});await expect(loanability).toBeVisible();
 await loanability.getByRole('button',{name:text.redesign.cancel,exact:true}).click();await expect(trigger).toBeFocused();expect(f.writes).toHaveLength(1);
});

for(const role of ['ADMIN','USER'] as const)test(`${role} blocked Asset keeps permission-aware read-only value`,async({page})=>{
 const f=await setup(page,{role,state:{lifecycle:'DEPLOYED',condition:'DEFECTIVE',loanable:false}});await page.goto(`/inventory/assets/${id}`);
 const card=page.getByRole('heading',{name:en.fields.loanable,exact:true}).locator('..');
 await expect(card.getByText(en.fields.false,{exact:true})).toBeVisible();await expect(card.getByText(en.ui.loanabilityBlocked)).toBeVisible();
 await expect(card.getByRole('button')).toHaveCount(0);expect(f.writes).toHaveLength(0);
});

test('authorized historical blocking Asset can still change Yes to No',async({page})=>{
 const f=await setup(page,{state:{lifecycle:'DEPLOYED',condition:'DEFECTIVE',loanable:true}});await page.goto(`/inventory/assets/${id}`);
 const card=page.getByRole('heading',{name:en.fields.loanable,exact:true}).locator('..');
 await card.getByRole('button',{name:`${en.ui.changeLoanability}: ${en.fields.true}`}).click();
 await page.getByRole('dialog',{name:en.ui.changeLoanability}).getByRole('button',{name:en.redesign.confirm}).click();
 await expect(card.getByText(en.fields.false,{exact:true})).toBeVisible();await expect(card.getByRole('button')).toHaveCount(0);
 expect(f.writes).toEqual([{path:`/api/v1/assets/${id}/loanability`,method:'PUT',body:{expectedVersion:3,loanable:false}}]);
});

test('Asset status entry points retain transition payload and deregistration confirmation',async({page})=>{
 const f=await setup(page,{state:{lifecycle:'REGISTERED',condition:'UNKNOWN'}});await page.goto(`/inventory/assets/${id}`);
 await page.getByRole('button',{name:`${en.ui.changeCondition}: ${en.enums.UNKNOWN}`}).click();let dialog=page.getByRole('dialog',{name:en.ui.changeCondition});
 await dialog.getByLabel(en.ui.conditionTarget).selectOption('WORKING');await dialog.getByRole('button',{name:en.ui.changeCondition,exact:true}).click();
 await expect(page.getByRole('button',{name:`${en.ui.changeCondition}: ${en.enums.WORKING}`})).toBeVisible();expect(f.writes[0]).toEqual({path:`/api/v1/assets/${id}/condition-transitions`,method:'POST',body:{expectedVersion:3,target:'WORKING'}});
 await page.getByRole('button',{name:`${en.ui.changeLifecycle}: ${en.enums.REGISTERED}`}).click();dialog=page.getByRole('dialog',{name:en.ui.changeLifecycle});
 await dialog.getByLabel(en.ui.lifecycleTarget).selectOption('DEREGISTERED');await dialog.getByLabel(en.ui.deregistrationReason).selectOption('LOST');await dialog.getByRole('button',{name:en.ui.changeLifecycle,exact:true}).click();
 await expect(page.getByRole('alertdialog')).toBeVisible();expect(f.writes).toHaveLength(1);await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel,exact:true}).click();expect(f.writes).toHaveLength(1);
 await dialog.getByRole('button',{name:en.ui.changeLifecycle,exact:true}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();
 await expect(page.getByRole('button',{name:`${en.ui.changeLifecycle}: ${en.enums.DEREGISTERED}`})).toHaveCount(0);expect(f.writes[1]).toEqual({path:`/api/v1/assets/${id}/lifecycle-transitions`,method:'POST',body:{expectedVersion:4,target:'DEREGISTERED',reason:'LOST',note:null}});
});

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} Asset action cleanup keeps guarded paths and Catalog confirmation`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language});await page.setViewportSize({width,height:900});
 const nextCategory='88888888-8888-8888-8888-888888888888',nextManufacturer='77777777-7777-7777-7777-777777777777',nextProduct='99999999-9999-9999-9999-999999999999';
 await page.route('**/api/v1/tenants/**/catalog/**',route=>{const path=new URL(route.request().url()).pathname;
  const [id,name]=path.endsWith('/categories')?[nextCategory,'Next Category']:path.endsWith('/manufacturers')?[nextManufacturer,'Next Manufacturer']:[nextProduct,'Next Product'];
  return route.fulfill({json:{items:[{id,name,tenantId:tenant,tenant:{id:tenant,name:'Fixture Tenant'}}],page:0,size:20,totalElements:1}});
 });
 await page.goto(`/inventory/assets/${id}`);
 const actions=page.getByRole('button',{name:text.ui.editCategory,exact:true}).locator('..');
 const labels=[text.ui.editCategory,text.ui.reassignCatalog,text.ui.changeLifecycle,text.ui.changeCondition,text.ui.exitDetail];
 await expect(actions.getByRole('button')).toHaveText(labels);
 for(const key of ['changeLoanability','changeLocation','changeOwner'] as const)await expect(page.getByRole('button',{name:text.ui[key],exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.fields.false}`,exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:`${text.ui.changeLocation}: Fixture Location`,exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:`${text.ui.changeOwner}: —`,exact:true})).toBeVisible();
 await page.getByRole('tab',{name:text.redesign.history}).click();await expect(actions.getByRole('button')).toHaveText(labels);await page.getByRole('tab',{name:text.redesign.overview}).click();
 const catalog=page.getByRole('button',{name:text.ui.reassignCatalog,exact:true});await catalog.focus();await page.keyboard.press('Enter');
 let dialog=page.getByRole('dialog',{name:text.ui.reassignCatalog});await expect(dialog.getByRole('button',{name:text.redesign.confirm,exact:true})).toBeVisible();
 await expect(dialog.getByLabel(text.ui.catalogSelection)).toHaveValue('Direct');
 await dialog.getByRole('combobox',{name:text.ui.category}).click();await dialog.getByRole('option',{name:/Next Category/}).click();
 await dialog.getByRole('combobox',{name:text.ui.manufacturer}).click();await dialog.getByRole('option',{name:/Next Manufacturer/}).click();
 await dialog.getByRole('button',{name:text.redesign.confirm,exact:true}).click();await expect(dialog).toHaveCount(0);
 expect(f.writes[0]).toEqual({path:`/api/v1/assets/${id}/catalog`,method:'PUT',body:{expectedVersion:3,categoryId:nextCategory,manufacturerId:nextManufacturer}});
 await catalog.focus();await page.keyboard.press('Enter');dialog=page.getByRole('dialog',{name:text.ui.reassignCatalog});
 await dialog.getByLabel(text.ui.catalogSelection).selectOption('Product');await expect(dialog).not.toContainText(text.ui.productDependencyHelp);
 await dialog.getByRole('combobox',{name:text.ui.product,exact:true}).click();await dialog.getByRole('option',{name:/Next Product/}).click();
 await dialog.getByRole('button',{name:text.redesign.confirm,exact:true}).focus();await page.keyboard.press('Enter');await expect(dialog).toHaveCount(0);await expect(catalog).toBeFocused();
 expect(f.writes[1]).toEqual({path:`/api/v1/assets/${id}/catalog`,method:'PUT',body:{expectedVersion:4,productId:nextProduct}});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

for(const loanedTo of [null,'Borrower'])for(const role of ['ADMIN','USER'] as const)test(`${role} Asset action cleanup preserves ${loanedTo?'return':'loan'} authorization`,async({page})=>{
 const f=await setup(page,{role,state:{lifecycle:'DEPLOYED',condition:'WORKING',loanable:true,loanedTo}});await page.goto(`/inventory/assets/${id}`);
 const bottom=page.getByRole('button',{name:en.ui.exitDetail,exact:true}).locator('..');
 const expected=role==='ADMIN'?[en.ui.editCategory,en.ui.reassignCatalog,en.ui.changeLifecycle,en.ui.changeCondition,loanedTo?en.ui.returnAsset:en.ui.loanAsset,en.ui.exitDetail]:[en.ui.exitDetail];
 await expect(bottom.getByRole('button')).toHaveText(expected);
 for(const key of ['changeLoanability','changeLocation','changeOwner'] as const)await expect(page.getByRole('button',{name:en.ui[key],exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:`${en.ui.changeLoanability}: ${en.fields.true}`,exact:true})).toHaveCount(role==='ADMIN'&&!loanedTo?1:0);
 expect(f.writes).toHaveLength(0);
});
