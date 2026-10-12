import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
const id='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',tenant='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
async function fixture(page:Page,language='en',failure=0){
 let signed=false,oidc=false;const attempts:any[]=[],routes:string[]=[],business:string[]=[];
 await page.addInitScript(l=>localStorage.setItem('kelios.ui.language',l),language);
 await page.route('**/api/v1/**',route=>{
  const url=new URL(route.request().url());routes.push(url.pathname);
  if(url.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(url.pathname.endsWith('/session')&&route.request().method()==='DELETE'){signed=false;return route.fulfill({status:200,body:''});}
  if(url.pathname.endsWith('/session/refresh'))return signed?route.fulfill({json:{accessToken:'fixture-access',expiresIn:900,user:{id,scope:oidc?'TENANT':'GLOBAL',tenantId:oidc?tenant:null,role:oidc?'USER':'ADMIN',status:oidc?'PENDING':'ACTIVE',displayName:'Fixture'}}}):route.fulfill({status:401,json:{status:401,detail:'No session'}});
  if(url.pathname.endsWith('/local/login')){attempts.push(route.request().postDataJSON());if(failure)return route.fulfill({status:failure,json:{status:failure,detail:'PRIVATE'}});signed=true;return route.fulfill({json:{accessToken:'fixture-access',expiresIn:900,user:{id,scope:'GLOBAL',tenantId:null,role:'ADMIN',status:'ACTIVE',displayName:'Fixture'}}});}
  if(url.pathname.endsWith('/oidc/organizations'))return route.fulfill({json:{items:[{routingId:tenant,name:'Explicit provider organization'}],page:0,size:25,totalElements:1}});
  if(url.pathname.endsWith('/start')){signed=true;oidc=true;return route.fulfill({json:{authorizationUrl:'https://provider.fixture/authorize'}});}
  business.push(url.pathname);
  if(url.pathname==='/api/v1/tenants')return route.fulfill({json:[]});
  return route.fulfill({json:{items:[],page:0,size:20,totalElements:0}});
 });
 await page.route('https://provider.fixture/**',route=>route.fulfill({status:302,headers:{location:new URL('/auth/complete',String(test.info().project.use.baseURL)).href}}));
 return {attempts,routes,business};
}
for(const language of ['en','de']){
 test(`Email accessibility, keyboard login, refresh and logout (${language})`,async({page})=>{
  const f=await fixture(page,language),r=language==='de'?de:en;await page.goto('/categories');const email=page.getByLabel(r.redesign.email,{exact:true});
  await expect(email).toHaveAttribute('type','email');await expect(email).toHaveAttribute('name','email');await expect(email).toHaveAttribute('inputmode','email');await expect(email).toHaveAttribute('autocomplete','username');await expect(email).toHaveAttribute('maxlength','254');await expect(page.getByLabel(r.redesign.username,{exact:true})).toHaveCount(0);
  await email.fill('Mixed.Case@Example.org');const password=page.getByLabel(r.ui.password,{exact:true});await password.fill('  unchanged Password!1  ');await password.press('Enter');await expect(page.getByRole('heading',{name:r.fields.categories,exact:true})).toBeVisible();expect(f.attempts).toEqual([{email:'Mixed.Case@Example.org',password:'  unchanged Password!1  '}]);
  await page.reload();await expect(page.getByRole('heading',{name:r.fields.categories,exact:true})).toBeVisible();await page.getByRole('button',{name:r.redesign.accountMenu}).click();const loggedOut=page.waitForResponse(response=>response.url().endsWith('/api/v1/auth/session')&&response.request().method()==='DELETE');await page.getByRole('menuitem',{name:r.ui.signOut,exact:true}).click();await loggedOut;await expect(page.getByLabel(r.redesign.email,{exact:true})).toBeVisible();expect(f.routes).toContain('/api/v1/auth/session');
  const storage=await page.evaluate(()=>({local:Object.keys(localStorage),session:Object.keys(sessionStorage)}));expect(storage.local).toEqual(['kelios.ui.language']);expect(storage.session).toEqual([]);
 });
 test(`Email required/format/254 boundary prevents invalid submissions (${language})`,async({page})=>{
  const f=await fixture(page,language),r=language==='de'?de:en;await page.goto('/categories');const email=page.getByLabel(r.redesign.email,{exact:true}),password=page.getByLabel(r.ui.password,{exact:true});await password.fill('Fixture');await page.getByRole('button',{name:r.ui.signIn,exact:true}).click();expect(f.attempts).toHaveLength(0);await email.fill('not-an-email');await password.press('Enter');expect(f.attempts).toHaveLength(0);
  const long='a'.repeat(64)+'@'+'b'.repeat(63)+'.'+'c'.repeat(63)+'.'+'d'.repeat(61);expect(long).toHaveLength(254);await email.fill(long);await email.press('End');await email.press('x');await expect(email).toHaveValue(long);await password.press('Enter');await expect.poll(()=>f.attempts.length).toBe(1);expect(f.attempts[0].email).toBe(long);
 });
 for(const [status,key] of [[401,'LOGIN_FAILED'],[403,'ACCESS_DENIED'],[429,'THROTTLED']] as const)test(`Email ${status} sanitized login feedback (${language})`,async({page})=>{const f=await fixture(page,language,status),r=language==='de'?de:en;await page.goto('/categories');const email=page.getByLabel(r.redesign.email,{exact:true}),password=page.getByLabel(r.ui.password,{exact:true});await email.fill('fixture@example.org');await password.fill('Fixture');await password.press('Enter');await expect(page.getByRole('alert')).toHaveText(r.errors[key]);await expect(password).toHaveValue('');await expect(email).toHaveValue('fixture@example.org');expect(f.attempts).toHaveLength(1);expect(f.business).toEqual([]);await expect(page.getByText('PRIVATE')).toHaveCount(0);});
}
for(const known of [false,true])test(`OIDC routing stays explicit and Tenant pending has no local credentials (known=${known})`,async({page})=>{const f=await fixture(page);await page.goto(known?`/login/tenant/${tenant}`:'/login');await page.getByRole('button',{name:en.ui.signInWithIServ,exact:true}).click();if(!known){await expect(page.getByRole('dialog')).toBeVisible();expect(f.routes.some(x=>x.endsWith('/start'))).toBe(false);await page.getByRole('button',{name:/Explicit provider organization/}).click();}await expect(page.getByText(en.ui.pendingHelp,{exact:true})).toBeVisible();expect(f.routes).toContain(`/api/v1/auth/oidc/${tenant}/start`);expect(f.attempts).toEqual([]);expect(f.business).toEqual([]);await expect(page.getByLabel(en.redesign.email,{exact:true})).toHaveCount(0);});
for(const language of ['en','de'])for(const width of [1440,1024,768,390])test(`Email login label/focus/layout ${language} ${width}`,async({page})=>{
 await page.setViewportSize({width,height:900});await fixture(page,language);const r=language==='de'?de:en;await page.goto('/login');const email=page.getByLabel(r.redesign.email,{exact:true});await expect(email).toBeVisible();await email.focus();await expect(email).toBeFocused();await email.press('Tab');await expect(page.getByLabel(r.ui.password,{exact:true})).toBeFocused();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:`../.cache/frontend-f1a/login-${language}-${width}.png`});
});
