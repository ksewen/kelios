import {test,expect,type Route} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
const tenant='aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',id='bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
test('Assets headers use one server collection query: applied filters, sort, paging and late response safety',async({page})=>{
 const requests:{url:URL;route:Route}[]=[];
 await page.addInitScript(()=>localStorage.setItem('kelios.ui.language','en'));
 await page.route('**/api/v1/**',r=>{const url=new URL(r.request().url());if(url.pathname.endsWith('/csrf'))return r.fulfill({json:{token:'fixture'}});if(url.pathname.endsWith('/session/refresh'))return r.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id,scope:'GLOBAL',role:'ADMIN',status:'ACTIVE',tenantId:null,displayName:'Fixture'}}});if(url.pathname==='/api/v1/tenants')return r.fulfill({json:[{id:tenant,name:'Workspace'}]});requests.push({url,route:r});});
 const reply=async(n:number,tags=['Z-server-first','A-server-second'])=>requests[n].route.fulfill({json:{items:tags.map((tag,i)=>({id:id.slice(0,-1)+i,assetTag:tag,createdAt:'2026-10-10T00:00:00Z'})),page:Number(requests[n].url.searchParams.get('page')),size:20,totalElements:75}});
 await page.goto(`/tenants/${tenant}/assets`);await expect.poll(()=>requests.length).toBe(1);await reply(0);
 const input=page.getByLabel(en.ui.assetIdentifiers),sort=page.getByRole('columnheader',{name:new RegExp(en.fields.assetTag)}).getByRole('button');
 await input.fill('HP');await input.press('Enter');await expect.poll(()=>requests.length).toBe(2);await reply(1);
 await page.getByRole('button',{name:en.ui.nextPage,exact:true}).click();await expect.poll(()=>requests.length).toBe(3);expect(requests[2].url.searchParams.get('q')).toBe('HP');expect(requests[2].url.searchParams.get('sort')).toBe('createdAt');expect(requests[2].url.searchParams.get('page')).toBe('1');await reply(2);
 await input.fill('Dell');expect(requests.length).toBe(3);await sort.click();await expect.poll(()=>requests.length).toBe(4);expect(Object.fromEntries(requests[3].url.searchParams)).toMatchObject({q:'HP',sort:'tag',direction:'asc',page:'0',size:'20'});await reply(3);
 await expect(page.locator('tbody tr').first()).toContainText('Z-server-first');await expect(sort.locator('..')).toHaveAttribute('aria-sort','ascending');
 await sort.click();await expect.poll(()=>requests.length).toBe(5);expect(Object.fromEntries(requests[4].url.searchParams)).toMatchObject({q:'HP',sort:'tag',direction:'desc',page:'0'});
 await page.getByRole('button',{name:en.ui.nextPage,exact:true}).click();await expect.poll(()=>requests.length).toBe(6);expect(Object.fromEntries(requests[5].url.searchParams)).toMatchObject({q:'HP',sort:'tag',direction:'desc',page:'1'});await reply(5,['Newest server slice']);await reply(4,['Obsolete server slice']);await expect(page.getByText('Newest server slice',{exact:true})).toBeVisible();await expect(page.getByText('Obsolete server slice')).toHaveCount(0);
 await input.press('Enter');await expect.poll(()=>requests.length).toBe(7);expect(Object.fromEntries(requests[6].url.searchParams)).toMatchObject({q:'Dell',sort:'tag',direction:'desc',page:'0'});await reply(6);
});
