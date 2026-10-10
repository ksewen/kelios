import { describe, expect, it } from 'vitest';
import { assetQuery, catalogInput, conditionTargets, display, historyChanges, lifecycleTargets, nullable } from './inventory';
describe('explicit inventory UI commands', () => {
  it('sends Product ID without redundant dependency facts', () => expect(catalogInput({ productId: 'p', categoryId: 'c', manufacturerId: 'm' })).toEqual({ productId: 'p' }));
  it('sends direct references when Product is not selected', () => expect(catalogInput({ productId: '', categoryId: 'c', manufacturerId: 'm' })).toEqual({ categoryId: 'c', manufacturerId: 'm' }));
  it('normalizes optional blanks without modifying nonblank input', () => { expect(nullable('  ')).toBeNull(); expect(nullable(' note ')).toBe(' note '); });
  it('keeps bounded literal table search encoded and fixed filters explicit', () => { const q=new URLSearchParams(assetQuery(2,'%_\\','tag','asc',{loaned:'true',ownerId:'',condition:'WORKING'}));expect(q.get('q')).toBe('%_\\');expect(q.get('size')).toBe('25');expect(q.get('page')).toBe('2');expect(q.has('ownerId')).toBe(false);expect(q.get('loaned')).toBe('true'); });
  it('preserves terminal graphs and no self targets', () => { for(const [from, targets] of Object.entries(lifecycleTargets))expect(targets).not.toContain(from);for(const [from, targets] of Object.entries(conditionTargets))expect(targets).not.toContain(from);expect(lifecycleTargets.DEREGISTERED).toEqual([]);expect(conditionTargets.UNUSABLE).toEqual([]); });
  it('renders history as data and missing old fields as unavailable', () => {expect(display(null)).toBe('—');expect(display({note:'<script>'})).toContain('<script>');});
  it('formats captured historical labels and never fabricates absent loan fields', () => {const changes=historyChanges({before:{location:{id:'old',name:'Original label'}},after:{location:{id:'new',name:'New label'}}});expect(changes).toEqual([{field:'location',before:'Original label · old',after:'New label · new'}]);expect(changes.some(c=>c.field==='loanedTo')).toBe(false);});

});
