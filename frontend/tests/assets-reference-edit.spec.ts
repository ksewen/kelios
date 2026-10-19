import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
import {assetDetailFixture,tenantFixture} from './backend-fixtures';

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const location='cccccccc-cccc-cccc-cccc-cccccccccccc',nextLocation='dddddddd-dddd-dddd-dddd-dddddddddddd';
const owner='eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',nextOwner='ffffffff-ffff-ffff-ffff-ffffffffffff';
const place=(id:string,name:string)=>({id,name,tenantId:tenant,tenant:{id:tenant,name:'Fixture Tenant'}});
async function setup(page:Page,{lang='en',scope='GLOBAL',role='ADMIN'}:{lang?:'en'|'de';scope?:'GLOBAL'|'TENANT';role?:'ADMIN'|'USER'}={}){
 let asset=assetDetailFixture({id,tenantId:tenant,locationId:location,location:place(location,'Current Room'),ownerId:owner,owner:place(owner,'Current Owner'),version:7});
 const writes:{path:string;body:any}[]=[];let conflict=false;
 await page.addInitScript(language=>localStorage.setItem('kelios.ui.language',language),lang);
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname,method=route.request().method();
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope,role,status:'ACTIVE',tenantId:scope==='TENANT'?tenant:null,displayName:'Fixture'}}});
  if(method!=='GET'){
   const body=route.request().postDataJSON();writes.push({path,body});
   if(conflict)return route.fulfill({status:409,json:{code:'VERSION_CONFLICT'}});
   if(path.endsWith('/location')&&body.locationId===asset.locationId||path.endsWith('/owner')&&body.ownerId===asset.ownerId)return route.fulfill({status:400,json:{code:'INVALID_INPUT'}});
   asset={...asset,...(path.endsWith('/location')?{locationId:body.locationId,location:place(body.locationId,'Next Room')}:{ownerId:body.ownerId,owner:body.ownerId?place(body.ownerId,'Next Owner'):null}),version:asset.version+1};return route.fulfill({json:asset});
  }
  if(path==='/api/v1/tenants')return route.fulfill({json:[tenantFixture(tenant,'Fixture Tenant')]});
  if(path===`/api/v1/assets/${id}`)return route.fulfill({json:asset});
  if(path===`/api/v1/assets/${id}/events`)return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
  if(path.endsWith('/locations'))return route.fulfill({json:{items:[place(location,'Current Room'),place(nextLocation,'Next Room')],page:0,size:20,totalElements:2}});
  if(path.endsWith('/owners'))return route.fulfill({json:{items:[place(owner,'Current Owner'),place(nextOwner,'Next Owner')],page:0,size:20,totalElements:2}});
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return {writes,conflict:()=>{conflict=true;}};
}

for(const lang of ['en','de'] as const)for(const width of [1440,390])for(const kind of ['location','owner'] as const)test(`${lang}/${width} Asset ${kind} entry keeps picker closed and writes only a changed UUID`,async({page})=>{
 const t=lang==='en'?en:de,f=await setup(page,{lang});await page.setViewportSize({width,height:900});await page.goto(`/inventory/assets/${id}`);
 const action=t.ui[kind==='location'?'changeLocation':'changeOwner'],field=t.ui[kind];
 const entry=page.getByRole('button',{name:new RegExp(`^${action}: Current`)});await expect(entry).toBeVisible();await entry.focus();await page.keyboard.press('Enter');
 const dialog=page.getByRole('dialog',{name:action}),picker=dialog.getByRole('combobox',{name:field}),save=dialog.getByRole('button',{name:action,exact:true});
 await expect(picker).toHaveValue(kind==='location'?'Current Room':'Current Owner');await expect(picker).toBeFocused();await expect(picker).toHaveAttribute('aria-expanded','false');await expect(save).toBeDisabled();expect(f.writes).toHaveLength(0);
 await picker.click();await expect(picker).toHaveAttribute('aria-expanded','true');await picker.press('Escape');await expect(picker).toHaveAttribute('aria-expanded','false');await expect(picker).toBeFocused();
 await picker.press('ArrowDown');await expect(picker).toHaveAttribute('aria-expanded','true');await picker.press('Escape');
 await picker.evaluate(element=>(window as any).assetPicker=element);
 await picker.fill('Next');await expect(save).toBeDisabled();await expect(picker).toBeFocused();
 await dialog.getByRole('option',{name:new RegExp(kind==='location'?'Next Room':'Next Owner')}).click();await expect(save).toBeEnabled();
 expect(await picker.evaluate(element=>element===(window as any).assetPicker)).toBe(true);
 await save.focus();await save.press('Enter');await expect(dialog).toHaveCount(0);
 await expect(page.getByRole('button',{name:new RegExp(`^${action}: Next`)})).toBeVisible();await expect(page.getByRole('button',{name:new RegExp(`^${action}: Next`)})).toBeFocused();
 expect(f.writes).toEqual([{path:`/api/v1/assets/${id}/${kind}`,body:{expectedVersion:7,[kind==='location'?'locationId':'ownerId']:kind==='location'?nextLocation:nextOwner}}]);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('Owner clear, required Location, and stale request preserve command semantics',async({page})=>{
 const f=await setup(page);await page.goto(`/inventory/assets/${id}`);
 await page.getByRole('button',{name:/^Change Location: Current Room/}).click();let dialog=page.getByRole('dialog',{name:en.ui.changeLocation});
 await dialog.getByRole('button',{name:`Clear ${en.ui.location}`}).click();await expect(dialog.getByRole('button',{name:en.ui.changeLocation,exact:true})).toBeDisabled();
 await dialog.getByRole('button',{name:en.redesign.cancel,exact:true}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect(dialog).toHaveCount(0);expect(f.writes).toHaveLength(0);
 await page.getByRole('button',{name:/^Change Owner: Current Owner/}).click();dialog=page.getByRole('dialog',{name:en.ui.changeOwner});
 await dialog.getByRole('button',{name:`Clear ${en.ui.owner}`}).click();await expect(dialog.getByRole('button',{name:en.ui.changeOwner,exact:true})).toBeEnabled();
 await dialog.getByRole('button',{name:en.ui.changeOwner,exact:true}).click();await expect(dialog).toHaveCount(0);
 expect(f.writes).toEqual([{path:`/api/v1/assets/${id}/owner`,body:{expectedVersion:7,ownerId:null}}]);
 await expect(page.getByRole('button',{name:/^Change Owner: —/})).toBeVisible();
 await page.getByRole('button',{name:/^Change Location: Current Room/}).click();dialog=page.getByRole('dialog',{name:en.ui.changeLocation});
 await dialog.getByRole('combobox',{name:en.ui.location}).click();await dialog.getByRole('option',{name:/Next Room/}).click();f.conflict();
 await dialog.getByRole('button',{name:en.ui.changeLocation,exact:true}).click();await expect(dialog.getByRole('alert')).toBeVisible();
 await expect(dialog.getByRole('combobox',{name:en.ui.location})).toHaveValue('Next Room');expect(f.writes.at(-1)?.body).toEqual({expectedVersion:8,locationId:nextLocation});
});

for(const scope of ['GLOBAL','TENANT'] as const)test(`${scope} non-admin sees read-only Location and Owner`,async({page})=>{
 const f=await setup(page,{scope,role:'USER'});await page.goto(`/inventory/assets/${id}`);
 await expect(page.getByText('Current Room',{exact:true})).toBeVisible();await expect(page.getByText('Current Owner',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:/^Change (Location|Owner):/})).toHaveCount(0);expect(f.writes).toHaveLength(0);
});

test('TENANT administrator can change an own Asset reference through the displayed value',async({page})=>{
 const f=await setup(page,{scope:'TENANT'});await page.goto(`/inventory/assets/${id}`);
 await page.getByRole('button',{name:/^Change Location: Current Room/}).click();const dialog=page.getByRole('dialog',{name:en.ui.changeLocation});
 await dialog.getByRole('combobox',{name:en.ui.location}).click();await dialog.getByRole('option',{name:/Next Room/}).click();
 await dialog.getByRole('button',{name:en.ui.changeLocation,exact:true}).click();await expect(dialog).toHaveCount(0);
 await expect(page.getByRole('button',{name:/^Change Location: Next Room/})).toBeVisible();
 expect(f.writes).toEqual([{path:`/api/v1/assets/${id}/location`,body:{expectedVersion:7,locationId:nextLocation}}]);
});
