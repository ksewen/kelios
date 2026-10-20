import {describe,it,expect} from 'vitest';
import {execFileSync} from 'node:child_process';
import {localInstant,localEndOfDayInstant,localCalendarDate,validLocalDate,auditBounds,validLocalInput} from './local-time';
const module=new URL('./local-time.ts',import.meta.url).href;
function inZone(zone:string,expression:string){return JSON.parse(execFileSync(process.execPath,['--input-type=module','-e',`import * as t from ${JSON.stringify(module)};console.log(JSON.stringify(${expression}));`],{env:{...process.env,TZ:zone},encoding:'utf8'}));}
describe('F5 absolute/local boundaries',()=>{
 for(const [zone,fromInput,untilInput,from,until] of [
 ['Europe/Berlin','2026-03-29T01:00','2026-03-29T23:59','2026-03-29T00:00:00.000Z','2026-03-29T21:59:00.000Z'],
 ['Europe/Berlin','2026-10-25T01:00','2026-10-25T23:59','2026-10-24T23:00:00.000Z','2026-10-25T22:59:00.000Z'],
 ['America/New_York','2026-03-08T01:00','2026-03-08T23:59','2026-03-08T06:00:00.000Z','2026-03-09T03:59:00.000Z'],
 ['UTC','2026-03-29T00:00','2026-03-29T23:59','2026-03-29T00:00:00.000Z','2026-03-29T23:59:00.000Z']] as const)it(`${zone} converts explicitly entered date-time bounds`,()=>expect(inZone(zone,`t.auditBounds('${fromInput}','${untilInput}')`)).toEqual({from,until}));
 for(const value of ['2026-02-30','2026-13-01','2026-01-01T25:00','2026-01-01T12:69','not time','2026-01-01T00:00:00.0001','2026-01-01T00:00Z'])it(`rejects invalid/local input ${value}`,()=>expect(()=>localInstant(value)).toThrow());
 it('independently optional bounds and blank loan instant',()=>{expect(localInstant('')).toBeNull();expect(auditBounds('','')).toEqual({from:undefined,until:undefined});expect(inZone('UTC',"t.auditBounds('2026-01-01T00:00','')")).toEqual({from:'2026-01-01T00:00:00.000Z'});expect(inZone('UTC',"t.auditBounds('','2026-01-01T23:59')")).toEqual({until:'2026-01-01T23:59:00.000Z'});});
 it('rejects reversed absolute bounds and date-only input',()=>{expect(()=>auditBounds('2026-01-02T12:00','2026-01-01T12:00')).toThrow();expect(()=>auditBounds('2026-01-01','')).toThrow();});
 it('preserves exact local seconds/milliseconds in UTC transport',()=>expect(inZone('Europe/Berlin',"t.localInstant('2026-07-01T14:15:16.123')")).toBe('2026-07-01T12:15:16.123Z'));
 for(const [zone,value] of [['Europe/Berlin','2026-03-29T02:30'],['America/New_York','2026-03-08T02:30']] as const)it(`${zone} rejects skipped DST hour before dispatch`,()=>expect(inZone(zone,`t.validLocalInput('${value}')`)).toBe(false));
 it('repeated hour uses the native earlier occurrence with an absolute offset',()=>expect(inZone('Europe/Berlin',"t.localInstant('2026-10-25T02:30')")).toBe('2026-10-25T00:30:00.000Z'));
 for(const language of ['de','en'])it(`${language} local display crosses calendar day and includes timezone`,()=>{const text=inZone('America/Los_Angeles',`t.localTimestamp('2026-10-12T00:30:00Z','${language}')`);expect(text).toMatch(/11/);expect(text).toMatch(/17:30|05:30/);expect(text).toMatch(/PDT|GMT-7/);});
 it('invalid display stays safe and blank input is valid',()=>{expect(validLocalInput('')).toBe(true);expect(inZone('UTC',"t.localTimestamp('bad','en')")).toBe('bad');});
 for(const [zone,date,instant] of [
  ['UTC','2026-07-01','2026-07-01T23:59:59.999Z'],
  ['Europe/Berlin','2026-07-01','2026-07-01T21:59:59.999Z'],
  ['Europe/Berlin','2026-03-29','2026-03-29T21:59:59.999Z'],
  ['Europe/Berlin','2026-10-25','2026-10-25T22:59:59.999Z'],
  ['America/New_York','2026-03-08','2026-03-09T03:59:59.999Z'],
  ['America/New_York','2026-11-01','2026-11-02T04:59:59.999Z']
 ] as const)it(`${zone} ${date} maps checkout to local end of day`,()=>expect(inZone(zone,`t.localEndOfDayInstant('${date}')`)).toBe(instant));
 it.each(['','2026-02-30','2026-13-01','2026-01-01T12:00','not a date'])('rejects invalid checkout date %s',value=>{expect(validLocalDate(value)).toBe(false);expect(()=>localEndOfDayInstant(value)).toThrow();});
 it('formats arbitrary stored instants as the local date without changing the instant',()=>{expect(inZone('America/New_York',"t.localCalendarDate('2026-10-12T00:30:00Z','en')")).toBe('10/11/2026');expect(localCalendarDate('bad','de')).toBe('bad');});
});
