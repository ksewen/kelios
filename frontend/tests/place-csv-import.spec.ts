import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};

const own='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',other='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
async function setup(page:Page,scope:'GLOBAL'|'TENANT'){
  const writes:{path:string;body:{name:string;note:null}}[]=[];
  let reads=0;
  await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
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
async function open(page:Page,kind:'locations'|'owners'){
  await page.goto(`/tenants/${own}/${kind}`);
  await page.getByRole('toolbar').getByRole('button',{name:en.csv.importTitle}).click();
  return page.getByRole('dialog');
}
async function confirm(page:Page){await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();}
async function next(dialog:ReturnType<Page['getByRole']>){const button=dialog.getByRole('button',{name:en.csv.next});await expect(button).toBeEnabled();await button.click();}

for(const kind of ['locations','owners'] as const){
  test(`${kind}: GLOBAL ADMIN explicitly selects one Tenant; duplicate names succeed and invalid names fail independently`,async({page})=>{
    const fixture=await setup(page,'GLOBAL'),dialog=await open(page,kind);
    await expect(dialog.getByRole('combobox',{name:en.csv.encoding})).toHaveValue('AUTO');
    await expect(dialog.getByRole('combobox',{name:en.csv.delimiter})).toHaveValue('AUTO');
    const target=dialog.getByRole('combobox',{name:en.fields.tenantName});
    await expect(target).toHaveValue('');
    await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('NAME\nSame\n\nSame')});
    await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'2'})).toBeVisible();
    await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText(en.placeCsv.emptyName)).toBeVisible();
    await expect(dialog.getByRole('button',{name:en.csv.next})).toBeDisabled();
    await target.selectOption(other);await next(dialog);
    await expect(dialog.getByRole('heading',{name:en.placeCsv.previewTitle})).toBeFocused();
    await expect(dialog.getByText(en.placeCsv.emptyName)).toBeVisible();
    await expect(dialog.getByText(`${en.csv.ready}: 2`)).toBeVisible();
    await expect(dialog.getByText(`${en.placeCsv.invalid}: 1`)).toBeVisible();
    await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','3')}).click();await confirm(page);
    await expect(dialog.getByText(/2 successful · 1 failed/)).toBeVisible();
    expect(fixture.writes).toEqual(['Same','','Same'].map(name=>({path:`/api/v1/tenants/${other}/${kind}`,body:{name,note:null}})));
    await expect(dialog.getByText('INVALID_INPUT (HTTP 400)',{exact:false})).toBeVisible();
    expect(fixture.reads()).toBeGreaterThan(1);
  });
  test(`${kind}: TENANT ADMIN uses signed Tenant and headerless first row`,async({page})=>{
    const fixture=await setup(page,'TENANT'),dialog=await open(page,kind);
    await expect(dialog.getByRole('combobox',{name:en.fields.tenantName})).toHaveCount(0);
    await expect(dialog.getByRole('textbox',{name:en.fields.tenantName})).toHaveAttribute('readonly');
    await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();
    await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('First\nSecond')});
    await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toBeVisible();
    await next(dialog);
    await expect(dialog.getByRole('rowheader',{name:'1'})).toBeVisible();
    await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','2')}).click();await confirm(page);
    await expect(dialog.getByText(/2 successful/)).toBeVisible();
    expect(fixture.writes).toEqual(['First','Second'].map(name=>({path:`/api/v1/tenants/${own}/${kind}`,body:{name,note:null}})));
  });
}

test('extra columns and unapproved header prevent all writes',async({page})=>{
  const fixture=await setup(page,'GLOBAL'),dialog=await open(page,'locations');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nA;B')});
  await expect(dialog.getByRole('alert')).toContainText('Possible extra column');
  await expect(dialog.getByRole('button',{name:en.csv.next})).toBeDisabled();
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('Bezeichnung\nA')});
  await expect(dialog.getByRole('alert')).toContainText("header 'name'");
  expect(fixture.writes).toHaveLength(0);
});

test('automatic settings validate one column; explicit delimiter resolves punctuation and settings invalidate preview',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'locations');
  const delimiter=dialog.getByRole('combobox',{name:en.csv.delimiter});
  await expect(delimiter.locator('option')).toHaveText([en.csv.delimiterAUTO,en.csv.delimiterSEMICOLON,en.csv.delimiterCOMMA,en.csv.delimiterTAB,en.csv.delimiterCUSTOM]);
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nA,B')});
  await expect(dialog.getByRole('alert')).toContainText('Possible extra column at line 2');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle})).toHaveCount(0);
  expect(fixture.writes).toHaveLength(0);
  await delimiter.selectOption('SEMICOLON');
  await expect(dialog.getByRole('alert')).toHaveCount(0);
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('A,B')).toBeVisible();
  await next(dialog);
  await expect(dialog.getByText('A,B')).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await expect(dialog.getByText('names.csv')).toBeVisible();
  await delimiter.selectOption('AUTO');
  await expect(dialog.getByText('A,B')).toHaveCount(0);
  await delimiter.selectOption('SEMICOLON');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('A,B')).toBeVisible();
  await next(dialog);
  await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','1')}).click();await confirm(page);
  expect(fixture.writes).toEqual([{path:`/api/v1/tenants/${own}/locations`,body:{name:'A,B',note:null}}]);
  await dialog.getByRole('button',{name:en.csv.newImport}).click();
  await expect(delimiter).toHaveValue('AUTO');
  await expect(dialog.getByRole('combobox',{name:en.csv.encoding})).toHaveValue('AUTO');
});

test('automatic UTF-8 rejects invalid bytes; explicit legacy decoding and BOM guidance work',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'owners');
  const encoding=dialog.getByRole('combobox',{name:en.csv.encoding});
  await expect(encoding.locator('option')).toHaveText([en.csv.auto,'UTF-8','WINDOWS-1252','ISO-8859-1']);
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from([110,97,109,101,10,0x80])});
  await expect(dialog.getByRole('alert')).toContainText('not valid UTF-8');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle})).toHaveCount(0);
  expect(fixture.writes).toHaveLength(0);
  await encoding.selectOption('WINDOWS-1252');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('€')).toBeVisible();
  await next(dialog);
  await expect(dialog.getByText('€')).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('\ufeffname\nB')});
  await expect(dialog.getByRole('alert')).toContainText('UTF-8 BOM conflicts');
  await encoding.selectOption('AUTO');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('B',{exact:true})).toBeVisible();
  await next(dialog);
  await expect(dialog.getByText('B', {exact:true})).toBeVisible();
  expect(fixture.writes).toHaveLength(0);
});

test('header toggle immediately changes row one, source numbers and counts without reupload',async({page})=>{
  await setup(page,'TENANT');const dialog=await open(page,'locations');
  const header=dialog.getByRole('checkbox',{name:en.csv.headerPresent});
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst\n\nSecond')});
  await expect(dialog.getByText('3 names ready for review.',{exact:false})).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toHaveCount(0);
  await next(dialog);
  await expect(dialog.getByRole('rowheader',{name:'1'})).toHaveCount(0);
  await expect(dialog.getByRole('rowheader',{name:'2'})).toBeVisible();
  await expect(dialog.getByText(`${en.placeCsv.invalid}: 1`)).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await header.uncheck();
  await expect(dialog.getByText('4 names ready for review.',{exact:false})).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toBeVisible();
  await next(dialog);
  await expect(dialog.getByRole('rowheader',{name:'1'})).toBeVisible();
  await expect(dialog.getByRole('row',{name:/^1 1 name/})).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await header.check();
  await expect(dialog.getByText('3 names ready for review.',{exact:false})).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toHaveCount(0);
  await expect(dialog.getByText('names.csv')).toBeVisible();
});

test('custom delimiter changes reparse immediately and a changed Tenant is reviewed before import',async({page})=>{
  const fixture=await setup(page,'GLOBAL'),dialog=await open(page,'owners');
  const target=dialog.getByRole('combobox',{name:en.fields.tenantName});
  const delimiter=dialog.getByRole('combobox',{name:en.csv.delimiter});
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nA|B')});
  await delimiter.selectOption('CUSTOM');
  await expect(dialog.getByRole('alert')).toContainText(en.csv.customDelimiterRequired);
  await dialog.getByRole('textbox',{name:en.csv.customDelimiter}).fill('|');
  await expect(dialog.getByRole('alert')).toContainText('More than one column');
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle})).toHaveCount(0);
  await dialog.getByRole('textbox',{name:en.csv.customDelimiter}).fill(';');
  await expect(dialog.getByRole('alert')).toHaveCount(0);
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('A|B')).toBeVisible();
  await target.selectOption(other);await next(dialog);
  await expect(dialog.getByText('Other · names.csv')).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.back}).click();
  await target.selectOption(own);await next(dialog);
  await expect(dialog.getByText('Own · names.csv')).toBeVisible();
  await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','1')}).click();await confirm(page);
  expect(fixture.writes).toEqual([{path:`/api/v1/tenants/${own}/owners`,body:{name:'A|B',note:null}}]);
});

test('a stale file read cannot restore preview for newer header settings',async({page})=>{
  await page.addInitScript(()=>{
    const read=File.prototype.arrayBuffer;let first=true;
    File.prototype.arrayBuffer=function(){const result=read.call(this);if(!first)return result;first=false;return new Promise<ArrayBuffer>(resolve=>setTimeout(()=>void result.then(resolve),350));};
  });
  await setup(page,'TENANT');const dialog=await open(page,'locations');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst')});
  await dialog.getByRole('checkbox',{name:en.csv.headerPresent}).uncheck();
  await expect(dialog.getByText('2 names ready for review.',{exact:false})).toBeVisible();
  await page.waitForTimeout(450);
  await expect(dialog.getByText('2 names ready for review.',{exact:false})).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader',{name:'1'})).toBeVisible();
  await next(dialog);
  await expect(dialog.getByRole('rowheader',{name:'1'})).toBeVisible();
});

test('a stale read of an earlier file cannot replace the newly selected file',async({page})=>{
  await page.addInitScript(()=>{
    const read=File.prototype.arrayBuffer;let first=true;
    File.prototype.arrayBuffer=function(){const result=read.call(this);if(!first)return result;first=false;return new Promise<ArrayBuffer>(resolve=>setTimeout(()=>void result.then(resolve),350));};
  });
  await setup(page,'TENANT');const dialog=await open(page,'owners');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'old.csv',mimeType:'text/csv',buffer:Buffer.from('name\nOld')});
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'new.csv',mimeType:'text/csv',buffer:Buffer.from('name\nNew\nSecond')});
  await expect(dialog.getByText('2 names ready for review.',{exact:false})).toBeVisible();
  await page.waitForTimeout(450);
  await expect(dialog.getByText('2 names ready for review.',{exact:false})).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('New',{exact:true})).toBeVisible();
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByText('Old',{exact:true})).toHaveCount(0);
  await next(dialog);
  await expect(dialog.getByText('New',{exact:true})).toBeVisible();
  await expect(dialog.getByText('Old',{exact:true})).toHaveCount(0);
});

test('settings preview is bounded and survives a same-context session refresh',async({page})=>{
  await page.setViewportSize({width:390,height:900});
  await setup(page,'TENANT');const dialog=await open(page,'locations');
  const names=Array.from({length:12},(_,index)=>`Name ${index+1}`);
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from(`name\n${names.join('\n')}`)});
  const preview=dialog.getByRole('table',{name:en.placeCsv.previewTitle});
  await expect(preview.getByRole('rowheader')).toHaveCount(10);
  await expect(dialog.getByText(en.placeCsv.previewSample.replace('{{count}}','10'))).toBeVisible();
  const encoding=dialog.getByRole('combobox',{name:en.csv.encoding});
  await encoding.evaluate(node=>(window as any).csvEncoding=node);
  await preview.evaluate(node=>{(window as any).csvPreview=node;node.parentElement!.scrollTop=80;});
  const scroll=await preview.evaluate(node=>node.parentElement!.scrollTop);
  await encoding.focus();
  await page.evaluate(()=>import('/src/auth.ts').then(module=>module.authSession.refresh()));
  await expect(encoding).toBeFocused();
  expect(await encoding.evaluate(node=>node===(window as any).csvEncoding)).toBe(true);
  expect(await preview.evaluate(node=>node===(window as any).csvPreview)).toBe(true);
  expect(await preview.evaluate(node=>node.parentElement!.scrollTop)).toBe(scroll);
  await expect(dialog.getByText('names.csv')).toBeVisible();
  await next(dialog);
  await expect(dialog.getByRole('table',{name:en.placeCsv.previewTitle}).getByRole('rowheader')).toHaveCount(12);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});

test('confirmation cancellation and rapid clicks never submit the same rows twice',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'owners');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst\nSecond')});
  await next(dialog);
  const importButton=dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','2')});
  await importButton.click();
  await expect(page.locator('[role="dialog"] button[aria-busy="true"]')).toBeDisabled();
  await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();
  await expect(importButton).toBeEnabled();expect(fixture.writes).toHaveLength(0);
  await importButton.evaluate((button:HTMLButtonElement)=>{button.click();button.click();});
  await expect(page.locator('[role="dialog"] button[aria-busy="true"]')).toBeDisabled();await confirm(page);
  await expect(dialog.getByRole('heading',{name:en.placeCsv.resultTitle})).toBeVisible();
  await expect(dialog.getByText(/2 successful/)).toBeVisible();
  expect(fixture.writes).toHaveLength(2);
});

test('closing a reviewed draft asks before discarding and never reports an import',async({page})=>{
  const fixture=await setup(page,'TENANT'),dialog=await open(page,'locations');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst')});
  await next(dialog);
  await dialog.getByRole('button',{name:en.redesign.close}).click();
  await expect(page.getByRole('alertdialog')).toBeVisible();
  await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel}).click();
  await expect(dialog.getByRole('heading',{name:en.placeCsv.previewTitle})).toBeVisible();
  await dialog.getByRole('button',{name:en.redesign.close}).click();
  await confirm(page);
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(fixture.writes).toHaveLength(0);
});

test('execution locks the wizard until the row results are known',async({page})=>{
  const fixture=await setup(page,'TENANT');
  await page.route(`**/api/v1/tenants/${own}/locations`,async route=>{
    if(route.request().method()==='POST')await new Promise(resolve=>setTimeout(resolve,700));
    return route.fallback();
  });
  const dialog=await open(page,'locations');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst\nSecond')});
  await next(dialog);
  await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','2')}).click();await confirm(page);
  await expect(dialog.getByRole('heading',{name:en.placeCsv.resultTitle})).toBeVisible();
  await expect(dialog.getByRole('button',{name:en.redesign.close})).toBeDisabled();
  await expect(dialog.getByRole('button',{name:en.csv.back})).toHaveCount(0);
  await expect(dialog.getByText(/2 successful/)).toBeVisible();
  await expect(dialog.getByRole('button',{name:en.redesign.close})).toBeEnabled();
  expect(fixture.writes).toHaveLength(2);
});

test('ordinary navigation cannot discard confirmation or in-flight Place results',async({page})=>{
  const fixture=await setup(page,'GLOBAL');let release=()=>{},waiting=false;
  await page.route(`**/api/v1/tenants/${own}/owners`,async route=>{
    if(route.request().method()==='POST'){waiting=true;await new Promise<void>(resolve=>{release=resolve;});}
    return route.fallback();
  });
  await page.goto(`/tenants/${own}/dashboard`);
  await page.getByRole('navigation',{name:en.ui.inventoryNavigation}).getByRole('link',{name:'Owners',exact:true}).click();
  const path=page.url();
  await page.getByRole('toolbar').getByRole('button',{name:en.csv.importTitle}).click();
  const dialog=page.getByRole('dialog');
  await dialog.getByRole('combobox',{name:en.fields.tenantName}).selectOption(own);
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst')});
  await next(dialog);
  await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','1')}).click();
  await page.evaluate(()=>history.back());
  await expect(page).toHaveURL(path);
  await expect(page.getByRole('alertdialog')).toBeVisible();
  await confirm(page);
  await expect.poll(()=>waiting).toBe(true);
  try{
    await expect(dialog.getByText(en.placeCsv.finishBeforeLeaving)).toBeVisible();
    await page.evaluate(()=>history.back());
    await expect(page).toHaveURL(path);
    await page.locator(`nav[aria-label="${en.redesign.breadcrumb}"] a`).first().evaluate(node=>(node as HTMLAnchorElement).click());
    await expect(page).toHaveURL(path);
    await page.locator(`nav[aria-label="${en.ui.inventoryNavigation}"] a[href$="/locations"]`).first().evaluate(node=>(node as HTMLAnchorElement).click());
    await expect(page).toHaveURL(path);
    await expect(page.getByRole('alertdialog')).toHaveCount(0);
    await expect(dialog).toBeVisible();
  }finally{release();}
  await expect(dialog.getByText(/1 successful/)).toBeVisible();
  expect(fixture.writes).toHaveLength(1);
  await dialog.getByRole('button',{name:en.redesign.close}).click();
  await page.getByRole('navigation',{name:en.redesign.breadcrumb}).getByRole('link').first().click();
  await expect(page).toHaveURL(`/tenants/${own}/dashboard`);
});

test('an uncertain POST stops later rows and cannot be replayed from results',async({page})=>{
  const fixture=await setup(page,'TENANT');let attempts=0;
  await page.route(`**/api/v1/tenants/${own}/owners`,route=>{
    if(route.request().method()==='POST'){attempts++;return route.abort('failed');}
    return route.fallback();
  });
  const dialog=await open(page,'owners');
  await dialog.getByLabel(en.csv.file).setInputFiles({name:'names.csv',mimeType:'text/csv',buffer:Buffer.from('name\nFirst\nSecond')});
  await next(dialog);
  await dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','2')}).click();await confirm(page);
  await expect(dialog.getByRole('alert')).toContainText(en.placeCsv.unknown);
  await expect(dialog.getByText(/1 uncertain · 1 not attempted/)).toBeVisible();
  expect(attempts).toBe(1);expect(fixture.writes).toHaveLength(0);
  await expect(dialog.getByRole('button',{name:en.csv.importSelected.replace('{{count}}','2')})).toHaveCount(0);
});
