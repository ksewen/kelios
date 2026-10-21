import { describe, expect, it } from 'vitest';
import { assetQuery, catalogInput, conditionTargets, display, historyChanges, registrationValues, lifecycleTargets, nullable, PAGE_SIZES, hasNextPage, normalizeSearch } from './inventory';
describe('explicit inventory UI commands', () => {
  it('sends Product ID without redundant dependency facts', () => expect(catalogInput({ productId: 'p', categoryId: 'c', manufacturerId: 'm' })).toEqual({ productId: 'p' }));
  it('sends direct references when Product is not selected', () => expect(catalogInput({ productId: '', categoryId: 'c', manufacturerId: 'm' })).toEqual({ categoryId: 'c', manufacturerId: 'm' }));
  it('normalizes optional blanks without modifying nonblank input', () => { expect(nullable('  ')).toBeNull(); expect(nullable(' note ')).toBe(' note '); });
  it('keeps bounded literal table search encoded and fixed filters explicit', () => { const q=new URLSearchParams(assetQuery(2,'%_\\','tag','asc',{loaned:'true',ownerId:'',condition:'WORKING'}));expect(q.get('q')).toBe('%_\\');expect(q.get('size')).toBe('20');expect(q.get('page')).toBe('2');expect(q.has('ownerId')).toBe(false);expect(q.get('loaned')).toBe('true'); });
  it('preserves terminal graphs and no self targets', () => { for(const [from, targets] of Object.entries(lifecycleTargets))expect(targets).not.toContain(from);for(const [from, targets] of Object.entries(conditionTargets))expect(targets).not.toContain(from);expect(lifecycleTargets.DEREGISTERED).toEqual([]);expect(conditionTargets.UNUSABLE).toEqual([]); });
  it('renders history as data and missing old fields as unavailable', () => {expect(display(null)).toBe('—');expect(display({note:'<script>'})).toContain('<script>');});
  it('formats captured historical labels and never fabricates absent loan fields', () => {const changes=historyChanges({before:{location:{id:'old',name:'Original label'}},after:{location:{id:'new',name:'New label'}}});expect(changes).toEqual([{field:'location',before:'Original label · old',after:'New label · new'}]);expect(changes.some(c=>c.field==='loanedTo')).toBe(false);});
  it('leaves the semantically empty side of loan and return events blank without changing other diffs',()=>{
    expect(historyChanges({before:{loanedTo:'Prior borrower'},after:{loanedTo:'Borrower',expectedReturnAt:'2026-10-25T12:00:00Z'}},'LOANED')).toEqual([
      {field:'loanedTo',before:'—',after:'Borrower'},{field:'expectedReturnAt',before:'—',after:'2026-10-25T12:00:00Z'}]);
    expect(historyChanges({before:{loanedTo:'Borrower',expectedReturnAt:'2026-10-25T12:00:00Z'},after:{loanedTo:null}},'RETURNED')).toEqual([
      {field:'loanedTo',before:'Borrower',after:'—'},{field:'expectedReturnAt',before:'2026-10-25T12:00:00Z',after:'—'}]);
    expect(historyChanges({before:{note:'Old'},after:{note:'New'}},'UPDATED')).toEqual([{field:'note',before:'Old',after:'New'}]);
  });
  it('shows captured registration values, including false, but no absent or explicit null values',()=>{expect(registrationValues({before:null,after:{assetTag:'T1',loanable:false,location:{id:'l',name:'Room'},owner:null,loanedAt:null}})).toEqual([{field:'assetTag',value:'T1'},{field:'loanable',value:'false'},{field:'location',value:'Room · l'}]);expect(registrationValues({before:{loanable:false},after:{loanable:true}})).toBeNull();expect(registrationValues({before:null,after:null})).toBeNull();});

  it('uses exactly six supported sizes and current response page metadata',()=>{expect(PAGE_SIZES).toEqual([10,20,30,40,50,100]);expect(hasNextPage({items:[],page:0,size:20,totalElements:21})).toBe(true);expect(hasNextPage({items:[],page:1,size:20,totalElements:21})).toBe(false);expect(hasNextPage({items:[],page:9,size:20,totalElements:21})).toBe(false);});
  it('normalizes only outer search whitespace, preserving literal wildcard characters',()=>{expect(normalizeSearch(' %_\\ ')).toBe('%_\\');expect(normalizeSearch('  ')).toBe('');expect(normalizeSearch('a  b')).toBe('a  b');});
  for(const size of PAGE_SIZES)it(`serializes requested ${size} without leaking blank query`,()=>{const p=new URLSearchParams(assetQuery(0,'  ','tag','asc',{lifecycle:'IN_STOCK'},size));expect(p.get('size')).toBe(String(size));expect(p.has('q')).toBe(false);expect(p.get('lifecycle')).toBe('IN_STOCK');});
});
