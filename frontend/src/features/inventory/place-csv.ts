export type PlaceCsvRow = { record: number; line: number; name: string };
export type PlaceCsvSettings = { encoding: 'AUTO' | 'UTF-8' | 'WINDOWS-1252' | 'ISO-8859-1'; delimiter: string; header: boolean };
export type PlaceCsvSourceRow = { record: number; line: number; cells: string[] };
export type PlaceCsvTable = { columns: string[]; rows: PlaceCsvSourceRow[]; delimiter: string | null; encoding: string };

export class PlaceCsvError extends Error {
  constructor(readonly reason: 'encoding' | 'autoEncoding' | 'bom' | 'format' | 'columns' | 'ambiguous' | 'limit' | 'empty', readonly line?: number) { super(reason); }
}

function decode(bytes: ArrayBuffer, encoding: PlaceCsvSettings['encoding']): string {
  if (bytes.byteLength > 5 * 1024 * 1024) throw new PlaceCsvError('limit');
  const source = new Uint8Array(bytes), bom = source[0] === 0xef && source[1] === 0xbb && source[2] === 0xbf;
  if (bom && !['AUTO', 'UTF-8'].includes(encoding)) throw new PlaceCsvError('bom');
  let input: string;
  try {
    if (encoding === 'ISO-8859-1') {
      // Browser TextDecoder aliases ISO-8859-1 to Windows-1252; preserve the selected byte mapping.
      const parts: string[] = [];
      for (let i = 0; i < source.length; i += 8192) parts.push(String.fromCharCode(...source.subarray(i, i + 8192)));
      input = parts.join('');
    } else input = new TextDecoder(encoding === 'AUTO' ? 'utf-8' : encoding.toLowerCase(), { fatal: true }).decode(bytes);
  } catch { throw new PlaceCsvError(encoding === 'AUTO' ? 'autoEncoding' : 'encoding'); }
  return input.startsWith('\ufeff') ? input.slice(1) : input;
}

export function parsePlaceCsvTable(bytes: ArrayBuffer, settings: PlaceCsvSettings): PlaceCsvTable {
  const input = decode(bytes, settings.encoding);
  const encoding = settings.encoding === 'AUTO' ? 'UTF-8' : settings.encoding;
  if (settings.delimiter !== 'AUTO') return { ...parseRecords(input, settings.delimiter, settings.header), delimiter: settings.delimiter, encoding };
  const multi: PlaceCsvTable[] = [], single: PlaceCsvTable[] = [];
  let failure: PlaceCsvError | null = null;
  for (const delimiter of [';', ',', '\t']) {
    try {
      const parsed = parseRecords(input, delimiter, settings.header);
      (parsed.columns.length > 1 ? multi : single).push({ ...parsed, delimiter, encoding });
    } catch (cause) { if (cause instanceof PlaceCsvError) failure ??= cause; else throw cause; }
  }
  if (multi.length > 1) throw new PlaceCsvError('ambiguous');
  if (multi.length === 1) return multi[0];
  if (single.length) return { ...single[0], delimiter: null };
  throw failure ?? new PlaceCsvError('format');
}

function parseRecords(input: string, delimiter: string, header: boolean): Pick<PlaceCsvTable, 'columns' | 'rows'> {
  if (Array.from(delimiter).length !== 1 || ['"', '\r', '\n'].includes(delimiter)) throw new PlaceCsvError('format');
  const records: { line: number; cells: string[] }[] = [];
  let cells: string[] = [], cell = '', state: 'start' | 'plain' | 'quoted' | 'closed' = 'start';
  let line = 1, startLine = 1;
  const addCell = () => { if (cell.length > 32 * 1024) throw new PlaceCsvError('limit', startLine); cells.push(cell); cell = ''; state = 'start'; };
  const addRecord = () => {
    addCell();
    if (cells.length > 64) throw new PlaceCsvError('limit', startLine);
    if (records.length && cells.length !== records[0].cells.length) throw new PlaceCsvError('columns', startLine);
    records.push({ line: startLine, cells }); cells = []; startLine = line + 1;
    if (records.length > 5001) throw new PlaceCsvError('limit', line);
  };
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
  if (!records.length) throw new PlaceCsvError('empty');
  const columns = header ? records[0].cells : records[0].cells.map(() => '');
  const rows = records.slice(header ? 1 : 0).map((row, index) => ({ record: index + (header ? 2 : 1), line: row.line, cells: row.cells }));
  if (!rows.length) throw new PlaceCsvError('empty');
  if (rows.length > 5000) throw new PlaceCsvError('limit');
  return { columns, rows };
}
