export type PlaceCsvRow = { record: number; line: number; name: string };
export type PlaceCsvSettings = { encoding: 'AUTO' | 'UTF-8' | 'WINDOWS-1252' | 'ISO-8859-1'; delimiter: string; header: boolean };

export class PlaceCsvError extends Error {
  constructor(readonly reason: 'encoding' | 'autoEncoding' | 'bom' | 'format' | 'columns' | 'ambiguous' | 'header' | 'limit' | 'empty', readonly line?: number) { super(reason); }
}

export function parsePlaceCsv(bytes: ArrayBuffer, settings: PlaceCsvSettings): PlaceCsvRow[] {
  if (bytes.byteLength > 5 * 1024 * 1024) throw new PlaceCsvError('limit');
  const source=new Uint8Array(bytes),bom=source[0]===0xef&&source[1]===0xbb&&source[2]===0xbf;
  if (bom && !['AUTO','UTF-8'].includes(settings.encoding)) throw new PlaceCsvError('bom');
  let input: string;
  try {
    if (settings.encoding === 'ISO-8859-1') {
      // Browser TextDecoder aliases ISO-8859-1 to Windows-1252; preserve the selected byte mapping.
      const parts:string[]=[];
      for(let i=0;i<source.length;i+=8192)parts.push(String.fromCharCode(...source.subarray(i,i+8192)));
      input=parts.join('');
    } else input = new TextDecoder(settings.encoding==='AUTO'?'utf-8':settings.encoding.toLowerCase(), { fatal: true }).decode(bytes);
  } catch { throw new PlaceCsvError(settings.encoding==='AUTO'?'autoEncoding':'encoding'); }
  if (input.startsWith('\ufeff')) input = input.slice(1);

  if (settings.delimiter !== 'AUTO') return parseRecords(input,settings.delimiter,settings.header);
  let accepted:PlaceCsvRow[]|null=null;
  for (const delimiter of [';',',','\t']) {
    let candidate:PlaceCsvRow[];
    try { candidate=parseRecords(input,delimiter,settings.header); }
    catch (cause) { if (cause instanceof PlaceCsvError && cause.reason==='columns') throw new PlaceCsvError('ambiguous',cause.line); throw cause; }
    if (accepted && (candidate.length!==accepted.length || candidate.some((row,index)=>row.name!==accepted![index].name||row.line!==accepted![index].line))) throw new PlaceCsvError('ambiguous');
    accepted=candidate;
  }
  return accepted!;
}

function parseRecords(input:string,delimiter:string,header:boolean):PlaceCsvRow[] {
  if (Array.from(delimiter).length !== 1 || ['"', '\r', '\n'].includes(delimiter)) throw new PlaceCsvError('format');
  const records: { line: number; cells: string[] }[] = [];
  let cells: string[] = [], cell = '', state: 'start' | 'plain' | 'quoted' | 'closed' = 'start';
  let line = 1, startLine = 1;
  const addCell = () => { if (cell.length > 32 * 1024) throw new PlaceCsvError('limit', startLine); cells.push(cell); cell = ''; state = 'start'; };
  const addRecord = () => { addCell(); if (cells.length !== 1) throw new PlaceCsvError('columns', startLine); records.push({ line: startLine, cells }); cells = []; startLine = line + 1; if (records.length > 5001) throw new PlaceCsvError('limit', line); };
  for (let i = 0; i < input.length; i++) {
    const char = input[i];
    if (state === 'quoted') {
      if (char === '"') { if (input[i + 1] === '"') { cell += '"'; i++; } else state = 'closed'; }
      else if (char === '\r' || char === '\n') {
        if (char === '\r') { if (input[i + 1] !== '\n') throw new PlaceCsvError('format', line); i++; }
        cell += char === '\r' ? '\r\n' : '\n'; line++;
      } else cell += char;
      if (cell.length > 32 * 1024) throw new PlaceCsvError('limit', startLine);
      continue;
    }
    if (char === delimiter) { addCell(); continue; }
    if (char === '\r' || char === '\n') {
      if (char === '\r') { if (input[i + 1] !== '\n') throw new PlaceCsvError('format', line); i++; }
      addRecord(); line++; continue;
    }
    if (char === '"') {
      if (state === 'start') state = 'quoted';
      else throw new PlaceCsvError('format', line);
    } else {
      if (state === 'closed') throw new PlaceCsvError('format', line);
      cell += char; state = 'plain';
      if (cell.length > 32 * 1024) throw new PlaceCsvError('limit', startLine);
    }
  }
  if (state === 'quoted') throw new PlaceCsvError('format', startLine);
  if (cell || cells.length || state !== 'start') addRecord();
  if (header) {
    if (!records.length || records[0].cells[0].trim().toLowerCase() !== 'name') throw new PlaceCsvError('header', records[0]?.line ?? 1);
    records.shift();
  }
  if (!records.length) throw new PlaceCsvError('empty');
  if (records.length > 5000) throw new PlaceCsvError('limit');
  return records.map((row, index) => ({ record: index + (header ? 2 : 1), line: row.line, name: row.cells[0] }));
}
