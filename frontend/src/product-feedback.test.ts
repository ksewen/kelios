import {afterEach,expect,it} from 'vitest';
import {mutationFeedback} from './resource-contracts';
import {i18n} from './i18n';
import en from './locales/en.json';
import de from './locales/de.json';
afterEach(async()=>{await i18n.changeLanguage('en');});
for(const language of ['en','de'] as const){
 it(`${language}: accepted Product concurrent code remains exact and safe in Product context`,async()=>{
  await i18n.changeLanguage(language);const r=language==='de'?de:en;
  const error={status:409,code:'CONCURRENT_OPERATION',detail:'UNSAFE_RAW_DETAIL Asset changed <script>secret</script>'},before={...error};
  const result=mutationFeedback(error,'product');expect(result).toEqual({message:r.errors.CONCURRENT_OPERATION,guard:false,uncertain:false,review:true});expect(result.message).not.toContain('UNSAFE_RAW_DETAIL');expect(result.message).not.toBe(r.errors.STALE_ASSET_VERSION);expect(error).toEqual(before);
 });
 it(`${language}: Asset stale version and ProductSelection concurrent semantics are preserved`,async()=>{
  await i18n.changeLanguage(language);const r=language==='de'?de:en;
  expect(mutationFeedback({status:409,code:'STALE_ASSET_VERSION'},'asset')).toEqual({message:r.errors.STALE_ASSET_VERSION,guard:false,uncertain:false,review:true});
  expect(mutationFeedback({status:409,code:'CONCURRENT_OPERATION'},'asset')).toEqual({message:r.errors.CONCURRENT_OPERATION,guard:false,uncertain:false,review:true});
 });
 it(`${language}: guards, unknown/code-less409 and uncertain transport retain safe classification`,async()=>{
  await i18n.changeLanguage(language);const r=language==='de'?de:en;
  for(const code of ['PRODUCT_EDIT_FROZEN','REFERENCED'] as const)expect(mutationFeedback({status:409,code},'product')).toEqual({message:r.errors[code],guard:true,uncertain:false,review:false});
  for(const error of [{status:409},{status:409,code:'FUTURE_CONFLICT',detail:'UNSAFE_RAW_DETAIL'}])expect(mutationFeedback(error,'product')).toEqual({message:r.errors.HTTP_CONFLICT,guard:false,uncertain:false,review:true});
  expect(mutationFeedback('UNCERTAIN','product')).toMatchObject({message:r.errors.UNCERTAIN,uncertain:true,review:true,guard:false});
 });
}
