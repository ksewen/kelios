import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};

const own='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',other='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
async function setup(page:Page,scope:'GLOBAL'|'TENANT',language:'en'|'de'='en'){
  const writes:{path:string;body:{name:string;note:null}}[]=[];
  let reads=0;
  await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname,method=route.request().method();
    if(path==='/api/v1/auth/csrf')return route.fulfill({json:{token:'fixture'}});
    if(path==='/api/v1/auth/session/refresh')return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:own,scope,tenantId:scope==='TENANT'?own:null,role:'ADMIN',status:'ACTIVE',displayName:'Admin'}}});
    if(path==='/api/v1/auth/me')return route.fulfill({json:{id:own,scope,tenantId:scope==='TENANT'?own:null,role:'ADMIN',status:'ACTIVE',displayName:'Admin'}});
    if(path==='/api/v1/tenants')return route.fulfill({json:[{id:own,name:'Own'},{id:other,name:'Other'}]});
    if(path===`/api/v1/tenants/${own}`)return route.fulfill({json:{id:own,name:'Own'}});
    if(method==='POST'&&/\/api\/v1\/tenants\/[^/]+\/(locations|owners)$/.test(path)){
      const body=route.request().postDataJSON() as {name:string;note:null};writes.push({path,body});
      return body.name.trim()?route.fulfill({status:201,json:{id:other}}):route.fulfill({status:400,json:{code:'INVALID_INPUT'}});
    }
    if(method==='GET'&&/\/(locations|owners)$/.test(path)){reads++;return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});}
    return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
  });
  return {writes,reads:()=>reads};
}
async function open(page:Page,kind:'locations'|'owners',text=en){
  await page.goto(`/tenants/${own}/${kind}`);
  await page.getByRole('toolbar').getByRole('button',{name:text.csv.importTitle}).click();
  return page.getByRole('dialog');
}
async function upload(dialog:ReturnType<Page['getByRole']>,text:string){await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from(text)});}
async function next(dialog:ReturnType<Page['getByRole']>,text=en){const button=dialog.getByRole('button',{name:text.csv.next});await expect(button).toBeEnabled();await button.click();}
async function importRows(page:Page,dialog:ReturnType<Page['getByRole']>,count:number,text=en){await dialog.getByRole('button',{name:text.csv.importSelected.replace('{{count}}',String(count))}).click();await page.getByRole('alertdialog').getByRole('button',{name:text.redesign.confirm,exact:true}).click();}

for(const kind of ['locations','owners'] as const){
  test(`${kind}: GLOBAL ADMIN maps an arbitrary source column into one selected Tenant`,async({page})=>{
    const fixture=await setup(page,'GLOBAL'),dialog=await open(page,kind);
    await upload(dialog,'Room;Code\nSame;1\n;2\nSame;3');
    await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByText('Code')).toBeVisible();
    await expect(dialog.getByText(/3 records · 2 columns/)).toBeVisible();
    await expect(dialog.getByRole('button',{name:en.csv.next})).toBeDisabled();
    await dialog.getByRole('combobox',{name:en.fields.tenantName}).selectOption(other);await next(dialog);
    const mapping=dialog.getByRole('combobox',{name:en.ui.name});
    await expect(mapping).toHaveValue('');await mapping.selectOption('0');
    await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText(en.placeCsv.emptyName)).toBeVisible();
    await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('Column 2 · Code: 1')).toBeVisible();
    await expect(dialog.getByText(`${en.csv.ready}: 2`)).toBeVisible();
    await next(dialog);
    await expect(dialog.getByText(/Other · Name: Column 1 · Room/)).toBeVisible();
    await importRows(page,dialog,3);
    await expect(dialog.getByText(/2 successful · 1 failed/)).toBeVisible();
    expect(fixture.writes).toEqual(['Same','','Same'].map(name=>({path:`/api/v1/tenants/${other}/${kind}`,body:{name,note:null}})));
    await expect(dialog.getByText('INVALID_INPUT (HTTP 400)',{exact:false})).toBeVisible();
    expect(fixture.reads()).toBeGreaterThan(1);
  });
  test(`${kind}: TENANT ADMIN imports headerless names into signed Tenant`,async({page})=>{
    const fixture=await setup(page,'TENANT'),dialog=await open(page,kind);
    await expect(dialog.getByRole('combobox',{name:en.fields.tenantName})).toHaveCount(0);
    await expect(dialog.getByRole('textbox',{name:en.fields.tenantName})).toHaveAttribute('readonly');
    await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();await upload(dialog,'First\nSecond');
    await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByRole('rowheader',{name:'1'})).toBeVisible();
    await next(dialog);await expect(dialog.getByRole('combobox',{name:en.ui.name})).toHaveValue('0');
    await next(dialog);await importRows(page,dialog,2);
    await expect(dialog.getByText(/2 successful/)).toBeVisible();
    expect(fixture.writes).toEqual(['First','Second'].map(name=>({path:`/api/v1/tenants/${own}/${kind}`,body:{name,note:null}})));
  });
}

test('unique name heading preselects; blank and duplicate headings stay positional',async({page})=>{
  await setup(page,'TENANT');const dialog=await open(page,'locations');
  await upload(dialog,'Code;NAME;Other\n1;Chosen;Ignored');await next(dialog);
  await expect(dialog.getByRole('combobox',{name:en.ui.name})).toHaveValue('1');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('Chosen')).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await upload(dialog,'Name;name;;Code\nA;B;C;D');await next(dialog);
  const mapping=dialog.getByRole('combobox',{name:en.ui.name});await expect(mapping).toHaveValue('');
  await expect(mapping.locator('option')).toHaveText([en.ui.select,'Column 1 · Name','Column 2 · name','Column 3','Column 4 · Code']);
  await mapping.selectOption('2');await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('cell',{name:'C',exact:true})).toBeVisible();
});

test('headerless later-column mapping includes first source record and ignores other columns',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'owners');
  await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();await upload(dialog,'1;X;First\n2;Y;Second');
  await next(dialog);const mapping=dialog.getByRole('combobox',{name:en.ui.name});await expect(mapping).toHaveValue('');await mapping.selectOption('2');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toBeVisible();
  await next(dialog);await importRows(page,dialog,2);await expect(dialog.getByText(/2 successful/)).toBeVisible();
  expect(fixture.writes.map(item=>item.body.name)).toEqual(['First','Second']);
});

test('automatic delimiter exposes split columns; an explicit choice restores a punctuated name',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'locations');
  await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();await upload(dialog,'A Block, 414\nB Block, 415');
  await expect(dialog.getByText(/2 records · 2 columns · Comma/)).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByText('414')).toBeVisible();
  await dialog.getByRole('combobox',{name:en.csv.delimiter}).selectOption('SEMICOLON');
  await expect(dialog.getByText(/2 records · 1 columns · Semicolon/)).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByText('A Block, 414')).toBeVisible();
  await next(dialog);await expect(dialog.getByRole('combobox',{name:en.ui.name})).toHaveValue('0');
  await next(dialog);await importRows(page,dialog,2);await expect(dialog.getByText(/2 successful/)).toBeVisible();
  expect(fixture.writes.map(item=>item.body.name)).toEqual(['A Block, 414','B Block, 415']);
});

test('header and custom delimiter changes reparse without reupload and reset changed mappings',async({page})=>{
  await setup(page,'TENANT');const dialog=await open(page,'owners');
  await upload(dialog,'Room|Code\nA|1');
  await expect(dialog.getByText(/1 records · 1 columns/)).toBeVisible();
  await dialog.getByRole('combobox',{name:en.csv.delimiter}).selectOption('CUSTOM');
  await expect(dialog.getByRole('alert')).toContainText(en.csv.customDelimiterRequired);
  await dialog.getByRole('textbox',{name:en.csv.customDelimiter}).fill('|');
  await expect(dialog.getByText(/1 records · 2 columns/)).toBeVisible();
  await next(dialog);await dialog.getByRole('combobox',{name:en.ui.name}).selectOption('0');
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();
  await expect(dialog.getByText(/2 records · 2 columns/)).toBeVisible();
  await next(dialog);await expect(dialog.getByRole('combobox',{name:en.ui.name})).toHaveValue('');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toHaveCount(0);
});

test('mapping survives equivalent reparse but resets when the selected file changes',async({page})=>{
  await setup(page,'TENANT');const dialog=await open(page,'locations');
  await upload(dialog,'Room;Code\nFirst;1');await next(dialog);
  const mapping=dialog.getByRole('combobox',{name:en.ui.name});await mapping.selectOption('0');
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await dialog.getByRole('combobox',{name:en.csv.encoding}).selectOption('UTF-8');
  await expect(dialog.getByText(/1 records · 2 columns/)).toBeVisible();
  await next(dialog);await expect(mapping).toHaveValue('0');
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await upload(dialog,'Other;Code\nSecond;2');await next(dialog);
  await expect(mapping).toHaveValue('');
});

test('invalid encoding and malformed structure disable progression without writes',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'locations');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from([110,97,109,101,10,0x80])});
  await expect(dialog.getByRole('alert')).toContainText('not valid UTF-8');
  await expect(dialog.getByRole('button',{name:en.csv.next})).toBeDisabled();
  await dialog.getByRole('combobox',{name:en.csv.encoding}).selectOption('WINDOWS-1252');
  await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByText('€')).toBeVisible();
  await upload(dialog,'A;B\nC');await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();
  await dialog.getByRole('combobox',{name:en.csv.delimiter}).selectOption('SEMICOLON');
  await expect(dialog.getByRole('alert')).toContainText('Inconsistent column count');
  await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview})).toHaveCount(0);
  await expect(dialog.getByRole('button',{name:en.csv.next})).toBeDisabled();
  expect(fixture.writes).toHaveLength(0);
});

test('late file reads cannot restore an obsolete preview or mapping',async({page})=>{
  await page.addInitScript(()=>{const read=File.prototype.arrayBuffer;let first=true;File.prototype.arrayBuffer=function(){const result=read.call(this);if(!first)return result;first=false;return new Promise<ArrayBuffer>(resolve=>setTimeout(()=>void result.then(resolve),350));};});
  await setup(page,'TENANT');const dialog=await open(page,'locations');
  await upload(dialog,'Old;Code\nA;1');await upload(dialog,'New;Code\nB;2\nC;3');
  await expect(dialog.getByText(/2 records · 2 columns/)).toBeVisible();await page.waitForTimeout(450);
  await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByText('New')).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.sourcePreview}).getByText('Old')).toHaveCount(0);
  await next(dialog);await expect(dialog.getByRole('combobox',{name:en.ui.name})).toHaveValue('');
});

test('confirmation cancellation, duplicate clicks and uncertain POST keep individual outcomes',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'owners');
  await upload(dialog,'name\nFirst\nSecond');await next(dialog);await next(dialog);
  const importButton=dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','2')});
  await importButton.click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();
  expect(fixture.writes).toHaveLength(0);await expect(importButton).toBeEnabled();
  let attempts=0;await page.route(`**/api/v1/tenants/${own}/owners`,route=>{if(route.request().method()==='POST'){attempts++;return route.abort('failed');}return route.fallback();});
  await importButton.evaluate((button:HTMLButtonElement)=>{button.click();button.click();});
  await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();
  await expect(dialog.getByText(/1 uncertain · 1 not attempted/)).toBeVisible();
  expect(attempts).toBe(1);await expect(importButton).toHaveCount(0);
});

test('ordinary navigation is blocked during a delayed POST, then results persist',async({page})=>{
  const fixture=await setup(page,'GLOBAL');let release=()=>{},waiting=false;
  await page.route(`**/api/v1/tenants/${own}/owners`,async route=>{if(route.request().method()==='POST'){waiting=true;await new Promise<void>(resolve=>{release=resolve;});}return route.fallback();});
  await page.goto(`/tenants/${own}/dashboard`);
  await page.getByRole('navigation',{name:en.ui.inventoryNavigation}).getByRole('link',{name:'Owners',exact:true}).click();
  const path=page.url();await page.getByRole('toolbar').getByRole('button',{name:en.csv.importTitle}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByRole('combobox',{name:en.fields.tenantName}).selectOption(own);
  await upload(dialog,'name\nFirst');await next(dialog);await next(dialog);
  await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','1')}).click();
  await expect(page.getByRole('alertdialog')).toBeVisible();await page.evaluate(()=>history.back());await expect(page).toHaveURL(path);
  await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();
  await expect.poll(()=>waiting).toBe(true);
  try{await expect(dialog.getByRole('button',{name:en.redesign.close})).toBeDisabled();await page.evaluate(()=>history.back());await expect(page).toHaveURL(path);}
  finally{release();}
  await expect(dialog.getByText(/1 successful/)).toBeVisible();expect(fixture.writes).toHaveLength(1);
});

for(const language of ['en','de'] as const)for(const width of [390,1440])test(`${language}/${width}: responsive mapping keeps focus and draft through same-context refresh`,async({page})=>{
  await page.setViewportSize({width,height:900});await setup(page,'TENANT',language);const text=language==='en'?en:de;
  const dialog=await open(page,'locations',text);
  await dialog.getByLabel(text.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('Raum;Code\nA;1')});
  await expect(dialog.getByRole('table',{name:text.placeCsv.sourcePreview})).toBeVisible();
  const box=await dialog.boundingBox();expect(box).not.toBeNull();expect(box!.width).toBeLessThanOrEqual(Math.min(width-16,1025));
  await next(dialog,text);const mapping=dialog.getByRole('combobox',{name:text.ui.name});
  await mapping.selectOption('0');await mapping.evaluate(node=>(window as any).mappingInput=node);await mapping.focus();
  await page.evaluate(()=>import('/src/auth.ts').then(module=>module.authSession.refresh()));
  await expect(mapping).toBeFocused();expect(await mapping.evaluate(node=>node===(window as any).mappingInput)).toBe(true);
  await next(dialog,text);await expect(dialog.getByText(/Raum/)).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
