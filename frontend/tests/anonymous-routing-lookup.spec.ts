import {test,expect,type Page,type Route} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const id=(n:number)=>`00000000-0000-0000-0000-${(n+1).toString(16).padStart(12,'0')}`;
async function fixture(page:Page,{language='en',total=45}={}){
 const organizations=Array.from({length:total},(_,n)=>({routingId:id(n),name:n===0||n===20?'Duplicate organization':n===5?'Literal %_\\ marker':`Organization ${String(n).padStart(2,'0')}`}));
 const requests:URL[]=[],starts:string[]=[],business:string[]=[],held:{url:URL;route:Route}[]=[];let hold:(url:URL)=>boolean=()=>false,fail=false;
 await page.addInitScript(l=>localStorage.setItem('kelios.ui.language',l),language);
 function response(url:URL){const q=url.searchParams.get('q')??'',page=Number(url.searchParams.get('page')),size=Number(url.searchParams.get('size'));const rows=q==='server-only'?organizations.slice(0,1):organizations.filter(org=>org.name.toLowerCase().includes(q.toLowerCase()));return{items:rows.slice(page*size,(page+1)*size),page,size,totalElements:rows.length};}
 await page.route('**/api/v1/**',route=>{const url=new URL(route.request().url()),path=url.pathname;
  if(path.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(path.endsWith('/session/refresh'))return route.fulfill({status:401,json:{status:401}});
  if(path.endsWith('/session')&&route.request().method()==='DELETE')return route.fulfill({status:204});
  if(path.endsWith('/oidc/organizations')){requests.push(url);expect(route.request().headers().authorization).toBeUndefined();if(hold(url)){held.push({url,route});return;}if(fail){fail=false;return route.fulfill({status:503,json:{status:503}});}return route.fulfill({json:response(url)});}
  if(path.endsWith('/start')){starts.push(path);return route.fulfill({status:503,json:{status:503}});}
  business.push(path);return route.fulfill({status:500,json:{}});
 });
 return{requests,starts,business,held,hold:(value:(url:URL)=>boolean)=>{hold=value;},fail:()=>{fail=true;},release:async(n:number)=>{await held[n].route.fulfill({json:response(held[n].url)});}};
}
async function open(page:Page,language='en'){
 const r=language==='de'?de:en;await page.goto('/login');const trigger=page.getByRole('button',{name:r.ui.signInWithIServ,exact:true});await expect(trigger).toBeEnabled();await trigger.click();const dialog=page.getByRole('dialog');await expect(dialog.getByRole('list')).toBeVisible();return dialog;
}
const options=(page:Page)=>page.getByRole('dialog').locator('button[data-routing-id]');
async function boundary(page:Page){await page.getByRole('dialog').getByRole('list').evaluate(node=>{node.scrollTop=node.scrollHeight;node.dispatchEvent(new Event('scroll'));});}
test('batch20 append, partial final Page, duplicate names, selected ID and mounted focus/scroll',async({page})=>{
 const f=await fixture(page);const dialog=await open(page);await expect(options(page)).toHaveCount(20);expect(Object.fromEntries(f.requests[0].searchParams)).toEqual({page:'0',size:'20',q:''});expect(f.starts).toEqual([]);
 const first=dialog.locator(`[data-routing-id="${id(0)}"]`),input=dialog.getByLabel(en.redesign.searchOrganizations,{exact:true});await first.evaluate(node=>(window as any).option=node);await input.evaluate(node=>(window as any).input=node);
 await first.click();await expect.poll(()=>f.starts.length).toBe(1);await expect(first).toBeEnabled();await expect(first).toHaveAttribute('aria-pressed','true');expect(f.starts[0]).toBe(`/api/v1/auth/oidc/${id(0)}/start`);
 f.hold(url=>url.searchParams.get('page')==='1');await input.focus();await boundary(page);await expect.poll(()=>f.held.length).toBe(1);await boundary(page);await expect(options(page)).toHaveCount(20);await expect(first).toBeEnabled();expect(f.requests).toHaveLength(2);await f.release(0);await expect(options(page)).toHaveCount(40);
 expect(await first.evaluate(node=>node===(window as any).option)).toBe(true);expect(await input.evaluate(node=>node===(window as any).input)).toBe(true);await expect(input).toBeFocused();await expect(first).toHaveAttribute('aria-pressed','true');await expect(first).toContainText(id(0));await expect(dialog.locator(`[data-routing-id="${id(20)}"]`)).toContainText(id(20));expect(await dialog.getByRole('list').evaluate(node=>node.scrollTop)).toBeGreaterThan(0);
 f.hold(()=>false);await boundary(page);await expect(options(page)).toHaveCount(45);await expect(dialog.getByRole('button',{name:en.ui.loadMore,exact:true})).toBeDisabled();await boundary(page);expect(f.requests).toHaveLength(3);expect(f.requests.map(url=>url.searchParams.get('page'))).toEqual(['0','1','2']);expect(f.business).toEqual([]);
});
for(const total of [0,20,40])test(`returned count ends at exact ${total} boundary`,async({page})=>{
 const f=await fixture(page,{total});const dialog=await open(page);await expect(options(page)).toHaveCount(Math.min(total,20));if(total===40){await dialog.getByRole('button',{name:en.ui.loadMore,exact:true}).focus();await page.keyboard.press('Enter');await expect(options(page)).toHaveCount(40);await expect(dialog.getByRole('button',{name:en.ui.loadMore,exact:true})).toBeDisabled();}else await expect(dialog.getByRole('button',{name:en.ui.loadMore,exact:true})).toHaveCount(0);
 await boundary(page);expect(f.requests).toHaveLength(total===40?2:1);if(!total)await expect(dialog).toContainText(en.redesign.noOrganizations);
});
test('trim/blank/literal and128-unit input bound; server result is not locally filtered',async({page})=>{
 const f=await fixture(page);const dialog=await open(page),input=dialog.getByLabel(en.redesign.searchOrganizations,{exact:true});await input.fill('   ');await page.waitForTimeout(200);expect(f.requests).toHaveLength(1);
 await input.fill('  literal %_\\  ');await expect.poll(()=>f.requests.at(-1)!.searchParams.get('q')).toBe('literal %_\\');await expect(options(page)).toHaveCount(1);await expect(dialog).toContainText('Literal %_\\ marker');
 await input.fill(' server-only ');await expect.poll(()=>f.requests.at(-1)!.searchParams.get('q')).toBe('server-only');await expect(options(page)).toHaveCount(1);await expect(dialog).toContainText('Duplicate organization');
 await expect(input).toHaveAttribute('maxlength','128');await input.fill('x'.repeat(128));await expect.poll(()=>f.requests.at(-1)!.searchParams.get('q')).toBe('x'.repeat(128));await expect(options(page)).toHaveCount(0);const count=f.requests.length;await input.press('End');await input.press('x');await expect(input).toHaveValue('x'.repeat(128));expect(f.requests).toHaveLength(count);
 await input.fill('');await expect(options(page)).toHaveCount(20);expect(f.requests.at(-1)!.searchParams.get('q')).toBe('');expect(f.business).toEqual([]);
});
test('late query and continuation cannot replace or append into new query',async({page})=>{
 const f=await fixture(page);const dialog=await open(page),input=dialog.getByLabel(en.redesign.searchOrganizations,{exact:true});f.hold(()=>true);await boundary(page);await expect.poll(()=>f.held.length).toBe(1);await input.fill('old');await expect.poll(()=>f.held.length).toBe(2);await input.fill('server-only');await expect.poll(()=>f.held.length).toBe(3);await f.release(2);await expect(options(page)).toHaveCount(1);await f.release(0);await f.release(1);await expect(options(page)).toHaveCount(1);await expect(dialog).toContainText('Duplicate organization');await expect(input).toHaveValue('server-only');expect(f.starts).toEqual([]);
});
for(const change of ['session','route','close'] as const)test(`${change} invalidates an old continuation and restarts bounded initial routing`,async({page})=>{
 const f=await fixture(page);await open(page);f.hold(url=>url.searchParams.get('page')==='1');await boundary(page);await expect.poll(()=>f.held.length).toBe(1);
 if(change==='session')await page.evaluate(async()=>{const {authSession}=await import('/src/auth.ts');await authSession.logout();});
 if(change==='route')await page.evaluate(path=>{history.pushState(null,'',path);dispatchEvent(new PopStateEvent('popstate'));},`/login/tenant/${id(9)}`);
 if(change==='close'){await page.keyboard.press('Escape');await page.getByRole('button',{name:en.ui.signInWithIServ,exact:true}).click();}
 await expect.poll(()=>f.requests.length).toBe(3);await expect(options(page)).toHaveCount(20);await f.release(0);await expect(options(page)).toHaveCount(20);expect(f.starts).toEqual([]);
 if(change==='route'){await page.keyboard.press('Escape');const count=f.requests.length;await page.getByRole('button',{name:en.ui.signInWithIServ,exact:true}).click();await expect.poll(()=>f.starts.length).toBe(1);expect(f.starts[0]).toBe(`/api/v1/auth/oidc/${id(9)}/start`);expect(f.requests).toHaveLength(count);}
});
test('continuation failure retains current options and retries only on explicit request',async({page})=>{
 const f=await fixture(page);const dialog=await open(page);f.fail();await dialog.getByRole('button',{name:en.ui.loadMore,exact:true}).click();await expect(dialog.getByRole('alert')).toContainText(en.redesign.organizationsUnavailable);await expect(options(page)).toHaveCount(20);await expect(options(page).first()).toBeEnabled();expect(f.requests).toHaveLength(2);await dialog.getByRole('button',{name:en.ui.loadMore,exact:true}).click();await expect(options(page)).toHaveCount(40);expect(f.requests).toHaveLength(3);
});
for(const language of ['en','de'])for(const width of [1440,1024,768,390])test(`${language}/${width} keyboard routing, stable controls, readable duplicates and focus return`,async({page})=>{
 await page.setViewportSize({width,height:900});const f=await fixture(page,{language}),r=language==='de'?de:en;const dialog=await open(page,language),input=dialog.getByLabel(r.redesign.searchOrganizations,{exact:true});await expect(input).toBeFocused();await input.evaluate(node=>(window as any).input=node);await input.fill('  organization  ');await expect.poll(()=>f.requests.at(-1)!.searchParams.get('q')).toBe('organization');await expect(options(page)).toHaveCount(20);await input.press('Tab');const clear=dialog.getByRole('button',{name:r.actions.clearField.replace('{{field}}',r.redesign.searchOrganizations)});await expect(clear).toBeFocused();await clear.press('Tab');const first=options(page).first();await expect(first).toBeFocused();await first.evaluate(node=>(window as any).option=node);
 await boundary(page);await expect(options(page)).toHaveCount(40);expect(await first.evaluate(node=>node===(window as any).option)).toBe(true);await expect(first).toBeFocused();expect(await input.evaluate(node=>node===(window as any).input)).toBe(true);await page.keyboard.press('Enter');await expect.poll(()=>f.starts.length).toBe(1);expect(f.starts[0]).toBe(`/api/v1/auth/oidc/${id(0)}/start`);await expect(first).toHaveAttribute('aria-pressed','true');await expect(first).toContainText(id(0));
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.cache/frontend-f2-routing/${language}-${width}.png`});await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);await expect(page.getByRole('button',{name:r.ui.signInWithIServ,exact:true})).toBeFocused();expect(f.business).toEqual([]);
});

test('initial request failure has meaningful feedback and never infers a Provider',async({page})=>{
 const f=await fixture(page);f.fail();await page.goto('/login');await page.getByRole('button',{name:en.ui.signInWithIServ,exact:true}).click();await expect(page.getByRole('dialog').getByRole('alert')).toContainText(en.redesign.organizationsUnavailable);await expect(options(page)).toHaveCount(0);expect(f.starts).toEqual([]);expect(f.requests).toHaveLength(1);
});

test('returning to an earlier query restarts page0 rather than reusing its continuation cursor',async({page})=>{
 const f=await fixture(page);const dialog=await open(page),input=dialog.getByLabel(en.redesign.searchOrganizations,{exact:true});await dialog.getByRole('button',{name:en.ui.loadMore,exact:true}).click();await expect(options(page)).toHaveCount(40);await input.fill('server-only');await expect(options(page)).toHaveCount(1);await input.fill('');await expect(options(page)).toHaveCount(20);expect(f.requests.map(url=>url.searchParams.get('page'))).toEqual(['0','1','0','0']);expect(f.starts).toEqual([]);
});
for(const language of ['en','de'] as const)test(`${language} routing search X clears only query`,async({page})=>{
 const t=language==='en'?en:de,f=await fixture(page,{language}),dialog=await open(page,language),input=dialog.getByLabel(t.redesign.searchOrganizations,{exact:true});
 await input.fill('server-only');await expect.poll(()=>f.requests.at(-1)?.searchParams.get('q')).toBe('server-only');
 const clear=dialog.getByRole('button',{name:t.actions.clearField.replace('{{field}}',t.redesign.searchOrganizations)});
 await clear.focus();await clear.press('Enter');await expect(input).toBeFocused();await expect(input).toHaveValue('');
 await expect.poll(()=>f.requests.at(-1)?.searchParams.get('q')).toBe('');expect(f.starts).toHaveLength(0);
});
