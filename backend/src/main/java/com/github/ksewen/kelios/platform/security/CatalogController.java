package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.catalog.CatalogService;
import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class CatalogController {
 private final InventoryOperations ops;
 public CatalogController(InventoryOperations ops){this.ops=ops;}
 @GetMapping("/catalog/categories") public Page<Map<String,Object>> categories(@RequestParam Map<String,String> q){return ops.catalogList(CatalogService.Kind.CATEGORY,null,q);}
 @GetMapping("/tenants/{tenant}/catalog/categories") public Page<Map<String,Object>> categoriesTenant(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return ops.catalogList(CatalogService.Kind.CATEGORY,tenant,q);}
 @GetMapping("/catalog/categories/{id}") public CatalogService.Entry categoriesDetail(@PathVariable UUID id){return ops.entry(CatalogService.Kind.CATEGORY,id);}
 @PostMapping("/catalog/categories") public ResponseEntity<Map<String,UUID>> createCategory(@RequestBody Map<String,Object> b){return createCategoryIn(null,b);}
 @PostMapping("/tenants/{tenant}/catalog/categories") public ResponseEntity<Map<String,UUID>> createCategoryTenant(@PathVariable UUID tenant,@RequestBody Map<String,Object> b){return createCategoryIn(tenant,b);}
 private ResponseEntity<Map<String,UUID>> createCategoryIn(UUID tenant,Map<String,Object> b){var c=new CommandInput(b,"name");UUID id=ops.createCategory(tenant,c.text("name"));return ResponseEntity.created(URI.create("/api/v1/catalog/categories/"+id)).body(Map.of("id",id));}
 @PutMapping("/catalog/categories/{id}") public ResponseEntity<Void> editCategory(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name");ops.editCategory(id,c.text("name"));return ResponseEntity.noContent().build();}
 @DeleteMapping("/catalog/categories/{id}") public ResponseEntity<Void> deleteCategory(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.deleteCatalog(CatalogService.Kind.CATEGORY,id);return ResponseEntity.noContent().build();}
 @PostMapping("/catalog/categories/{id}/publish") public ResponseEntity<Void> publishCategory(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.publish(CatalogService.Kind.CATEGORY,id);return ResponseEntity.noContent().build();}
 @PostMapping("/catalog/categories/merge-preview") public CatalogService.MergePreview previewCategory(@RequestBody Map<String,Object> b){var c=new CommandInput(b,"sourceId","targetId");return ops.preview(CatalogService.MergeKind.CATEGORY,c.id("sourceId",true),c.id("targetId",true));}
 @PostMapping("/catalog/categories/merge") public ResponseEntity<Void> mergeCategory(@RequestBody Map<String,Object> b){var c=new CommandInput(b,"sourceId","targetId");ops.merge(CatalogService.MergeKind.CATEGORY,c.id("sourceId",true),c.id("targetId",true));return ResponseEntity.noContent().build();}
 @GetMapping("/catalog/manufacturers") public Page<Map<String,Object>> manufacturers(@RequestParam Map<String,String> q){return ops.catalogList(CatalogService.Kind.MANUFACTURER,null,q);}
 @GetMapping("/tenants/{tenant}/catalog/manufacturers") public Page<Map<String,Object>> manufacturersTenant(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return ops.catalogList(CatalogService.Kind.MANUFACTURER,tenant,q);}
 @GetMapping("/catalog/manufacturers/{id}") public CatalogService.Entry manufacturersDetail(@PathVariable UUID id){return ops.entry(CatalogService.Kind.MANUFACTURER,id);}
 @PostMapping("/catalog/manufacturers") public ResponseEntity<Map<String,UUID>> createManufacturer(@RequestBody Map<String,Object> b){return createManufacturerIn(null,b);}
 @PostMapping("/tenants/{tenant}/catalog/manufacturers") public ResponseEntity<Map<String,UUID>> createManufacturerTenant(@PathVariable UUID tenant,@RequestBody Map<String,Object> b){return createManufacturerIn(tenant,b);}
 private ResponseEntity<Map<String,UUID>> createManufacturerIn(UUID tenant,Map<String,Object> b){var c=new CommandInput(b,"name");UUID id=ops.createManufacturer(tenant,c.text("name"));return ResponseEntity.created(URI.create("/api/v1/catalog/manufacturers/"+id)).body(Map.of("id",id));}
 @PutMapping("/catalog/manufacturers/{id}") public ResponseEntity<Void> editManufacturer(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name");ops.editManufacturer(id,c.text("name"));return ResponseEntity.noContent().build();}
 @DeleteMapping("/catalog/manufacturers/{id}") public ResponseEntity<Void> deleteManufacturer(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.deleteCatalog(CatalogService.Kind.MANUFACTURER,id);return ResponseEntity.noContent().build();}
 @PostMapping("/catalog/manufacturers/{id}/publish") public ResponseEntity<Void> publishManufacturer(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.publish(CatalogService.Kind.MANUFACTURER,id);return ResponseEntity.noContent().build();}
 @PostMapping("/catalog/manufacturers/merge-preview") public CatalogService.MergePreview previewManufacturer(@RequestBody Map<String,Object> b){var c=new CommandInput(b,"sourceId","targetId");return ops.preview(CatalogService.MergeKind.MANUFACTURER,c.id("sourceId",true),c.id("targetId",true));}
 @PostMapping("/catalog/manufacturers/merge") public ResponseEntity<Void> mergeManufacturer(@RequestBody Map<String,Object> b){var c=new CommandInput(b,"sourceId","targetId");ops.merge(CatalogService.MergeKind.MANUFACTURER,c.id("sourceId",true),c.id("targetId",true));return ResponseEntity.noContent().build();}
 @GetMapping("/catalog/products") public Page<Map<String,Object>> products(@RequestParam Map<String,String> q){return ops.catalogList(CatalogService.Kind.PRODUCT,null,q);}
 @GetMapping("/tenants/{tenant}/catalog/products") public Page<Map<String,Object>> productsTenant(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return ops.catalogList(CatalogService.Kind.PRODUCT,tenant,q);}
 @GetMapping("/catalog/products/{id}") public CatalogService.Entry productsDetail(@PathVariable UUID id){return ops.entry(CatalogService.Kind.PRODUCT,id);}
 @PostMapping("/catalog/products") public ResponseEntity<Map<String,UUID>> createProduct(@RequestBody Map<String,Object> b){return createProductIn(null,b);}
 @PostMapping("/tenants/{tenant}/catalog/products") public ResponseEntity<Map<String,UUID>> createProductTenant(@PathVariable UUID tenant,@RequestBody Map<String,Object> b){return createProductIn(tenant,b);}
 private ResponseEntity<Map<String,UUID>> createProductIn(UUID tenant,Map<String,Object> b){var c=new CommandInput(b,"name","categoryId","manufacturerId","manufacturerNumber");UUID id=ops.createProduct(tenant,c.id("categoryId",true),c.id("manufacturerId",true),c.text("name"),c.text("manufacturerNumber"));return ResponseEntity.created(URI.create("/api/v1/catalog/products/"+id)).body(Map.of("id",id));}
 @PutMapping("/catalog/products/{id}") public ResponseEntity<Void> editProduct(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name","categoryId","manufacturerId","manufacturerNumber");ops.editProduct(id,c.id("categoryId",true),c.id("manufacturerId",true),c.text("name"),c.text("manufacturerNumber"));return ResponseEntity.noContent().build();}
 @DeleteMapping("/catalog/products/{id}") public ResponseEntity<Void> deleteProduct(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.deleteCatalog(CatalogService.Kind.PRODUCT,id);return ResponseEntity.noContent().build();}
 @PostMapping("/catalog/products/{id}/publish") public ResponseEntity<Void> publishProduct(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.publish(CatalogService.Kind.PRODUCT,id);return ResponseEntity.noContent().build();}
}
