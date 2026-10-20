import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
import {assetDetailFixture,tenantFixture} from './backend-fixtures';

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
type State=ReturnType<typeof assetDetailFixture>;
async function setup(page:Page,{language='en',scope='GLOBAL',role='ADMIN',state={}}:{language?:'en'|'de';scope?:'GLOBAL'|'TENANT';role?:'ADMIN'|'USER';state?:Partial<State>}={}){
 let asset=assetDetailFixture({id,tenantId:tenant,version:3,...state}),failure:string|null=null;
 const writes:{path:string;method:string;body:any}[]=[];
 await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname,method=route.request().method();
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope,role,status:'ACTIVE',tenantId:scope==='TENANT'?tenant:null,displayName:'Fixture'}}});
  if(method!=='GET'){const body=route.request().postDataJSON();writes.push({path,method,body});if(failure)return route.fulfill({status:409,json:{code:failure}});asset={...asset,...(path.endsWith('/loanability')?{loanable:body.loanable}:path.endsWith('/lifecycle-transitions')?{lifecycle:body.target}:path.endsWith('/condition-transitions')?{condition:body.target}:{}),version:asset.version+1};return route.fulfill({json:asset});}
  if(path==='/api/v1/tenants')return route.fulfill({json:[tenantFixture(tenant,'Fixture Tenant')]});
  if(path===`/api/v1/tenants/${tenant}`)return route.fulfill({json:tenantFixture(tenant,'Fixture Tenant')});
  if(path===`/api/v1/assets/${id}`)return route.fulfill({json:asset});
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return {writes,set:(next:Partial<State>)=>{asset={...asset,...next};},fail:(code:string|null)=>{failure=code;}};
}

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} Asset status colors and guarded entry points`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language});await page.setViewportSize({width,height:900});await page.goto(`/inventory/assets/${id}`);
 const states=[['lifecycle','REGISTERED',true,'sky'],['lifecycle','IN_STOCK',true,'slate'],['lifecycle','DEPLOYED',true,'emerald'],['lifecycle','DEREGISTERED',false,'stone'],['condition','UNKNOWN',true,'slate'],['condition','WORKING',true,'emerald'],['condition','DEFECTIVE',true,'amber'],['condition','UNUSABLE',false,'rose']] as const;
 for(const [kind,state,actionable,tone] of states){f.set({[kind]:state});await page.reload();
  const label=text.enums[state],action=kind==='lifecycle'?text.ui.changeLifecycle:text.ui.changeCondition;
  const indicator=page.getByRole('tabpanel',{name:text.redesign.overview}).getByText(label,{exact:true});await expect(indicator).toBeVisible();await expect(indicator).toHaveClass(new RegExp(`bg-${tone}-50`));
  const trigger=page.getByRole('button',{name:`${action}: ${label}`,exact:true});await expect(trigger).toHaveCount(actionable?1:0);
  if(actionable){await trigger.focus();await page.keyboard.press('Enter');await expect(page.getByRole('dialog',{name:action})).toBeVisible();await page.getByRole('dialog').getByRole('button',{name:text.redesign.cancel,exact:true}).click();await expect(trigger).toBeFocused();}
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 }
});

for(const [scope,role] of [['GLOBAL','ADMIN'],['TENANT','ADMIN'],['GLOBAL','USER'],['TENANT','USER']] as const)test(`${scope} ${role} Asset status authority and outstanding-loan guard`,async({page})=>{
 const f=await setup(page,{scope,role,state:{lifecycle:'DEPLOYED',condition:'WORKING',loanable:true}});await page.goto(`/inventory/assets/${id}`);
 for(const name of [`${en.ui.changeLifecycle}: ${en.enums.DEPLOYED}`,`${en.ui.changeCondition}: ${en.enums.WORKING}`,`${en.ui.changeLoanability}: ${en.ui.loanable}`])await expect(page.getByRole('button',{name,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 f.set({loanedTo:'Borrower'});await page.reload();await expect(page.getByRole('button',{name:`${en.ui.changeLoanability}: ${en.ui.loanable}`,exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:`${en.ui.changeLifecycle}: ${en.enums.DEPLOYED}`,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 await expect(page.getByRole('button',{name:`${en.ui.changeCondition}: ${en.enums.WORKING}`,exact:true})).toHaveCount(role==='ADMIN'?1:0);
 expect(f.writes).toHaveLength(0);
});

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} Asset loanability confirmation, cancel, success and stale conflict`,async({page})=>{
 const text=language==='de'?de:en,f=await setup(page,{language,state:{loanable:false}});await page.setViewportSize({width,height:900});await page.goto(`/inventory/assets/${id}`);
 const open=async(value:string)=>{const trigger=page.getByRole('button',{name:`${text.ui.changeLoanability}: ${value}`,exact:true});await trigger.focus();await page.keyboard.press('Enter');return page.getByRole('dialog',{name:text.ui.changeLoanability});};
 let dialog=await open(text.ui.notLoanable);await expect(dialog).toContainText(text.ui.notLoanable);await expect(dialog).toContainText(text.ui.loanable);expect(f.writes).toHaveLength(0);
 await dialog.getByRole('button',{name:text.redesign.cancel,exact:true}).click();expect(f.writes).toHaveLength(0);await expect(page.getByRole('dialog')).toHaveCount(0);await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.ui.notLoanable}`})).toBeFocused();
 dialog=await open(text.ui.notLoanable);await dialog.getByRole('button',{name:text.redesign.confirm,exact:true}).click();await expect(dialog).toHaveCount(0);
 await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.ui.loanable}`,exact:true})).toBeVisible();
 expect(f.writes).toEqual([{path:`/api/v1/assets/${id}/loanability`,method:'PUT',body:{expectedVersion:3,loanable:true}}]);
 f.fail('CONCURRENT_OPERATION');dialog=await open(text.ui.loanable);await expect(dialog).toContainText(text.ui.notLoanable);await dialog.getByRole('button',{name:text.redesign.confirm,exact:true}).click();
 await expect(dialog.getByRole('alert')).toContainText(text.errors.CONCURRENT_OPERATION);await expect(dialog).toBeVisible();expect(f.writes[1].body).toEqual({expectedVersion:4,loanable:false});
 await dialog.getByRole('button',{name:text.redesign.cancel,exact:true}).click();expect(f.writes).toHaveLength(2);await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.ui.loanable}`,exact:true})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
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
 await expect(page.getByRole('button',{name:`${text.ui.changeLoanability}: ${text.ui.notLoanable}`,exact:true})).toBeVisible();
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
 await expect(page.getByRole('button',{name:`${en.ui.changeLoanability}: ${en.ui.loanable}`,exact:true})).toHaveCount(role==='ADMIN'&&!loanedTo?1:0);
 expect(f.writes).toHaveLength(0);
});
