package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.platform.*;
import com.github.ksewen.kelios.inventory.AssetService.*;
import java.util.*;
import java.time.Instant;
/** Strict command binding; only explicit mutable inputs enter application operations. */
final class CommandInput {
 private final Map<String,Object> body;
 CommandInput(Map<String,Object> body,String... fields){this.body=body; if(body==null || !Set.of(fields).containsAll(body.keySet()))throw BusinessFailure.invalid("Unknown command field");}
 String text(String key){Object v=body.get(key);if(v==null)return null;if(!(v instanceof String s))throw BusinessFailure.invalid("Text required: "+key);return s;}
 UUID id(String key,boolean required){UUID id=ListInput.uuid(text(key));if(required && id==null)throw BusinessFailure.invalid("Reference required: "+key);return id;}
 long version(){Object v=body.get("expectedVersion");if(!(v instanceof Integer || v instanceof Long) || ((Number)v).longValue()<0)throw BusinessFailure.invalid("Non-negative expectedVersion required");return ((Number)v).longValue();}
 boolean bool(String key){Object v=body.get(key);if(!(v instanceof Boolean b))throw BusinessFailure.invalid("Explicit boolean required: "+key);return b;}
 void present(String key){if(!body.containsKey(key))throw BusinessFailure.invalid("Field required: "+key);}
 <E extends Enum<E>> E state(String key,Class<E> type,boolean required){String value=text(key);if(value==null && !required)return null;try{return Enum.valueOf(type,value);}catch(IllegalArgumentException|NullPointerException e){throw BusinessFailure.invalid("Invalid "+key);}}
 Instant instant(String key){String s=text(key);try{return s==null?null:Instant.parse(s);}catch(java.time.DateTimeException ex){throw BusinessFailure.invalid("UTC instant required");}}
 CatalogSelection catalog(){UUID product=id("productId",false);if(product!=null){if(body.containsKey("categoryId") || body.containsKey("manufacturerId"))throw BusinessFailure.invalid("Product derives Category and Manufacturer");return new ProductSelection(product);}return new DirectSelection(id("categoryId",true),id("manufacturerId",true));}
}
