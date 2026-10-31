import {test,expect} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const other='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const file={name:'legacy.csv',mimeType:'text/csv',buffer:Buffer.from(`Mandant;Kennung;Modell;Raum\n${tenant};ä-1;Gerät;Room\n${other};ä-2;Gerät;Missing`,'utf8')};
const parsed={sha256:'fixture-hash',encoding:'UTF-8',delimiter:';',headers:['Mandant','Kennung','Modell','Raum'],totalRows:2,rows:[{rowNumber:2,lineNumber:2,values:{Mandant:tenant,Kennung:'ä-1',Modell:'Gerät',Raum:'Room'}},{rowNumber:3,lineNumber:3,values:{Mandant:other,Kennung:'ä-2',Modell:'Gerät',Raum:'Missing'}}]};
const preview={...parsed,ready:1,warnings:0,errors:1,rows:[{rowNumber:2,lineNumber:2,status:'READY',tenantId:tenant,tenantName:'Workspace',source:parsed.rows[0].values,normalized:{localIdentifier:'ä-1'},references:{},issues:[]},{rowNumber:3,lineNumber:3,status:'ERROR',tenantId:other,tenantName:'Other',source:parsed.rows[1].values,normalized:{localIdentifier:'ä-2'},references:{},issues:['Missing Location']}],referenceResolution:[]};

async function setup(page:import('@playwright/test').Page,role:'ADMIN'|'USER'='ADMIN',scope:'GLOBAL'|'TENANT'='GLOBAL'){
 const requests:{path:string;body:string|null}[]=[];
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname;
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:tenant,scope,tenantId:scope==='TENANT'?tenant:null,role,status:'ACTIVE',displayName:'Fixture'}}});
  if(path==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'Workspace'},{id:other,name:'Other'}]});
  if(path.startsWith('/api/v1/asset-csv/')){requests.push({path,body:route.request().postData()});if(path.endsWith('/parse-preview'))return route.fulfill({json:parsed});if(path.endsWith('/preview'))return route.fulfill({json:preview});return route.fulfill({json:{successful:1,failed:0,skipped:1,rows:[{rowNumber:2,lineNumber:2,status:'SUCCESS',assetId:tenant,assetTag:'KEL-2026-000001',reason:null},{rowNumber:3,lineNumber:3,status:'SKIPPED',assetId:null,assetTag:null,reason:'Preview error'}],failureCsv:'\ufeffKennung;KELIOS import error\nä-2;Preview error'}});}
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });return requests;
}

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} CSV import mapping, confirmation and partial result`,async({page})=>{
 const text=language==='de'?de:en;await page.setViewportSize({width,height:900});await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);const requests=await setup(page);
 await page.goto('/assets/import');await expect(page.getByRole('heading',{name:text.csv.importTitle})).toBeVisible();await expect(page.getByRole('combobox',{name:text.csv.tenant})).toHaveCount(0);
 await expect(page.getByRole('checkbox',{name:text.csv.headerPresent})).toBeChecked();
 await page.getByLabel(text.csv.file).setInputFiles(file);await page.getByRole('button',{name:text.csv.parseFile}).click();await expect(page.getByText('ä-1',{exact:true})).toBeVisible();
 const mapping=page.getByRole('heading',{name:text.csv.columnMapping}).locator('..');await expect(mapping.getByText(`${text.csv.sampleValues}: ä-1 · ä-2`)).toBeVisible();await page.getByRole('button',{name:text.csv.showPreview}).click();await expect(mapping.getByRole('alert')).toContainText(text.csv.tenant);await expect(mapping.getByRole('alert')).toBeFocused();expect(requests.filter(item=>item.path.endsWith('/preview'))).toHaveLength(0);
 await mapping.locator('select').nth(0).focus();await expect(mapping.locator('select').nth(0)).toBeFocused();await page.keyboard.press('Tab');await expect(mapping.locator('select').nth(1)).toBeFocused();
 await mapping.locator('select').nth(0).selectOption('tenantId');
 await mapping.locator('select').nth(1).selectOption('localIdentifier');await mapping.locator('select').nth(2).selectOption('productName');await mapping.locator('select').nth(3).selectOption('locationName');
 await expect(mapping.locator('select').nth(0).locator('option[value="tenantName"]')).toBeEnabled();await expect(mapping.locator('select').nth(1).locator('option[value="tenantName"]')).toHaveAttribute('disabled','');await expect(mapping.locator('select').nth(0).locator('option[value="productId"]')).toHaveAttribute('disabled','');await expect(mapping.locator('select').nth(0).locator('option[value="categoryName"]')).toHaveAttribute('disabled','');
 await page.getByRole('button',{name:text.csv.showPreview}).click();await expect(page.getByRole('heading',{name:text.csv.previewTitle})).toBeVisible();
 await expect(page.getByRole('cell',{name:`Workspace · ${tenant}`})).toBeVisible();await expect(page.getByRole('cell',{name:`Other · ${other}`})).toBeVisible();
 await expect(page.getByRole('checkbox',{name:text.csv.selectNumber.replace('{{number}}','3')})).toBeDisabled();await expect(page.getByRole('checkbox',{name:text.csv.selectNumber.replace('{{number}}','2')})).toBeChecked();
 await page.getByRole('button',{name:text.csv.importSelected.replace('{{count}}','1')}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:text.redesign.cancel}).click();await expect(page.getByRole('button',{name:text.csv.importSelected.replace('{{count}}','1')})).toBeFocused();expect(requests.filter(item=>item.path.endsWith('/execute'))).toHaveLength(0);
 await page.getByRole('button',{name:text.csv.importSelected.replace('{{count}}','1')}).focus();await page.keyboard.press('Enter');await page.getByRole('alertdialog').getByRole('button',{name:text.redesign.confirm,exact:true}).click();await expect(page.getByRole('heading',{name:text.csv.resultTitle})).toBeVisible();expect(requests.filter(item=>item.path.endsWith('/execute'))).toHaveLength(1);const download=page.waitForEvent('download');await page.getByRole('button',{name:text.csv.downloadFailures}).click();expect((await download).suggestedFilename()).toBe('kelios-import-errors.csv');
 expect(requests.find(item=>item.path.endsWith('/preview'))?.body).toContain('"tenantId":"Mandant"');expect(requests.find(item=>item.path.endsWith('/preview'))?.body).not.toContain(`"tenantId":"${tenant}"`);expect(requests.find(item=>item.path.endsWith('/execute'))?.body).toContain('selectedRows');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} headerless mapping reparses and includes record one`,async({page})=>{
 const text=language==='de'?de:en;await page.setViewportSize({width,height:900});await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);
 await setup(page);
 const noHeaders={...parsed,headers:['column1','column2','column3','column4'],rows:[{rowNumber:1,lineNumber:1,values:{column1:tenant,column2:'ä-1',column3:'Gerät',column4:'Room'}}],totalRows:1};
 const seen:{url:string;body:string|null}[]=[];
 await page.route('**/api/v1/asset-csv/parse-preview?*',route=>{seen.push({url:route.request().url(),body:null});return route.fulfill({json:new URL(route.request().url()).searchParams.get('headerPresent')==='false'?noHeaders:parsed});});
 await page.route('**/api/v1/asset-csv/preview',route=>{seen.push({url:route.request().url(),body:route.request().postData()});return route.fulfill({json:{...preview,...noHeaders,ready:1,errors:0,rows:[{...preview.rows[0],rowNumber:1,lineNumber:1,source:noHeaders.rows[0].values}],referenceResolution:[]}});});
 await page.goto('/assets/import');await page.getByLabel(text.csv.file).setInputFiles({name:'old.csv',mimeType:'text/csv',buffer:Buffer.from(`${tenant};ä-1;Gerät;Room\n`)});
 await page.getByRole('checkbox',{name:text.csv.headerPresent}).uncheck();
 const mapping=page.getByRole('heading',{name:text.csv.columnMapping}).locator('..');await expect(mapping.getByText(text.csv.positionalColumn.replace('{{number}}','1'))).toBeVisible();
 await expect(mapping.getByText(`${text.csv.sampleValues}: ä-1`)).toBeVisible();await expect(page.getByRole('rowheader',{name:'1'})).toBeVisible();
 await mapping.locator('select').nth(0).selectOption('tenantId');await mapping.locator('select').nth(1).selectOption('localIdentifier');await mapping.locator('select').nth(2).selectOption('productName');await mapping.locator('select').nth(3).selectOption('locationName');
 await expect(mapping.getByRole('status')).toHaveText(text.csv.mappingComplete);await page.getByRole('button',{name:text.csv.showPreview}).click();
 await expect(page.getByRole('checkbox',{name:text.csv.selectNumber.replace('{{number}}','1')})).toBeChecked();
 expect(seen.find(item=>item.body?.includes('"headerPresent":false'))?.body).toContain('"tenantId":"column1"');
 await page.getByRole('checkbox',{name:text.csv.headerPresent}).check();await expect(mapping.locator('label[for="csv-source-0"]')).toHaveText('Mandant');
 await expect(page.getByRole('heading',{name:text.csv.previewTitle})).toHaveCount(0);
 await expect(mapping.locator('select').first()).toHaveValue('');
 expect(seen.filter(item=>item.url.includes('/parse-preview?'))).toHaveLength(2);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('import is hidden from GLOBAL USER and route is unavailable',async({page})=>{await setup(page,'USER');await page.goto('/assets');await expect(page.getByRole('button',{name:en.csv.importTitle})).toHaveCount(0);await page.goto('/assets/import');await expect(page.getByRole('heading',{name:en.csv.importTitle})).toHaveCount(0);});
test('TENANT ADMIN cannot open the import route',async({page})=>{await setup(page,'ADMIN','TENANT');await page.goto(`/tenants/${tenant}/assets`);await expect(page.getByRole('button',{name:en.csv.importTitle})).toHaveCount(0);await page.goto(`/tenants/${tenant}/assets/import`);await expect(page.getByRole('heading',{name:en.csv.importTitle})).toHaveCount(0);});
test('top-level Tenant switch asks before abandoning an in-progress import',async({page})=>{await setup(page);await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.ui.tenantContext}).click();await page.getByRole('menuitem',{name:'Workspace'}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();await expect(page).toHaveURL(/assets\/import$/);await page.getByRole('button',{name:en.ui.tenantContext}).click();await page.getByRole('menuitem',{name:'Workspace'}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect(page).toHaveURL(new RegExp(`/tenants/${tenant}/dashboard$`));});

test('encoding and delimiter changes invalidate raw preview; leaving a draft asks before discarding',async({page})=>{
 const requests=await setup(page);await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('combobox',{name:en.csv.encoding}).selectOption('WINDOWS-1252');await page.getByRole('combobox',{name:en.csv.delimiter}).selectOption('COMMA');
 await page.getByRole('button',{name:en.csv.parseFile}).click();await expect(page.getByText('ä-1',{exact:true})).toBeVisible();expect(requests.find(item=>item.path.endsWith('/parse-preview'))?.path).toBe('/api/v1/asset-csv/parse-preview');
 await page.getByRole('combobox',{name:en.csv.delimiter}).selectOption('SEMICOLON');await expect(page.getByText('ä-1',{exact:true})).toHaveCount(0);await page.getByRole('navigation',{name:en.redesign.breadcrumb}).getByRole('link',{name:en.ui.assets}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();await expect(page).toHaveURL(/assets\/import$/);
 await page.getByRole('navigation',{name:en.redesign.breadcrumb}).getByRole('link',{name:en.ui.assets}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect(page).toHaveURL(/\/assets$/);
});

test('ambiguous Name reference requires explicit choice and a fresh preview',async({page})=>{
 await setup(page);let previews=0;await page.route('**/api/v1/asset-csv/preview',route=>{previews++;const chosen=route.request().postData()?.includes(`\"selectedId\":\"${tenant}\"`);return route.fulfill({json:{...preview,ready:chosen?1:0,errors:chosen?0:1,rows:[{...preview.rows[0],status:chosen?'READY':'ERROR',issues:chosen?[]:['product ambiguous: Gerät']}],referenceResolution:[{target:'productName',scopeTenantId:tenant,raw:'Gerät',result:{status:chosen?'CHOSEN':'AMBIGUOUS',id:chosen?tenant:null,candidates:[{id:tenant,name:'Gerät',tenantId:null}],more:false}}]}});});
 await page.route(`**/api/v1/tenants/${tenant}/catalog/products?*`,route=>route.fulfill({json:{items:[{id:tenant,name:'Gerät',tenant:null}],page:0,size:20,totalElements:1}}));
 await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.csv.parseFile}).click();const mapping=page.getByRole('heading',{name:en.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('tenantId');await mapping.locator('select').nth(1).selectOption('localIdentifier');await mapping.locator('select').nth(2).selectOption('productName');await mapping.locator('select').nth(3).selectOption('locationId');await page.getByRole('button',{name:en.csv.showPreview}).click();
 await expect(page.getByText(en.csv.resolutionAMBIGUOUS)).toBeVisible();const choose=page.getByRole('combobox',{name:en.csv.chooseExisting});await choose.fill('Gerät');await page.getByRole('listbox').getByRole('option').first().click();await expect(page.getByText(en.csv.revalidateNeeded)).toBeVisible();await page.getByRole('button',{name:en.csv.revalidate}).click();await expect(page.getByRole('checkbox',{name:en.csv.selectNumber.replace('{{number}}','2')})).toBeChecked();expect(previews).toBe(2);
});

test('ambiguous Tenant Name needs an explicit existing-Tenant choice',async({page})=>{
 await setup(page);await page.route('**/api/v1/tenants',route=>route.fulfill({json:[{id:tenant,name:'Shared'},{id:other,name:'Shared'}]}));
 const named={...parsed,rows:[{...parsed.rows[0],values:{...parsed.rows[0].values,Mandant:'Shared'}}]};
 await page.route('**/api/v1/asset-csv/parse-preview?*',route=>route.fulfill({json:named}));
 let previews=0;await page.route('**/api/v1/asset-csv/preview',route=>{
  previews++;const chosen=route.request().postData()?.includes(`\"selectedId\":\"${other}\"`);
  return route.fulfill({json:{...preview,rows:[{...preview.rows[0],tenantId:chosen?other:null,tenantName:chosen?'Shared':null,status:chosen?'READY':'ERROR',issues:chosen?[]:['Tenant ambiguous: Shared']}],ready:chosen?1:0,errors:chosen?0:1,referenceResolution:[{target:'tenantName',scopeTenantId:null,raw:'Shared',result:{status:chosen?'CHOSEN':'AMBIGUOUS',id:chosen?other:null,candidates:[{id:tenant,name:'Shared',tenantId:null},{id:other,name:'Shared',tenantId:null}],more:false}}]}});
 });
 await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.csv.parseFile}).click();
 const mapping=page.getByRole('heading',{name:en.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('tenantName');await mapping.locator('select').nth(1).selectOption('localIdentifier');await mapping.locator('select').nth(2).selectOption('productId');await mapping.locator('select').nth(3).selectOption('locationId');
 await page.getByRole('button',{name:en.csv.showPreview}).click();await expect(page.getByText(en.csv.resolutionAMBIGUOUS)).toBeVisible();await expect(page.getByText(en.csv.tenantMustExist)).toHaveCount(0);
 const chooser=page.getByRole('combobox',{name:en.csv.chooseTenant});await chooser.selectOption(other);await expect(page.getByText(en.csv.revalidateNeeded)).toBeVisible();await page.getByRole('button',{name:en.csv.revalidate}).click();
 await expect(page.getByRole('cell',{name:`Shared · ${other}`})).toBeVisible();expect(previews).toBe(2);
});

test('unresolved Tenant cannot be created inside Import',async({page})=>{
 await setup(page);await page.route('**/api/v1/asset-csv/preview',route=>route.fulfill({json:{...preview,ready:0,errors:1,rows:[{...preview.rows[0],tenantId:null,tenantName:null,status:'ERROR',issues:['Tenant unresolved']}],referenceResolution:[{target:'tenantName',scopeTenantId:null,raw:'Missing',result:{status:'UNRESOLVED',id:null,candidates:[],more:false}}]}}));
 await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.csv.parseFile}).click();
 const mapping=page.getByRole('heading',{name:en.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('tenantName');await mapping.locator('select').nth(2).selectOption('productId');await mapping.locator('select').nth(3).selectOption('locationId');await page.getByRole('button',{name:en.csv.showPreview}).click();
 await expect(page.getByText(en.csv.tenantMustExist)).toBeVisible();await expect(page.getByRole('link',{name:/Create Tenant/i})).toHaveCount(0);await expect(page.getByRole('checkbox',{name:en.csv.selectNumber.replace('{{number}}','2')})).toBeDisabled();
});

test('direct Catalog mapping reports both conditional requirements before Preview',async({page})=>{
 const requests=await setup(page);await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.csv.parseFile}).click();
 const mapping=page.getByRole('heading',{name:en.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('tenantId');await mapping.locator('select').nth(3).selectOption('locationName');await page.getByRole('button',{name:en.csv.showPreview}).click();
 await expect(mapping.getByRole('alert')).toContainText(en.ui.category);await expect(mapping.getByRole('alert')).toContainText(en.ui.manufacturer);expect(requests.filter(item=>item.path.endsWith('/preview'))).toHaveLength(0);
 await mapping.locator('select').nth(1).selectOption('categoryName');await mapping.locator('select').nth(2).selectOption('manufacturerName');await expect(mapping.getByRole('alert')).toHaveCount(0);await page.getByRole('button',{name:en.csv.showPreview}).click();expect(requests.filter(item=>item.path.endsWith('/preview'))).toHaveLength(1);
});

test('same reference Name remains isolated by row Tenant and creation uses that Tenant',async({page})=>{
 await setup(page);const seen:string[]=[];
 await page.route('**/api/v1/asset-csv/preview',route=>{
  seen.push(route.request().postData()??'');const chosen=seen.at(-1)?.includes(`\"scopeTenantId\":\"${tenant}\"`);
  return route.fulfill({json:{...preview,ready:chosen?1:0,errors:chosen?1:2,rows:[{...preview.rows[0],status:chosen?'READY':'ERROR',issues:chosen?[]:['location ambiguous: Room']},{...preview.rows[1],status:'ERROR',issues:['location unresolved: Room']}],referenceResolution:[
   {target:'locationName',scopeTenantId:tenant,raw:'Room',result:{status:chosen?'CHOSEN':'AMBIGUOUS',id:chosen?tenant:null,candidates:[{id:tenant,name:'Room',tenantId:tenant}],more:false}},
   {target:'locationName',scopeTenantId:other,raw:'Room',result:{status:'UNRESOLVED',id:null,candidates:[],more:false}}
  ]}});
 });
 await page.route(`**/api/v1/tenants/${tenant}/locations?*`,route=>route.fulfill({json:{items:[{id:tenant,name:'Room',tenant:{id:tenant,name:'Workspace'}}],page:0,size:20,totalElements:1}}));
 await page.goto(`/tenants/${tenant}/assets/import`);await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.csv.parseFile}).click();
 const mapping=page.getByRole('heading',{name:en.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('tenantId');await mapping.locator('select').nth(1).selectOption('localIdentifier');await mapping.locator('select').nth(2).selectOption('productId');await mapping.locator('select').nth(3).selectOption('locationName');
 await page.getByRole('button',{name:en.csv.showPreview}).click();const create=page.getByRole('link',{name:en.csv.openCreation.replace('{{resource}}',`${en.ui.location} · ${en.csv.nameMode}`)});await expect(create).toHaveAttribute('href',`/tenants/${other}/locations`);
 const pick=page.getByRole('combobox',{name:en.csv.chooseExisting});await pick.fill('Room');await page.getByRole('listbox').getByRole('option').first().click();await page.getByRole('button',{name:en.csv.revalidate}).click();
 expect(seen.at(-1)).toContain(`\"scopeTenantId\":\"${tenant}\"`);expect(seen.at(-1)).not.toContain(`\"scopeTenantId\":\"${other}\"`);await expect(page.getByRole('checkbox',{name:en.csv.selectNumber.replace('{{number}}','3')})).toBeDisabled();
});
