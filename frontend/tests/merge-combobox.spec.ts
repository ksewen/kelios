import {labelFixture,tenantFixture} from './backend-fixtures';
import {test,expect,type Page,type Route} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const a='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',b='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2',c='cccccccc-cccc-cccc-cccc-ccccccccccc3';
const rows=[labelFixture(a,'Same name',null),{...labelFixture(b,'Same name','eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee'),tenant:{id:'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',name:'Readable Tenant B'}}];
async function setup(page:Page,kind:string,language='en',role='ADMIN',scope='GLOBAL'){
 const held:{url:URL;route:Route}[]=[],posts:{path:string;body:any}[]=[];
 await page.addInitScript(lang=>localStorage.setItem('kelios.ui.language',lang),language);
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url());
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:c,scope,role,status:'ACTIVE',tenantId:scope==='TENANT'?a:null,displayName:'Fixture'}}});
  if(url.pathname==='/api/v1/tenants'&&scope==='TENANT')return route.fulfill({status:403,json:{status:403}});
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[]});
  if(route.request().method()==='POST'){posts.push({path:url.pathname,body:route.request().postDataJSON()});return route.fulfill({json:{productCount:0,assetCount:0,affectedTenants:[],blockers:[]}});}
  if(url.pathname.endsWith(`/catalog/${kind}`)){held.push({url,route});return;}
  if(url.pathname===`/api/v1/tenants/${a}`)return role==='ADMIN'?route.fulfill({json:tenantFixture(a,'Signed Tenant')}):route.fulfill({status:403,json:{status:403}});
  if(new RegExp(`/catalog/${kind}/[^/]+$`).test(url.pathname))return route.fulfill({json:{...labelFixture(url.pathname.split('/').at(-1)!,'Referenced label',null),deleted:false,deletedAt:null,deletedBy:null}});
  return route.fulfill({status:404,json:{status:404}});
 });
 const reply=async(q:string,n=0,items=rows,total=30,status=200)=>{
  await expect.poll(()=>held.some(r=>(r.url.searchParams.get('q')??'')===q&&Number(r.url.searchParams.get('page'))===n)).toBe(true);
  for(const r of held.filter(r=>(r.url.searchParams.get('q')??'')===q&&Number(r.url.searchParams.get('page'))===n)){held.splice(held.indexOf(r),1);await r.route.fulfill({status,json:status===200?{items,totalElements:total,size:20,page:n}:{code:'SERVICE_UNAVAILABLE'}});}
 };
 await page.goto(scope==='TENANT'?`/tenants/${a}/${kind}`:`/${kind}`);await reply('');return {reply,held,posts};
}
for(const kind of ['categories','manufacturers'])for(const language of ['en','de'] as const)test(`${kind} ${language}: participants, keyboard, paging, identity and payload`,async({page})=>{
 const t=language==='en'?en:de,f=await setup(page,kind,language);await page.getByRole('button',{name:t.actions.merge,exact:true}).click();await f.reply('');
 const dialog=page.getByRole('dialog'),source=dialog.getByRole('combobox',{name:t.ui.mergeSource,exact:true}),target=dialog.getByRole('combobox',{name:t.ui.mergeTarget,exact:true});
 await expect(dialog.locator('select')).toHaveCount(0);await source.focus();await source.evaluate(el=>(window as any).input=el);await dialog.evaluate(el=>(window as any).dialog=el);
 await expect(page.getByRole('option')).toHaveCount(2);await expect(dialog.getByRole('status')).toHaveCount(0);const popupNext=await dialog.getByRole('button',{name:t.ui.loadMore,exact:true}).boundingBox(),dialogBox=await dialog.boundingBox();expect(popupNext!.y+popupNext!.height).toBeLessThanOrEqual(dialogBox!.y+dialogBox!.height);await page.screenshot({path:`../.cache/frontend-f2/${language}-${kind}-fixture.png`});await expect(page.getByRole('option').first()).toContainText('aaaaaaa1');await expect(page.getByRole('option').last()).toContainText('Readable Tenant B');
 await source.press('ArrowDown');await source.press('ArrowDown');await source.press('ArrowUp');await source.press('Enter');await expect(source).toHaveValue('Same name');
 await source.click();await page.getByRole('dialog').getByRole('button',{name:t.ui.loadMore,exact:true}).click();await f.reply('',1,[{id:c,name:'Later page',tenantId:null}]);await expect(source).toHaveValue('Same name');
 await page.keyboard.press('Escape');await expect(source).toBeFocused();await expect(dialog).toBeVisible();await expect(source).toHaveAttribute('aria-expanded','false');
 await target.focus();await page.getByRole('option').filter({hasText:'bbbbbbb2'}).click();
 const preview=page.getByRole('button',{name:t.ui.previewMerge,exact:true});expect(await source.evaluate(el=>el===(window as any).input)).toBe(true);expect(await dialog.evaluate(el=>el===(window as any).dialog)).toBe(true);await preview.click();
 await expect(page.getByRole('button',{name:t.ui.confirmMerge,exact:true})).toBeVisible();await expect(page.getByRole('dialog')).toHaveCount(1);expect(await dialog.evaluate(el=>el===(window as any).dialog)).toBe(true);expect(f.posts.at(-1)?.body).toEqual({sourceId:a,targetId:b});
 await target.fill('new');await expect(page.getByRole('button',{name:t.ui.confirmMerge,exact:true})).toHaveCount(0);await expect(preview).toBeDisabled();await expect(target).toBeFocused();await expect(page.getByRole('option').first()).toHaveAttribute('aria-disabled','true');
 await f.reply('new',0,[{id:c,name:'New target',tenantId:null}]);await target.press('ArrowDown');await target.press('Enter');
 await preview.click();await page.getByRole('button',{name:t.ui.confirmMerge,exact:true}).click();
 await page.getByRole('alertdialog').getByRole('button').last().click();await expect.poll(()=>f.posts.some(post=>post.path.endsWith('/merge'))).toBe(true);expect(f.posts.at(-1)?.body).toEqual({sourceId:a,targetId:c});
 await expect(dialog).toHaveCount(0);
});
for(const kind of ['categories','manufacturers'])test(`${kind}: pending, late responses, empty/error and Tab focus`,async({page})=>{
 const f=await setup(page,kind);await page.getByRole('button',{name:en.actions.merge,exact:true}).click();const input=page.getByRole('combobox',{name:en.ui.mergeSource,exact:true});await input.focus();await expect(page.getByRole('status').filter({hasText:'Searching'})).toBeVisible();await f.reply('');
 await input.evaluate(el=>(window as any).input=el);await page.getByRole('option').first().evaluate(el=>(window as any).option=el);await input.fill('a');await expect(page.getByRole('dialog').getByRole('status')).toHaveCount(0);await expect(page.getByRole('dialog').getByRole('button',{name:'Filter',exact:true})).toHaveCount(0);expect(await page.getByRole('option').first().evaluate(el=>el===(window as any).option)).toBe(true);await expect(page.getByRole('option').first()).toHaveAttribute('aria-disabled','true');await page.getByRole('option').first().click({force:true});await expect(input).toHaveValue('a');await input.fill('ab');await f.reply('ab',0,[{id:c,name:'Newest',tenantId:null}]);await f.reply('a');
 await expect(page.getByRole('option')).toHaveCount(1);await expect(page.getByRole('option')).toContainText('Newest');await expect(input).toBeFocused();expect(await input.evaluate(el=>el===(window as any).input)).toBe(true);
 await input.fill('empty');await f.reply('empty',0,[],0);await expect(page.getByText(en.ui.noRecords,{exact:true}).last()).toBeVisible();await input.fill('error');await expect(page.getByText(en.ui.noRecords,{exact:true}).last()).toBeVisible();await expect(page.getByRole('dialog').getByRole('status')).toHaveCount(0);await f.reply('error',0,[],0,503);await expect(page.getByRole('dialog').getByRole('alert')).toBeVisible();await expect(page.getByRole('option')).toHaveCount(0);
 await input.press('Tab');await expect(input).toHaveAttribute('aria-expanded','false');await page.keyboard.press('Shift+Tab');await expect(input).toBeFocused();
});
test('identical explicit UUIDs cannot preview/execute; source edit invalidates preview',async({page})=>{
 const f=await setup(page,'categories');await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');await page.getByText(en.redesign.advancedIdentifiers,{exact:true}).click();
 await page.getByLabel(en.ui.mergeSourceIDExplicit,{exact:true}).fill(a);await page.getByLabel(en.ui.mergeTargetIDExplicit,{exact:true}).fill(a);await expect(page.getByRole('button',{name:en.ui.previewMerge,exact:true})).toBeDisabled();await expect(page.getByRole('button',{name:en.ui.confirmMerge,exact:true})).toHaveCount(0);expect(f.posts.length).toBe(0);
 await page.getByLabel(en.ui.mergeTargetIDExplicit,{exact:true}).fill(b);await page.getByRole('button',{name:en.ui.previewMerge,exact:true}).click();await expect(page.getByRole('button',{name:en.ui.confirmMerge,exact:true})).toBeVisible();await page.getByLabel(en.ui.mergeSourceIDExplicit,{exact:true}).fill(c);await expect(page.getByRole('button',{name:en.ui.confirmMerge,exact:true})).toHaveCount(0);
});
for(const language of ['en','de'] as const)test(`${language}: merge rejection explains Promote-first and preserves the selected IDs`,async({page})=>{
 const t=language==='en'?en:de,f=await setup(page,'categories',language);
 await page.route('**/catalog/categories/merge',route=>route.fulfill({status:409,json:{code:'MERGE_REQUIRES_GLOBAL_TARGET',detail:'Promote the intended survivor to Global first'}}));
 await page.getByRole('button',{name:t.actions.merge,exact:true}).click();await f.reply('');
 await page.getByText(t.redesign.advancedIdentifiers,{exact:true}).click();
 const source=page.getByLabel(t.ui.mergeSourceIDExplicit,{exact:true}),target=page.getByLabel(t.ui.mergeTargetIDExplicit,{exact:true});
 await source.fill(a);await target.fill(b);await page.getByRole('button',{name:t.ui.previewMerge,exact:true}).click();
 await page.getByRole('button',{name:t.ui.confirmMerge,exact:true}).click();
 await page.getByRole('alertdialog').getByRole('button').last().click();
 await expect(page.getByRole('dialog').getByRole('alert')).toContainText(t.errors.MERGE_REQUIRES_GLOBAL_TARGET);
 await expect(source).toHaveValue(a);await expect(target).toHaveValue(b);
});
test('GLOBAL USER has no Merge controls',async({page})=>{await setup(page,'categories','en','USER');await expect(page.getByRole('button',{name:en.actions.merge,exact:true})).toHaveCount(0);});

test('a late preview cannot restore an obsolete source/target pair',async({page})=>{
 const f=await setup(page,'categories');let previewRoute:Route|undefined;
 await page.route('**/merge-preview',route=>{previewRoute=route;});
 await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');await page.getByText(en.redesign.advancedIdentifiers,{exact:true}).click();
 await page.getByLabel(en.ui.mergeSourceIDExplicit,{exact:true}).fill(a);await page.getByLabel(en.ui.mergeTargetIDExplicit,{exact:true}).fill(b);
 await page.getByRole('button',{name:en.ui.previewMerge,exact:true}).click();
 await expect.poll(()=>!!previewRoute).toBe(true);
 await page.getByLabel(en.ui.mergeTargetIDExplicit,{exact:true}).fill(c);
 await previewRoute!.fulfill({json:{productCount:0,assetCount:0,affectedTenants:[],blockers:[]}});
 await expect(page.getByRole('button',{name:en.ui.previewMerge,exact:true})).toBeEnabled();await expect(page.getByRole('button',{name:en.ui.confirmMerge,exact:true})).toHaveCount(0);
});

for(const role of ['ADMIN','USER'])test(`TENANT ${role} has no explicit Merge controls`,async({page})=>{await setup(page,'manufacturers','en',role,'TENANT');await expect(page.getByRole('button',{name:en.actions.merge,exact:true})).toHaveCount(0);});

test('locale changes preserve combobox input and selected UUID',async({page})=>{
 const f=await setup(page,'categories');await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');
 const source=page.getByRole('combobox',{name:en.ui.mergeSource,exact:true});await source.focus();await source.press('ArrowDown');await source.press('Enter');await source.evaluate(el=>(window as any).input=el);
 await page.evaluate(async()=>{const {selectLanguage}=await import('/src/i18n.ts');await selectLanguage('de');});
 const translated=page.getByRole('combobox',{name:de.ui.mergeSource,exact:true});await expect(translated).toHaveValue('Same name');expect(await translated.evaluate(el=>el===(window as any).input)).toBe(true);
 await page.getByText(de.redesign.advancedIdentifiers,{exact:true}).click();await expect(page.getByLabel(de.ui.mergeSourceIDExplicit,{exact:true})).toHaveValue(a);
});
for(const kind of ['categories','manufacturers'])test(`${kind}: select a later-page candidate and reset paging when editing it`,async({page})=>{
 const f=await setup(page,kind);await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');
 const source=page.getByRole('combobox',{name:en.ui.mergeSource,exact:true});await source.focus();await page.getByRole('dialog').getByRole('button',{name:en.ui.loadMore,exact:true}).click();await f.reply('',1,[{id:c,name:'Later page',tenantId:null}]);
 await page.getByRole('option',{name:/Later page/}).click();await expect(source).toHaveValue('Later page');await page.getByText(en.redesign.advancedIdentifiers,{exact:true}).click();await expect(page.getByLabel(en.ui.mergeSourceIDExplicit,{exact:true})).toHaveValue(c);
 await source.fill('changed');await f.reply('changed',0,[{id:a,name:'Replacement',tenantId:null}]);await expect(page.getByLabel(en.ui.mergeSourceIDExplicit,{exact:true})).toHaveValue('');await expect(page.getByRole('option')).toContainText('Replacement');await expect(source).toBeFocused();
});

for(const kind of ['categories','manufacturers'])test(`${kind}: final confirmation Cancel is a silent pre-dispatch no-op`,async({page})=>{
 const f=await setup(page,kind);await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');
 const d=page.getByRole('dialog'),source=d.getByRole('combobox').first(),target=d.getByRole('combobox').last();await source.focus();await source.press('ArrowDown');await source.press('Enter');await target.focus();await target.press('ArrowDown');await target.press('Enter');await d.evaluate(el=>(window as any).workflow=el);
 await d.getByRole('button',{name:en.ui.previewMerge,exact:true}).click();await d.getByRole('button',{name:en.ui.confirmMerge,exact:true}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel,exact:true}).click();await expect(page.getByRole('alertdialog')).toHaveCount(0);await expect(d).toBeVisible();expect(await d.evaluate(el=>el===(window as any).workflow)).toBe(true);await expect(source).toHaveValue('Same name');await expect(target).toHaveValue('Same name');expect(f.posts.filter(r=>r.path.endsWith('/merge'))).toHaveLength(0);await expect(d.getByRole('alert')).toHaveCount(0);await expect(d.getByText(en.ui.draftHelp)).toHaveCount(0);await expect(d.getByRole('button',{name:en.ui.confirmMerge,exact:true})).toBeEnabled();
});
test('Preview text/dimensions stay stable while pending and duplicate submissions are blocked',async({page})=>{
 const f=await setup(page,'categories');let held:Route|undefined,count=0;await page.route('**/merge-preview',r=>{held=r;count++;});await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');await page.getByText(en.redesign.advancedIdentifiers,{exact:true}).click();await page.getByLabel(en.ui.mergeSourceIDExplicit).fill(a);await page.getByLabel(en.ui.mergeTargetIDExplicit).fill(b);
 const button=page.getByRole('button',{name:en.ui.previewMerge,exact:true}),before=await button.boundingBox();await button.click();await expect.poll(()=>!!held).toBe(true);await expect(button).toHaveText(en.ui.previewMerge);await expect(button).toHaveAttribute('aria-busy','true');await expect(button).toBeDisabled();expect((await button.boundingBox())!.width).toBe(before!.width);await button.evaluate(el=>el.closest('form')!.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));expect(count).toBe(1);await held!.fulfill({json:{productCount:0,assetCount:0,affectedTenants:[],blockers:[]}});await expect(button).toBeEnabled();held=undefined;await button.click();await expect.poll(()=>count).toBe(2);await held!.fulfill({status:503,json:{code:'SERVICE_UNAVAILABLE'}});await expect(page.getByRole('form',{name:en.ui.previewMerge,exact:true}).getByRole('alert')).toBeVisible();await expect(button).toHaveText(en.ui.previewMerge);await expect(button).toBeEnabled();
});
test('open candidate popup: first X enters discard; Escape closes only popup; rejection preserves session',async({page})=>{
 const f=await setup(page,'categories');await page.getByRole('button',{name:en.actions.merge,exact:true}).click();await f.reply('');const d=page.getByRole('dialog'),input=d.getByRole('combobox').first();await input.fill('Same');await f.reply('Same');await input.evaluate(el=>(window as any).sameInput=el);await input.press('Escape');await expect(page.getByRole('alertdialog')).toHaveCount(0);await expect(input).toHaveAttribute('aria-expanded','false');await input.click();await d.getByRole('button',{name:en.redesign.close,exact:true}).click();await expect(page.getByRole('alertdialog')).toBeVisible();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.cancel,exact:true}).click();await expect(input).toHaveValue('Same');expect(await input.evaluate(el=>el===(window as any).sameInput)).toBe(true);await input.click();await d.getByRole('button',{name:en.redesign.close,exact:true}).click();await page.getByRole('alertdialog').getByRole('button',{name:en.redesign.confirm,exact:true}).click();await expect(d).toHaveCount(0);
});
