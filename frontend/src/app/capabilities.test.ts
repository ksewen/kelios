import {describe,it,expect} from 'vitest';
import {capabilities,tenantBusinessContext} from './capabilities';
import type {Identity} from '../auth';
describe('administrative destinations are independent of Tenant read capability',()=>{
 for(const scope of ['GLOBAL','TENANT'] as const)for(const role of ['ADMIN','USER'] as const)for(const status of ['ACTIVE','PENDING','DISABLED'] as const)it(`${scope}/${role}/${status}`,()=>{
 const user:Identity={id:'user',scope,role,status,tenantId:scope==='TENANT'?'signed':null,displayName:'Name'};const c=capabilities(user);
 expect(c.tenantCollection).toBe(status==='ACTIVE'&&scope==='GLOBAL');expect(c.tenantDetail).toBe(status==='ACTIVE'&&(scope==='GLOBAL'||role==='ADMIN'));
 expect(c.globalAdmin).toBe(status==='ACTIVE'&&scope==='GLOBAL'&&role==='ADMIN');expect(c.oidc).toBe(status==='ACTIVE'&&role==='ADMIN');expect(c.users).toBe(c.oidc);expect(c.audit).toBe(c.oidc);
 });
 it('Tenant detail is not an operating-context selection; signed Tenant overrides business URL',()=>{
 const global:Identity={id:'user',scope:'GLOBAL',role:'USER',status:'ACTIVE',tenantId:null,displayName:'Name'};
 expect(tenantBusinessContext('/tenants/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',global)).toBe('');expect(tenantBusinessContext('/tenants/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/',global)).toBe('');expect(tenantBusinessContext('/tenants/aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa/assets',global)).toBe('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa');
 expect(tenantBusinessContext('/tenants/foreign/assets',{...global,scope:'TENANT',tenantId:'signed'})).toBe('signed');
 });
});
