import {test,expect,type Page,type Route} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
async function fixture(page:Page,kind:string){
 const held:{url:URL;route:Route}[]=[];
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url());if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});if(url.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope:'GLOBAL',role:'ADMIN',status:'ACTIVE',tenantId:null,displayName:'Fixture'}}});if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[{id:tenant,name:'Workspace'}]});held.push({url,route});});
 await page.goto(`/tenants/${tenant}/${kind}`);await expect.poll(()=>held.length).toBe(1);
 const reply=async(index:number)=>held[index].route.fulfill({json:{items:[{id,name:'Stable',assetTag:'Stable'}],totalElements:75,page:Number(held[index].url.searchParams.get('page')),size:25}});
 await reply(0);await expect(page.locator('main table')).toBeVisible();return {held,reply};
}
for(const kind of ['categories','manufacturers','products','locations','owners','assets'])test(`${kind}: pending filter is quiet, applied filter owns requests/paging and identity`,async({page})=>{
 const f=await fixture(page,kind),toolbar=page.getByRole('toolbar'),input=toolbar.getByLabel(kind==='assets'?en.ui.assetIdentifiers:en.ui.name,{exact:true}),filter=toolbar.getByRole('button',{name:'Filter',exact:true});
 await input.evaluate(node=>(window as any).filterInput=node);await page.locator('main table').evaluate(node=>(window as any).table=node);
 await input.fill('HP');await input.press('Tab');expect(f.held.length).toBe(1);await expect(page.getByRole('alertdialog')).toHaveCount(0);
 await filter.click();await expect.poll(()=>f.held.length).toBe(2);expect(f.held[1].url.searchParams.get('q')).toBe('HP');await expect(page.getByText(en.redesign.updatingResults)).toHaveCount(0);expect(await page.locator('main table').evaluate(node=>node===(window as any).table)).toBe(true);await f.reply(1);
 await page.getByRole('button',{name:en.ui.nextPage,exact:true}).click();await expect.poll(()=>f.held.length).toBe(3);expect(f.held[2].url.searchParams.get('page')).toBe('1');await f.reply(2);
 await input.fill('Dell');await input.press('Tab');expect(f.held.length).toBe(3);await expect(page.getByText(/2.*75/)).toBeVisible();
 await input.press('Enter');await expect.poll(()=>f.held.length).toBe(4);expect(f.held[3].url.searchParams.get('page')).toBe('0');expect(f.held[3].url.searchParams.get('q')).toBe('Dell');await f.reply(3);
 await input.press('Enter');await input.press('Tab');expect(f.held.length).toBe(4);
 await input.fill('');await filter.click();await expect.poll(()=>f.held.length).toBe(5);expect(f.held[4].url.searchParams.get('q')??'').toBe('');await f.reply(4);
 expect(await input.evaluate(node=>node===(window as any).filterInput)).toBe(true);await expect(page.locator('[data-unsaved=true]')).toHaveCount(0);
});

test('Asset advanced filters remain pending until explicitly applied; sorting uses applied filters',async({page})=>{
 const f=await fixture(page,'assets');await page.getByRole('button',{name:en.actions.moreFilters,exact:true}).click();const d=page.getByRole('dialog');await d.getByLabel(en.ui.lifecycleFilter).selectOption('DEPLOYED');await d.getByLabel(en.ui.conditionFilter).selectOption('WORKING');await d.getByLabel(en.ui.loanedFilter).selectOption('false');expect(f.held.length).toBe(1);await d.getByRole('button',{name:'Filter',exact:true}).click();await expect.poll(()=>f.held.length).toBe(2);expect(Object.fromEntries(f.held[1].url.searchParams)).toMatchObject({lifecycle:'DEPLOYED',condition:'WORKING',loaned:'false',page:'0',sort:'createdAt',direction:'desc'});await f.reply(1);
 await page.getByRole('toolbar').getByLabel(en.ui.assetIdentifiers).fill('Unapplied');await page.getByRole('columnheader',{name:en.fields.createdAt}).getByRole('button').click();await expect.poll(()=>f.held.length).toBe(3);expect(f.held[2].url.searchParams.get('q')).toBeNull();expect(Object.fromEntries(f.held[2].url.searchParams)).toMatchObject({lifecycle:'DEPLOYED',condition:'WORKING',loaned:'false',sort:'createdAt',direction:'asc',page:'0'});await f.reply(2);
});

test('Asset entity references keep immediate bounded lookup without Filter controls',async({page})=>{
 const f=await fixture(page,'assets');await page.getByRole('toolbar').getByRole('button',{name:en.actions.create,exact:true}).click();
 for(const name of [en.fields.category,en.fields.manufacturer,en.fields.location,en.fields.owner,en.fields.product]){
  if(name===en.fields.product)await page.getByLabel(en.ui.catalogSelection).selectOption('Product');
  const input=page.getByRole('combobox',{name,exact:true});await input.evaluate(node=>(window as any).lookup=node);const before=f.held.length;await input.fill(name);
  await expect.poll(()=>f.held.slice(before).some(r=>r.url.searchParams.get('q')===name)).toBe(true);const request=f.held.slice(before).find(r=>r.url.searchParams.get('q')===name)!;expect(request.url.searchParams.get('size')).toBe('25');await request.route.fulfill({json:{items:[{id,name:'Candidate'}],totalElements:1,page:0,size:25}});await expect(page.getByRole('option',{name:/Candidate/})).toBeVisible();await expect(input).toBeFocused();expect(await input.evaluate(node=>node===(window as any).lookup)).toBe(true);await expect(page.getByRole('button',{name:'Filter',exact:true})).toHaveCount(0);await expect(page.getByText(en.redesign.updatingResults)).toHaveCount(0);
 }
});
