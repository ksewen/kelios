import {test,expect} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const file={name:'legacy.csv',mimeType:'text/csv',buffer:Buffer.from('Kennung;Modell\nä-1;Gerät\nä-2;Gerät','utf8')};
const parsed={sha256:'fixture-hash',encoding:'UTF-8',delimiter:';',headers:['Kennung','Modell'],totalRows:2,rows:[{rowNumber:2,values:{Kennung:'ä-1',Modell:'Gerät'}},{rowNumber:3,values:{Kennung:'ä-2',Modell:'Gerät'}}]};
const preview={...parsed,ready:1,warnings:0,errors:1,rows:[{rowNumber:2,status:'READY',source:parsed.rows[0].values,normalized:{localIdentifier:'ä-1'},references:{},issues:[]},{rowNumber:3,status:'ERROR',source:parsed.rows[1].values,normalized:{localIdentifier:'ä-2'},references:{},issues:['Missing Location']}],referenceResolution:{productName:{Gerät:{status:'RESOLVED',id:tenant,candidates:[],more:false}}}};

async function setup(page:import('@playwright/test').Page,role:'ADMIN'|'USER'='ADMIN',scope:'GLOBAL'|'TENANT'='GLOBAL'){
 const requests:{path:string;body:string|null}[]=[];
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname;
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:tenant,scope,tenantId:scope==='TENANT'?tenant:null,role,status:'ACTIVE',displayName:'Fixture'}}});
  if(path==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'Workspace'}]});
  if(path.startsWith('/api/v1/asset-csv/')){requests.push({path,body:route.request().postData()});if(path.endsWith('/parse-preview'))return route.fulfill({json:parsed});if(path.endsWith('/preview'))return route.fulfill({json:preview});return route.fulfill({json:{successful:1,failed:0,skipped:1,rows:[{rowNumber:2,status:'SUCCESS',assetId:tenant,assetTag:'KEL-2026-000001',reason:null},{rowNumber:3,status:'SKIPPED',assetId:null,assetTag:null,reason:'Preview error'}],failureCsv:'\ufeffKennung;KELIOS import error\nä-2;Preview error'}});}
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });return requests;
}

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} CSV import mapping, confirmation and partial result`,async({page})=>{
 const text=language==='de'?de:en;await page.setViewportSize({width,height:900});await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);const requests=await setup(page);
 await page.goto('/assets/import');await expect(page.getByRole('heading',{name:text.csv.importTitle})).toBeVisible();await page.getByRole('combobox',{name:text.csv.tenant}).selectOption(tenant);
 await page.getByLabel(text.csv.file).setInputFiles(file);await page.getByRole('button',{name:text.csv.parseFile}).click();await expect(page.getByText('ä-1')).toBeVisible();
 const mapping=page.getByRole('heading',{name:text.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('localIdentifier');await mapping.locator('select').nth(1).selectOption('productName');
 await expect(mapping.locator('select').nth(0).locator('option[value="productName"]')).toHaveAttribute('disabled','');await expect(mapping.locator('select').nth(0).locator('option[value="productId"]')).toHaveAttribute('disabled','');await expect(mapping.locator('select').nth(0).locator('option[value="categoryName"]')).toHaveAttribute('disabled','');
 await page.getByRole('button',{name:text.csv.showPreview}).click();await expect(page.getByRole('heading',{name:text.csv.previewTitle})).toBeVisible();
 await expect(page.getByRole('checkbox',{name:text.csv.selectNumber.replace('{{number}}','3')})).toBeDisabled();await expect(page.getByRole('checkbox',{name:text.csv.selectNumber.replace('{{number}}','2')})).toBeChecked();
 await page.getByRole('button',{name:text.csv.importSelected.replace('{{count}}','1')}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:text.redesign.cancel}).click();await expect(page.getByRole('button',{name:text.csv.importSelected.replace('{{count}}','1')})).toBeFocused();expect(requests.filter(item=>item.path.endsWith('/execute'))).toHaveLength(0);
 await page.getByRole('button',{name:text.csv.importSelected.replace('{{count}}','1')}).focus();await page.keyboard.press('Enter');await page.getByRole('alertdialog').getByRole('button',{name:text.redesign.confirm,exact:true}).click();await expect(page.getByRole('heading',{name:text.csv.resultTitle})).toBeVisible();expect(requests.filter(item=>item.path.endsWith('/execute'))).toHaveLength(1);const download=page.waitForEvent('download');await page.getByRole('button',{name:text.csv.downloadFailures}).click();expect((await download).suggestedFilename()).toBe('kelios-import-errors.csv');
 expect(requests.find(item=>item.path.endsWith('/preview'))?.body).toContain('productName');expect(requests.find(item=>item.path.endsWith('/execute'))?.body).toContain('selectedRows');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('import is hidden from GLOBAL USER and route is unavailable',async({page})=>{await setup(page,'USER');await page.goto('/assets');await expect(page.getByRole('button',{name:en.csv.importTitle})).toHaveCount(0);await page.goto('/assets/import');await expect(page.getByRole('heading',{name:en.csv.importTitle})).toHaveCount(0);});
test('TENANT ADMIN cannot open the import route',async({page})=>{await setup(page,'ADMIN','TENANT');await page.goto(`/tenants/${tenant}/assets`);await expect(page.getByRole('button',{name:en.csv.importTitle})).toHaveCount(0);await page.goto(`/tenants/${tenant}/assets/import`);await expect(page.getByRole('heading',{name:en.csv.importTitle})).toHaveCount(0);});
test('top-level Tenant switch asks before abandoning an in-progress import',async({page})=>{await setup(page);await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.ui.tenantContext}).click();await page.getByRole('menuitem',{name:'Workspace'}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();await expect(page).toHaveURL(/assets\/import$/);await page.getByRole('button',{name:en.ui.tenantContext}).click();await page.getByRole('menuitem',{name:'Workspace'}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect(page).toHaveURL(new RegExp(`/tenants/${tenant}/dashboard$`));});

test('encoding and delimiter changes invalidate raw preview; leaving a draft asks before discarding',async({page})=>{
 const requests=await setup(page);await page.goto('/assets/import');await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('combobox',{name:en.csv.encoding}).selectOption('WINDOWS-1252');await page.getByRole('combobox',{name:en.csv.delimiter}).selectOption('COMMA');
 await page.getByRole('button',{name:en.csv.parseFile}).click();await expect(page.getByText('ä-1')).toBeVisible();expect(requests.find(item=>item.path.endsWith('/parse-preview'))?.path).toBe('/api/v1/asset-csv/parse-preview');
 await page.getByRole('combobox',{name:en.csv.delimiter}).selectOption('SEMICOLON');await expect(page.getByText('ä-1')).toHaveCount(0);await page.getByRole('navigation',{name:en.redesign.breadcrumb}).getByRole('link',{name:en.ui.assets}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();await expect(page).toHaveURL(/assets\/import$/);
 await page.getByRole('navigation',{name:en.redesign.breadcrumb}).getByRole('link',{name:en.ui.assets}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect(page).toHaveURL(/\/assets$/);
});

test('ambiguous Name reference requires explicit choice and a fresh preview',async({page})=>{
 await setup(page);let previews=0;await page.route('**/api/v1/asset-csv/preview',route=>{previews++;const chosen=route.request().postData()?.includes(`\"Gerät\":\"${tenant}\"`);return route.fulfill({json:{...preview,ready:chosen?1:0,errors:chosen?0:1,rows:[{...preview.rows[0],status:chosen?'READY':'ERROR',issues:chosen?[]:['product ambiguous: Gerät']}],referenceResolution:{productName:{Gerät:{status:chosen?'CHOSEN':'AMBIGUOUS',id:chosen?tenant:null,candidates:[{id:tenant,name:'Gerät',tenantId:null}],more:false}}}}});});
 await page.route(`**/api/v1/tenants/${tenant}/catalog/products?*`,route=>route.fulfill({json:{items:[{id:tenant,name:'Gerät',tenant:null}],page:0,size:20,totalElements:1}}));
 await page.goto('/assets/import');await page.getByRole('combobox',{name:en.csv.tenant}).selectOption(tenant);await page.getByLabel(en.csv.file).setInputFiles(file);await page.getByRole('button',{name:en.csv.parseFile}).click();const mapping=page.getByRole('heading',{name:en.csv.columnMapping}).locator('..');await mapping.locator('select').nth(0).selectOption('localIdentifier');await mapping.locator('select').nth(1).selectOption('productName');await page.getByRole('button',{name:en.csv.showPreview}).click();
 await expect(page.getByText(en.csv.resolutionAMBIGUOUS)).toBeVisible();const choose=page.getByRole('combobox',{name:en.csv.chooseExisting});await choose.fill('Gerät');await page.getByRole('listbox').getByRole('option').first().click();await expect(page.getByText(en.csv.revalidateNeeded)).toBeVisible();await page.getByRole('button',{name:en.csv.revalidate}).click();await expect(page.getByRole('checkbox',{name:en.csv.selectNumber.replace('{{number}}','2')})).toBeChecked();expect(previews).toBe(2);
});
