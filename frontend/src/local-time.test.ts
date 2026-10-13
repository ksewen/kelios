import {describe,it,expect} from 'vitest';
import {execFileSync} from 'node:child_process';
import {localInstant,auditBounds,validLocalInput} from './local-time';
const module=new URL('./local-time.ts',import.meta.url).href;
function inZone(zone:string,expression:string){return JSON.parse(execFileSync(process.execPath,['--input-type=module','-e',`import * as t from ${JSON.stringify(module)};console.log(JSON.stringify(${expression}));`],{env:{...process.env,TZ:zone},encoding:'utf8'}));}
describe('F5 absolute/local boundaries',()=>{
 for(const [zone,day,from,until,hours] of [
 ['Europe/Berlin','2026-03-29','2026-03-28T23:00:00.000Z','2026-03-29T21:59:59.999999Z',23],
 ['Europe/Berlin','2026-10-25','2026-10-24T22:00:00.000Z','2026-10-25T22:59:59.999999Z',25],
 ['America/New_York','2026-03-08','2026-03-08T05:00:00.000Z','2026-03-09T03:59:59.999999Z',23],
 ['America/New_York','2026-11-01','2026-11-01T04:00:00.000Z','2026-11-02T04:59:59.999999Z',25],
 ['America/Santiago','2026-09-06','2026-09-06T04:00:00.000Z','2026-09-07T02:59:59.999999Z',23],
 ['UTC','2026-03-29','2026-03-29T00:00:00.000Z','2026-03-29T23:59:59.999999Z',24]] as const)it(`${zone} ${day} ${hours}-hour inclusive day`,()=>{const bounds=inZone(zone,`t.auditBounds('${day}','${day}')`);expect(bounds).toEqual({from,until});expect((Date.parse(until)+1-Date.parse(from))/3600000).toBe(hours);});
 for(const value of ['2026-02-30','2026-13-01','2026-01-01T25:00','2026-01-01T12:69','not time','2026-01-01T00:00:00.0001','2026-01-01T00:00Z'])it(`rejects invalid/local input ${value}`,()=>expect(()=>localInstant(value)).toThrow());
 it('independently optional bounds and blank loan instant',()=>{expect(localInstant('')).toBeNull();expect(auditBounds('','')).toEqual({from:undefined,until:undefined});expect(inZone('UTC',"t.auditBounds('2026-01-01','')")).toEqual({from:'2026-01-01T00:00:00.000Z'});expect(inZone('UTC',"t.auditBounds('','2026-01-01')")).toEqual({until:'2026-01-01T23:59:59.999999Z'});});
 it('rejects reversed absolute bounds',()=>expect(()=>auditBounds('2026-01-02','2026-01-01')).toThrow());
 it('preserves exact local seconds/milliseconds in UTC transport',()=>expect(inZone('Europe/Berlin',"t.localInstant('2026-07-01T14:15:16.123')")).toBe('2026-07-01T12:15:16.123Z'));
 for(const [zone,value] of [['Europe/Berlin','2026-03-29T02:30'],['America/New_York','2026-03-08T02:30']] as const)it(`${zone} rejects skipped DST hour before dispatch`,()=>expect(inZone(zone,`t.validLocalInput('${value}')`)).toBe(false));
 it('repeated hour uses the native earlier occurrence with an absolute offset',()=>expect(inZone('Europe/Berlin',"t.localInstant('2026-10-25T02:30')")).toBe('2026-10-25T00:30:00.000Z'));
 for(const language of ['de','en'])it(`${language} local display crosses calendar day and includes timezone`,()=>{const text=inZone('America/Los_Angeles',`t.localTimestamp('2026-10-12T00:30:00Z','${language}')`);expect(text).toMatch(/11/);expect(text).toMatch(/17:30|05:30/);expect(text).toMatch(/PDT|GMT-7/);});
 it('invalid display stays safe and blank input is valid',()=>{expect(validLocalInput('')).toBe(true);expect(inZone('UTC',"t.localTimestamp('bad','en')")).toBe('bad');});
});
