import {describe,expect,it} from 'vitest';
import {parsePlaceCsvTable,PlaceCsvError,type PlaceCsvSettings} from './place-csv';

const bytes=(text:string)=>new TextEncoder().encode(text).buffer as ArrayBuffer;
const settings:PlaceCsvSettings={encoding:'UTF-8',delimiter:';',header:true};
const names=(data:ArrayBuffer,options:PlaceCsvSettings)=>parsePlaceCsvTable(data,options).rows.map(row=>({record:row.record,line:row.line,name:row.cells[0]}));

describe('one-column Place CSV',()=>{
  it('accepts a delimiter-free column in both automatic modes, including BOM and quoted punctuation',()=>{
    expect(names(bytes('\ufeffNAME\r\n"Room; East, Wing\tA"\r\nSecond\r\n'),{encoding:'AUTO',delimiter:'AUTO',header:true})).toEqual([
      {record:2,line:2,name:'Room; East, Wing\tA'}, {record:3,line:3,name:'Second'}
    ]);
    expect(names(bytes('First\nSecond'),{encoding:'AUTO',delimiter:'AUTO',header:false})[0].name).toBe('First');
  });
  it.each([';',',','\t'])('keeps one column when the candidate separator produces inconsistent rows %s',separator=>{
    expect(names(bytes(`name\nA${separator}B`),{encoding:'AUTO',delimiter:'AUTO',header:true})[0].name).toBe(`A${separator}B`);
  });
  it('uses only the explicitly selected delimiter, including a custom character',()=>{
    expect(names(bytes('name\nA,B'),{encoding:'AUTO',delimiter:';',header:true})[0].name).toBe('A,B');
    expect(()=>names(bytes('name\nA,B'),{encoding:'AUTO',delimiter:',',header:true})).toThrowError(PlaceCsvError);
    expect(names(bytes('name\n"A|B"'),{encoding:'AUTO',delimiter:'|',header:true})[0].name).toBe('A|B');
  });
  it('treats automatic encoding as strict UTF-8 and rejects a BOM with legacy encodings',()=>{
    const invalid=Uint8Array.from([110,97,109,101,10,0x80]).buffer;
    try {names(invalid,{encoding:'AUTO',delimiter:'AUTO',header:true});throw Error('Expected invalid UTF-8');}
    catch(error){expect(error).toMatchObject({reason:'autoEncoding'});}
    for(const encoding of ['WINDOWS-1252','ISO-8859-1'] as const){
      try {names(bytes('\ufeffname\nA'),{encoding,delimiter:';',header:true});throw Error('Expected BOM conflict');}
      catch(error){expect(error).toMatchObject({reason:'bom'});}
    }
  });
  it('accepts a case-insensitive header, UTF-8 BOM, quotes and physical line numbers',()=>{
    expect(names(bytes('\ufeff Name \r\n"Room; ""North""\r\nWing"\r\nSecond\r\n'),settings)).toEqual([
      {record:2,line:2,name:'Room; "North"\r\nWing'},
      {record:3,line:4,name:'Second'}
    ]);
  });
  it('retains headerless first and empty rows for backend name validation',()=>{
    expect(names(bytes('First\n\nFirst'),{...settings,delimiter:',',header:false})).toEqual([
      {record:1,line:1,name:'First'}, {record:2,line:2,name:''}, {record:3,line:3,name:'First'}
    ]);
  });
  it.each([
    ['extra column','name\nA;B','columns'],
    ['bad quote','name\n"A"x','format'],
    ['unclosed quote','name\n"A','format'],
    ['no data','name\n','empty'],
    ['oversized cell',`name\n${'a'.repeat(32769)}`,'limit'],
  ])('rejects %s before execution',(_label,input,reason)=>{
    expect(()=>names(bytes(input),settings)).toThrowError(PlaceCsvError);
    try {names(bytes(input),settings);} catch(error){expect((error as PlaceCsvError).reason).toBe(reason);}
  });
  it('rejects invalid UTF-8 and decodes explicit Windows-1252',()=>{
    const legacy=Uint8Array.from([110,97,109,101,10,82,97,117,109,32,0x80]).buffer;
    expect(()=>names(legacy,settings)).toThrowError(PlaceCsvError);
    expect(names(legacy,{...settings,encoding:'WINDOWS-1252'})[0].name).toBe('Raum €');
  });
  it('supports ISO-8859-1 and enforces file and row bounds',()=>{
    const latin=Uint8Array.from([110,97,109,101,10,82,97,117,109,32,0xe4]).buffer;
    expect(names(latin,{...settings,encoding:'ISO-8859-1'})[0].name).toBe('Raum ä');
    expect(names(Uint8Array.from([110,97,109,101,10,0x80]).buffer,{...settings,encoding:'ISO-8859-1'})[0].name).toBe('\u0080');
    expect(()=>names(new ArrayBuffer(5*1024*1024+1),settings)).toThrowError(PlaceCsvError);
    expect(()=>names(bytes(`name\n${'A\n'.repeat(5001)}`),settings)).toThrowError(PlaceCsvError);
  });
});

describe('mapped Place CSV table',()=>{
  it('detects a headed multi-column structure and preserves source positions and lines',()=>{
    expect(parsePlaceCsvTable(bytes('Raum;Notiz;Code\r\n"A; B";"one\r\ntwo";7\r\nC;note;8\r\n'),{encoding:'AUTO',delimiter:'AUTO',header:true})).toEqual({
      encoding:'UTF-8',delimiter:';',columns:['Raum','Notiz','Code'],rows:[
        {record:2,line:2,cells:['A; B','one\r\ntwo','7']},
        {record:3,line:4,cells:['C','note','8']}
      ]
    });
  });
  it('accepts headerless columns and a single column without detecting a delimiter',()=>{
    expect(parsePlaceCsvTable(bytes('A;1\nB;2'),{...settings,delimiter:'AUTO',header:false})).toMatchObject({delimiter:';',columns:['',''],rows:[{record:1,line:1,cells:['A','1']},{record:2,line:2,cells:['B','2']}]});
    expect(parsePlaceCsvTable(bytes('A\nB'),{...settings,delimiter:'AUTO',header:false})).toMatchObject({delimiter:null,columns:[''],rows:[{record:1,line:1,cells:['A']},{record:2,line:2,cells:['B']}]});
  });
  it('keeps blank and duplicate source headings distinguishable by position',()=>{
    expect(parsePlaceCsvTable(bytes('Name;name;;Other\nA;B;C;D'),settings).columns).toEqual(['Name','name','','Other']);
  });
  it('uses an explicit delimiter to keep other punctuation in a name',()=>{
    expect(parsePlaceCsvTable(bytes('A Block, 414\nB Block, 415'),{...settings,delimiter:';',header:false}).rows[0].cells).toEqual(['A Block, 414']);
    expect(parsePlaceCsvTable(bytes('A Block, 414\nB Block, 415'),{...settings,delimiter:'AUTO',header:false}).columns).toHaveLength(2);
  });
  it('rejects conflicting automatic multi-column candidates and ragged explicit rows',()=>{
    try {parsePlaceCsvTable(bytes('A;B,C\nD;E,F'),{...settings,delimiter:'AUTO',header:false});throw Error('Expected ambiguity');}
    catch(error){expect(error).toMatchObject({reason:'ambiguous'});}
    try {parsePlaceCsvTable(bytes('A;B\nC'),{...settings,header:false});throw Error('Expected ragged record');}
    catch(error){expect(error).toMatchObject({reason:'columns',line:2});}
  });
  it('limits column count without limiting arbitrary header text',()=>{
    expect(()=>parsePlaceCsvTable(bytes(`${Array.from({length:65},()=> 'x').join(';')}\n${Array.from({length:65},()=> 'v').join(';')}`),settings)).toThrowError(PlaceCsvError);
  });
});
