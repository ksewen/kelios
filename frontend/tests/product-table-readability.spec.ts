import {test,expect,type Page} from '@playwright/test';
import en from '../src/locales/en.json' with {type:'json'};
import de from '../src/locales/de.json' with {type:'json'};
import {fixtureActor,labelFixture} from './backend-fixtures';
const uuid=(n:number)=>`bbbbbbbb-bbbb-bbbb-bbbb-${String(n).padStart(12,'0')}`;
async function fixture(page:Page,language:'en'|'de'){
 const name=language==='de'?'Lenovo ThinkPad L14 Gen 5 – Unterrichtsnotebook mit erweiterter Herstellergarantie':'Lenovo ThinkPad L14 Gen 5 – Classroom laptop with extended manufacturer warranty';
 const category='Notebooks für naturwissenschaftliche Fachräume / Science classroom notebooks',manufacturer='Lenovo Deutschland – Education & Campus Solutions';
 const rows=[1,2,3,4].map(n=>({...labelFixture(uuid(n),n<3?name:n===3?'HP EliteBook 840 G11':'ClassroomNotebookWithExtendedManufacturerWarrantyAndCampusSupport',n===3?uuid(40):null),manufacturerNumber:n===3?null:'21L1004HGE-CAMPUS-EDUCATION',categoryId:uuid(10+n),category:{id:uuid(10+n),name:n===3?'':category},manufacturerId:uuid(20+n),manufacturer:{id:uuid(20+n),name:n===3?'':manufacturer}}));
 const reads:URL[]=[],writes:string[]=[];
 await page.addInitScript(l=>localStorage.setItem('kelios.ui.language',l),language);
 await page.route('**/api/v1/**',route=>{const request=route.request(),u=new URL(request.url());
  if(u.pathname.endsWith('/csrf'))return route.fulfill({json:{token:'fixture'}});
  if(u.pathname.endsWith('/session/refresh'))return route.fulfill({json:{accessToken:'fixture',expiresIn:900,user:{id:fixtureActor,scope:'GLOBAL',tenantId:null,role:'ADMIN',status:'ACTIVE',displayName:'Fixture'}}});
  if(request.method()!=='GET'){writes.push(u.pathname);return route.fulfill({status:405,json:{status:405}});}
  reads.push(u);
  if(u.pathname==='/api/v1/tenants')return route.fulfill({json:[]});
  const row=rows.find(r=>u.pathname===`/api/v1/catalog/products/${r.id}`);
  if(row)return route.fulfill({json:{...row,deleted:false,deletedAt:null,deletedBy:null}});
  const items=u.pathname.endsWith('/products')?rows:[labelFixture(uuid(31),'Lenovo',null)];
  return route.fulfill({json:{items,page:Number(u.searchParams.get('page')??0),size:20,totalElements:44}});
 });
 return {rows,reads,writes,category,manufacturer};
}
for(const language of ['de','en'] as const)for(const width of [390,768,1024,1440])test(`${language} Product Name readable and stable at ${width}`,async({page},info)=>{
 await page.setViewportSize({width,height:900});const f=await fixture(page,language),r=language==='de'?de:en;
 await page.goto('/products');const row=page.locator('tbody tr').first(),name=row.locator('td').first();await expect(name).toContainText(f.rows[0].name);
 expect(await page.getByRole('columnheader').allTextContents()).toEqual([r.fields.name,r.fields.tenantName,r.fields.manufacturerName,r.fields.categories,r.fields.manufacturerNumber,r.fields.createdAt]);
 expect(await page.locator('tbody tr').nth(2).locator('td').allTextContents()).toEqual(['HP EliteBook 840 G11','Fixture Tenant','—','—','—',expect.any(String)]);
 await expect(page.getByText(r.redesign.resourceHelp)).toHaveCount(0);
 await expect(page.locator('tbody tr').nth(1).locator('td').first()).toContainText('00000002');await expect(name).toContainText('00000001');
 const geometry=await name.evaluate(e=>({width:e.getBoundingClientRect().width,height:e.getBoundingClientRect().height,wrap:getComputedStyle(e).overflowWrap}));
 expect(geometry.width).toBeGreaterThanOrEqual(224);expect(geometry.wrap).toBe('anywhere');if(width===390)expect(geometry.height).toBeLessThan(150);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
 const frame=page.locator('.table-frame');expect(await frame.evaluate(e=>getComputedStyle(e).overflowX)).toBe('auto');
 await page.screenshot({path:info.outputPath(`${language}-${width}.png`),fullPage:true});
 const input=page.getByRole('textbox',{name:r.ui.productSearch,exact:true});await input.fill('Pending text');await input.evaluate(e=>{(window as any).o2Input=e;(e as HTMLInputElement).setSelectionRange(2,7);});await row.evaluate(e=>(window as any).o2Row=e);
 await frame.evaluate(e=>e.scrollLeft=60);const scroll=await frame.evaluate(e=>e.scrollLeft);const count=f.reads.length;
 await input.press('ArrowRight');await input.press('x');await expect(input).toHaveValue('Pendingx text');
 expect(f.reads.length).toBe(count);expect(await input.evaluate(e=>e===(window as any).o2Input&&document.activeElement===e)).toBe(true);expect(await row.evaluate(e=>e===(window as any).o2Row)).toBe(true);expect(await frame.evaluate(e=>e.scrollLeft)).toBe(scroll);
 await input.evaluate(e=>(e as HTMLInputElement).setSelectionRange(2,7));
 // Pending criteria do not leak into a server sort. Refresh retains semantic row/input nodes.
 const beforeSort=f.reads.filter(u=>u.pathname.endsWith('/products')).length;await page.getByRole('columnheader').first().getByRole('button').click();await expect.poll(()=>f.reads.filter(u=>u.pathname.endsWith('/products')).length).toBeGreaterThan(beforeSort);
 const sorted=f.reads.filter(u=>u.pathname.endsWith('/products')).at(-1)!;expect(sorted.searchParams.get('q')).toBeNull();expect(sorted.searchParams.get('sort')).toBe('name');expect(sorted.searchParams.get('page')).toBe('0');
 await expect(input).toHaveValue('Pendingx text');expect(await input.evaluate(e=>[(e as HTMLInputElement).selectionStart,(e as HTMLInputElement).selectionEnd])).toEqual([2,7]);expect(await row.evaluate(e=>e===(window as any).o2Row)).toBe(true);expect(await input.evaluate(e=>e===(window as any).o2Input)).toBe(true);
 // The Product Name link remains keyboard reachable inside the horizontally scrolling table.
 const open=row.getByRole('link');await open.focus();await expect(open).toBeFocused();await open.press('Enter');await expect(page).toHaveURL(new RegExp(`/products/${uuid(1)}$`));
 await expect(page.locator(`a[href="/categories/${uuid(11)}"]`)).toHaveText(f.category);await expect(page.locator(`a[href="/manufacturers/${uuid(21)}"]`)).toHaveText(f.manufacturer);expect(f.writes).toEqual([]);
});
for(const kind of ['categories','manufacturers'])test(`${kind} does not inherit Product-only Name width`,async({page})=>{
 await page.setViewportSize({width:390,height:900});await fixture(page,'en');await page.goto(`/${kind}`);const name=page.locator('tbody tr td').first();await expect(name).toHaveText('Lenovo');expect(await name.evaluate(e=>getComputedStyle(e).minWidth)).toBe('0px');await expect(page.locator('.product-management')).toHaveCount(0);
});
