import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
import {assetDetailFixture,tenantFixture} from './backend-fixtures';

const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',actor='cccccccc-cccc-cccc-cccc-cccccccccccc';
const instant='2026-10-13T12:34:56Z';
const event=(type:string,data:object,index:number)=>({id:`${index.toString().padStart(8,'0')}-dddd-dddd-dddd-dddddddddddd`,assetId:id,tenantId:tenant,type,occurredAt:'2026-10-13T14:00:00Z',createdAt:'2026-10-13T14:00:00Z',actorId:actor,actorDisplayName:'Historical actor',data,note:null});
const events=[
 event('REGISTERED',{before:null,after:{assetTag:'Asset fixture',lifecycle:'REGISTERED',condition:'UNKNOWN',loanable:false,category:{id:actor,name:'Category'},location:{id:actor,name:'Room'},owner:null,product:null,loanedAt:null,expectedReturnAt:null}},1),
 event('LOANED',{before:{loanedTo:null,expectedReturnAt:null},after:{loanedTo:'Borrower',expectedReturnAt:instant}},2),
 event('REGISTERED',{before:{note:'Prior imported note'},after:{note:'Recorded note'}},3)
];
async function setup(page:Page,language:'de'|'en',loaned=false){
 const writes:{path:string;body:any}[]=[];
 let asset=assetDetailFixture({id,tenantId:tenant,assetTag:'Asset fixture',version:2,lifecycle:'DEPLOYED',condition:'WORKING',loanable:true,loanedTo:loaned?'Borrower':null,loanedAt:loaned?'2026-10-13T12:00:00Z':null,expectedReturnAt:loaned?instant:null});
 await page.addInitScript(value=>localStorage.setItem('kelios.ui.language',value),language);
 await page.route('**/api/v1/**',route=>{const path=new URL(route.request().url()).pathname;
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:actor,scope:'GLOBAL',role:'ADMIN',status:'ACTIVE',tenantId:null,displayName:'Fixture'}}});
  if(route.request().method()!=='GET'){const body=route.request().postDataJSON();writes.push({path,body});if(path.endsWith('/loan'))asset={...asset,loanedTo:body.borrower,loanedAt:'2026-10-13T12:00:00Z',expectedReturnAt:body.expectedReturnAt,version:asset.version+1};return route.fulfill({json:asset});}
  if(path==='/api/v1/tenants')return route.fulfill({json:[tenantFixture(tenant,'School')]});
  if(path===`/api/v1/tenants/${tenant}`)return route.fulfill({json:tenantFixture(tenant,'School')});
  if(path===`/api/v1/assets/${id}`)return route.fulfill({json:asset});
  if(path===`/api/v1/assets/${id}/events`)return route.fulfill({json:{items:events,page:0,size:20,totalElements:events.length}});
  if(path==='/api/v1/assets')return route.fulfill({json:{items:[asset],page:0,size:20,totalElements:1}});
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 return writes;
}

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} checkout requires one date and keeps the compact dialog usable`,async({browser})=>{
 const context=await browser.newContext({timezoneId:'Europe/Berlin',viewport:{width,height:900}}),page=await context.newPage(),text=language==='de'?de:en,writes=await setup(page,language);
 await page.goto(`/inventory/assets/${id}`);await page.getByRole('button',{name:text.ui.loanAsset,exact:true}).click();
 const dialog=page.getByRole('dialog',{name:text.ui.loanAsset}),form=dialog.getByRole('form',{name:text.ui.loanAsset}),date=form.getByLabel(text.fields.expectedReturnAt,{exact:true}),submit=form.getByRole('button',{name:text.ui.loanAsset,exact:true});
 await expect(date).toHaveAttribute('type','date');await expect(date).toHaveAttribute('required','');await expect(form.locator('label').filter({hasText:text.fields.expectedReturnAt})).toContainText('*');
 await form.getByLabel(text.ui.borrower,{exact:true}).fill('Borrower');await expect(submit).toBeDisabled();await submit.press('Enter');expect(writes).toHaveLength(0);
 await date.fill('2026-10-25');const clear=form.getByRole('button',{name:text.actions.clearField.replace('{{field}}',text.fields.expectedReturnAt)});await clear.focus();await clear.press('Enter');await expect(date).toHaveValue('');await expect(date).toBeFocused();await expect(submit).toBeDisabled();
 await date.fill('2026-10-25');const box=await dialog.boundingBox();expect(box).toBeTruthy();expect(box!.width).toBeLessThanOrEqual(width===390?width-30:450);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await submit.click();await expect(dialog).toHaveCount(0);expect(writes).toHaveLength(1);expect(writes[0]).toEqual({path:`/api/v1/assets/${id}/loan`,body:{expectedVersion:2,borrower:'Borrower',expectedReturnAt:'2026-10-25T22:59:59.999Z',loanNote:null}});
 const dateText=new Intl.DateTimeFormat(language,{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'Europe/Berlin'}).format(new Date('2026-10-25T22:59:59.999Z'));
 const shown=page.locator('dt').filter({hasText:text.fields.expectedReturnAt}).locator('..').locator('dd');await expect(shown).toHaveText(dateText);await context.close();
});

for(const language of ['de','en'] as const)for(const width of [390,1440])test(`${language}/${width} registration shows initial state and transition history keeps diffs and separators`,async({browser})=>{
 const context=await browser.newContext({timezoneId:'Europe/Berlin',viewport:{width,height:900}}),page=await context.newPage(),text=language==='de'?de:en;await setup(page,language,true);
 await page.goto(`/inventory/assets/${id}`);await page.getByRole('tab',{name:text.redesign.history}).click();await expect(page.getByRole('heading',{name:text.ui.assetHistory,exact:true})).toHaveCount(0);
 const cards=page.locator('main ol > li'),initial=cards.nth(0),loan=cards.nth(1),legacy=cards.nth(2);
 await expect(initial.locator('thead th')).toHaveText([text.ui.field,text.ui.initialState]);await expect(initial.locator('tbody')).toContainText(text.enums.REGISTERED);await expect(initial.locator('tbody')).toContainText(text.enums.UNKNOWN);await expect(initial.locator('tbody')).toContainText(text.fields.false);await expect(initial.locator('tbody')).not.toContainText(text.ui.unavailable);await expect(initial.locator('tbody')).not.toContainText(text.fields.expectedReturnAt);
 await expect(loan.locator('thead th')).toHaveText([text.ui.field,text.ui.before,text.ui.after]);await expect(loan.locator('tbody tr').filter({hasText:text.fields.expectedReturnAt}).locator('td').last()).toHaveText(new Intl.DateTimeFormat(language,{year:'numeric',month:'2-digit',day:'2-digit',timeZone:'Europe/Berlin'}).format(new Date(instant)));
 await expect(legacy.locator('thead th')).toHaveText([text.ui.field,text.ui.before,text.ui.after]);await expect(legacy.locator('tbody')).toContainText('Prior imported note');
 for(const card of [initial,loan])expect(await card.locator('tbody tr').first().locator('td').first().evaluate(cell=>getComputedStyle(cell).borderInlineStartStyle)).toBe('solid');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.goto('/inventory/assets');await expect(page.locator('main .data-table tbody tr')).toHaveCount(1);
 expect(await page.locator('main .data-table tbody tr td').nth(1).evaluate(cell=>getComputedStyle(cell).borderInlineStartStyle)).toBe('solid');await context.close();
});
