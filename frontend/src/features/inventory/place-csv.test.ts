import {describe,expect,it} from 'vitest';
import {parsePlaceCsv,PlaceCsvError,type PlaceCsvSettings} from './place-csv';

const bytes=(text:string)=>new TextEncoder().encode(text).buffer as ArrayBuffer;
const settings:PlaceCsvSettings={encoding:'UTF-8',delimiter:';',header:true};

describe('one-column Place CSV',()=>{
  it('accepts a delimiter-free column in both automatic modes, including BOM and quoted punctuation',()=>{
    expect(parsePlaceCsv(bytes('\ufeffNAME\r\n"Room; East, Wing\tA"\r\nSecond\r\n'),{encoding:'AUTO',delimiter:'AUTO',header:true})).toEqual([
      {record:2,line:2,name:'Room; East, Wing\tA'}, {record:3,line:3,name:'Second'}
    ]);
    expect(parsePlaceCsv(bytes('First\nSecond'),{encoding:'AUTO',delimiter:'AUTO',header:false})[0].name).toBe('First');
  });
  it.each([';',',','\t'])('rejects a possible extra column under automatic delimiter %s',separator=>{
    try {parsePlaceCsv(bytes(`name\nA${separator}B`),{encoding:'AUTO',delimiter:'AUTO',header:true});throw Error('Expected ambiguity');}
    catch(error){expect(error).toMatchObject({reason:'ambiguous',line:2});}
  });
  it('uses only the explicitly selected delimiter, including a custom character',()=>{
    expect(parsePlaceCsv(bytes('name\nA,B'),{encoding:'AUTO',delimiter:';',header:true})[0].name).toBe('A,B');
    expect(()=>parsePlaceCsv(bytes('name\nA,B'),{encoding:'AUTO',delimiter:',',header:true})).toThrowError(PlaceCsvError);
    expect(parsePlaceCsv(bytes('name\n"A|B"'),{encoding:'AUTO',delimiter:'|',header:true})[0].name).toBe('A|B');
  });
  it('treats automatic encoding as strict UTF-8 and rejects a BOM with legacy encodings',()=>{
    const invalid=Uint8Array.from([110,97,109,101,10,0x80]).buffer;
    try {parsePlaceCsv(invalid,{encoding:'AUTO',delimiter:'AUTO',header:true});throw Error('Expected invalid UTF-8');}
    catch(error){expect(error).toMatchObject({reason:'autoEncoding'});}
    for(const encoding of ['WINDOWS-1252','ISO-8859-1'] as const){
      try {parsePlaceCsv(bytes('\ufeffname\nA'),{encoding,delimiter:';',header:true});throw Error('Expected BOM conflict');}
      catch(error){expect(error).toMatchObject({reason:'bom'});}
    }
  });
  it('accepts a case-insensitive header, UTF-8 BOM, quotes and physical line numbers',()=>{
    expect(parsePlaceCsv(bytes('\ufeff Name \r\n"Room; ""North""\r\nWing"\r\nSecond\r\n'),settings)).toEqual([
      {record:2,line:2,name:'Room; "North"\r\nWing'},
      {record:3,line:4,name:'Second'}
    ]);
  });
  it('retains headerless first and empty rows for backend name validation',()=>{
    expect(parsePlaceCsv(bytes('First\n\nFirst'),{...settings,delimiter:',',header:false})).toEqual([
      {record:1,line:1,name:'First'}, {record:2,line:2,name:''}, {record:3,line:3,name:'First'}
    ]);
  });
  it.each([
    ['extra column','name\nA;B','columns'],
    ['wrong header','Bezeichnung\nA','header'],
    ['bad quote','name\n"A"x','format'],
    ['unclosed quote','name\n"A','format'],
    ['no data','name\n','empty'],
    ['oversized cell',`name\n${'a'.repeat(32769)}`,'limit'],
  ])('rejects %s before execution',(_label,input,reason)=>{
    expect(()=>parsePlaceCsv(bytes(input),settings)).toThrowError(PlaceCsvError);
    try {parsePlaceCsv(bytes(input),settings);} catch(error){expect((error as PlaceCsvError).reason).toBe(reason);}
  });
  it('rejects invalid UTF-8 and decodes explicit Windows-1252',()=>{
    const legacy=Uint8Array.from([110,97,109,101,10,82,97,117,109,32,0x80]).buffer;
    expect(()=>parsePlaceCsv(legacy,settings)).toThrowError(PlaceCsvError);
    expect(parsePlaceCsv(legacy,{...settings,encoding:'WINDOWS-1252'})[0].name).toBe('Raum €');
  });
  it('supports ISO-8859-1 and enforces file and row bounds',()=>{
    const latin=Uint8Array.from([110,97,109,101,10,82,97,117,109,32,0xe4]).buffer;
    expect(parsePlaceCsv(latin,{...settings,encoding:'ISO-8859-1'})[0].name).toBe('Raum ä');
    expect(parsePlaceCsv(Uint8Array.from([110,97,109,101,10,0x80]).buffer,{...settings,encoding:'ISO-8859-1'})[0].name).toBe('\u0080');
    expect(()=>parsePlaceCsv(new ArrayBuffer(5*1024*1024+1),settings)).toThrowError(PlaceCsvError);
    expect(()=>parsePlaceCsv(bytes(`name\n${'A\n'.repeat(5001)}`),settings)).toThrowError(PlaceCsvError);
  });
});
