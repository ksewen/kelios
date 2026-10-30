import {test,expect} from '@playwright/test';
import {readFile} from 'node:fs/promises';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
async function setup(page:import('@playwright/test').Page,scope:'GLOBAL'|'TENANT'='GLOBAL',role:'ADMIN'|'USER'='ADMIN',fail=false){
 const exports:URL[]=[];
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url());
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:tenant,scope,tenantId:scope==='TENANT'?tenant:null,role,status:'ACTIVE',displayName:'Fixture'}}});
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'Workspace'}]});
  if(url.pathname===`/api/v1/tenants/${tenant}`)return route.fulfill({json:{id:tenant,name:'Workspace'}});
  if(url.pathname==='/api/v1/asset-csv/export'){exports.push(url);return fail?route.fulfill({status:400,json:{code:'INVALID_INPUT'}}):route.fulfill({contentType:'text/csv;charset=utf-8',body:'\ufeffid,tenant\r\n"1","München"\r\n'});}
  return route.fulfill({json:{items:[],page:Number(url.searchParams.get('page')??0),size:20,totalElements:41}});
 });return exports;
}

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} CSV export uses applied query without paging`,async({page})=>{
 const text=language==='de'?de:en;await page.setViewportSize({width,height:900});await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);const exports=await setup(page);
 await page.goto(`/tenants/${tenant}/assets?q=M%C3%BCnchen&condition=WORKING`);await page.getByRole('button',{name:text.ui.nextPage}).click();await page.getByRole('columnheader',{name:text.ui.tag,exact:true}).getByRole('button').click();await page.getByRole('toolbar',{name:text.ui.assets}).getByRole('textbox').fill('unapplied');
 await page.getByRole('toolbar',{name:text.ui.assets}).getByRole('button',{name:text.csv.exportTitle}).click();const dialog=page.getByRole('dialog',{name:text.csv.exportTitle});await expect(dialog).toBeVisible();await dialog.getByRole('combobox',{name:text.csv.delimiter}).selectOption('COMMA');const download=page.waitForEvent('download');await dialog.getByRole('button',{name:text.csv.downloadExport}).click();const saved=await download;expect(saved.suggestedFilename()).toBe('assets.csv');expect((await readFile(await saved.path())).subarray(0,3)).toEqual(Buffer.from([0xef,0xbb,0xbf]));
 await expect(dialog).toHaveCount(0);expect(exports).toHaveLength(1);expect(Object.fromEntries(exports[0].searchParams)).toMatchObject({q:'München',condition:'WORKING',sort:'tag',direction:'asc',tenantId:tenant,delimiter:','});expect(exports[0].searchParams.has('page')).toBe(false);expect(exports[0].searchParams.has('size')).toBe(false);expect(exports[0].searchParams.get('q')).not.toBe('unapplied');expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

for(const [scope,role] of [['GLOBAL','USER'],['TENANT','USER']] as const)test(`${scope} ${role} can export but cannot import`,async({page})=>{const exports=await setup(page,scope,role);await page.goto(scope==='TENANT'?`/tenants/${tenant}/assets`:'/assets');await expect(page.getByRole('button',{name:en.csv.importTitle})).toHaveCount(0);await page.getByRole('button',{name:en.csv.exportTitle}).click();const dialog=page.getByRole('dialog',{name:en.csv.exportTitle});const download=page.waitForEvent('download');await dialog.getByRole('button',{name:en.csv.downloadExport}).click();await download;expect(exports).toHaveLength(1);expect(exports[0].searchParams.get('tenantId')).toBe(scope==='TENANT'?tenant:null);});

test('export failure keeps dialog and error feedback without a download',async({page})=>{const exports=await setup(page,'GLOBAL','ADMIN',true);await page.goto('/assets');await page.getByRole('button',{name:en.csv.exportTitle}).click();const dialog=page.getByRole('dialog',{name:en.csv.exportTitle});await dialog.getByRole('button',{name:en.csv.downloadExport}).click();await expect(dialog.getByRole('alert')).toBeVisible();await expect(dialog).toBeVisible();expect(exports).toHaveLength(1);});
