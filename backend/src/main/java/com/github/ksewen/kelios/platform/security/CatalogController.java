package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.catalog.ProductOperations;
import com.github.ksewen.kelios.catalog.CatalogLabel;
import com.github.ksewen.kelios.catalog.CatalogLabelOperations;
import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class CatalogController {
 private final ProductOperations products;
 private final CatalogLabelOperations labels;
 public CatalogController(ProductOperations products,CatalogLabelOperations labels){this.products=products;this.labels=labels;}
 @GetMapping("/catalog/categories")
 public Page<CatalogLabelDtos.Item> categories(@RequestParam Map<String,String> query) {
  return CatalogLabelDtos.page(labels.list(CatalogLabel.Kind.CATEGORY,null,CatalogLabelDtos.query(query,CatalogLabel.Kind.CATEGORY)));
 }
 @GetMapping("/tenants/{tenant}/catalog/categories")
 public Page<CatalogLabelDtos.Item> categoriesTenant(@PathVariable UUID tenant,@RequestParam Map<String,String> query) {
  return CatalogLabelDtos.page(labels.list(CatalogLabel.Kind.CATEGORY,tenant,CatalogLabelDtos.query(query,CatalogLabel.Kind.CATEGORY)));
 }
 @GetMapping("/catalog/categories/{id}")
 public CatalogLabelDtos.Detail categoriesDetail(@PathVariable UUID id) { return CatalogLabelDtos.detail(labels.detail(CatalogLabel.Kind.CATEGORY,id)); }
 @PostMapping("/catalog/categories")
 public ResponseEntity<CatalogLabelDtos.Created> createCategory(@RequestBody CatalogLabelDtos.NameCommand command) { return createCategoryIn(null,command); }
 @PostMapping("/tenants/{tenant}/catalog/categories")
 public ResponseEntity<CatalogLabelDtos.Created> createCategoryTenant(@PathVariable UUID tenant,@RequestBody CatalogLabelDtos.NameCommand command) { return createCategoryIn(tenant,command); }
 private ResponseEntity<CatalogLabelDtos.Created> createCategoryIn(UUID tenant,CatalogLabelDtos.NameCommand command) {
  UUID id=labels.create(CatalogLabel.Kind.CATEGORY,tenant,command.model());
  return ResponseEntity.created(URI.create("/api/v1/catalog/categories/"+id)).body(new CatalogLabelDtos.Created(id));
 }
 @PutMapping("/catalog/categories/{id}")
 public ResponseEntity<Void> editCategory(@PathVariable UUID id,@RequestBody CatalogLabelDtos.NameCommand command) {
  labels.edit(CatalogLabel.Kind.CATEGORY,id,command.model());return ResponseEntity.noContent().build();
 }
 @DeleteMapping("/catalog/categories/{id}")
 public ResponseEntity<Void> deleteCategory(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body) {
  new CommandInput(body==null?Map.of():body);labels.delete(CatalogLabel.Kind.CATEGORY,id);return ResponseEntity.noContent().build();
 }
 @PostMapping("/catalog/categories/{id}/publish")
 public ResponseEntity<Void> publishCategory(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body) {
  new CommandInput(body==null?Map.of():body);labels.publish(CatalogLabel.Kind.CATEGORY,id);return ResponseEntity.noContent().build();
 }
 @PostMapping("/catalog/categories/merge-preview")
 public CatalogLabelDtos.Preview previewCategory(@RequestBody CatalogLabelDtos.MergeCommand command) {
  return CatalogLabelDtos.preview(labels.preview(CatalogLabel.Kind.CATEGORY,command.sourceId(),command.targetId()));
 }
 @PostMapping("/catalog/categories/merge")
 public ResponseEntity<Void> mergeCategory(@RequestBody CatalogLabelDtos.MergeCommand command) {
  labels.merge(CatalogLabel.Kind.CATEGORY,command.sourceId(),command.targetId());return ResponseEntity.noContent().build();
 }
 @GetMapping("/catalog/manufacturers")
 public Page<CatalogLabelDtos.Item> manufacturers(@RequestParam Map<String,String> query) {
  return CatalogLabelDtos.page(labels.list(CatalogLabel.Kind.MANUFACTURER,null,CatalogLabelDtos.query(query,CatalogLabel.Kind.MANUFACTURER)));
 }
 @GetMapping("/tenants/{tenant}/catalog/manufacturers")
 public Page<CatalogLabelDtos.Item> manufacturersTenant(@PathVariable UUID tenant,@RequestParam Map<String,String> query) {
  return CatalogLabelDtos.page(labels.list(CatalogLabel.Kind.MANUFACTURER,tenant,CatalogLabelDtos.query(query,CatalogLabel.Kind.MANUFACTURER)));
 }
 @GetMapping("/catalog/manufacturers/{id}")
 public CatalogLabelDtos.Detail manufacturersDetail(@PathVariable UUID id) { return CatalogLabelDtos.detail(labels.detail(CatalogLabel.Kind.MANUFACTURER,id)); }
 @PostMapping("/catalog/manufacturers")
 public ResponseEntity<CatalogLabelDtos.Created> createManufacturer(@RequestBody CatalogLabelDtos.NameCommand command) { return createManufacturerIn(null,command); }
 @PostMapping("/tenants/{tenant}/catalog/manufacturers")
 public ResponseEntity<CatalogLabelDtos.Created> createManufacturerTenant(@PathVariable UUID tenant,@RequestBody CatalogLabelDtos.NameCommand command) { return createManufacturerIn(tenant,command); }
 private ResponseEntity<CatalogLabelDtos.Created> createManufacturerIn(UUID tenant,CatalogLabelDtos.NameCommand command) {
  UUID id=labels.create(CatalogLabel.Kind.MANUFACTURER,tenant,command.model());
  return ResponseEntity.created(URI.create("/api/v1/catalog/manufacturers/"+id)).body(new CatalogLabelDtos.Created(id));
 }
 @PutMapping("/catalog/manufacturers/{id}")
 public ResponseEntity<Void> editManufacturer(@PathVariable UUID id,@RequestBody CatalogLabelDtos.NameCommand command) {
  labels.edit(CatalogLabel.Kind.MANUFACTURER,id,command.model());return ResponseEntity.noContent().build();
 }
 @DeleteMapping("/catalog/manufacturers/{id}")
 public ResponseEntity<Void> deleteManufacturer(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body) {
  new CommandInput(body==null?Map.of():body);labels.delete(CatalogLabel.Kind.MANUFACTURER,id);return ResponseEntity.noContent().build();
 }
 @PostMapping("/catalog/manufacturers/{id}/publish")
 public ResponseEntity<Void> publishManufacturer(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body) {
  new CommandInput(body==null?Map.of():body);labels.publish(CatalogLabel.Kind.MANUFACTURER,id);return ResponseEntity.noContent().build();
 }
 @PostMapping("/catalog/manufacturers/merge-preview")
 public CatalogLabelDtos.Preview previewManufacturer(@RequestBody CatalogLabelDtos.MergeCommand command) {
  return CatalogLabelDtos.preview(labels.preview(CatalogLabel.Kind.MANUFACTURER,command.sourceId(),command.targetId()));
 }
 @PostMapping("/catalog/manufacturers/merge")
 public ResponseEntity<Void> mergeManufacturer(@RequestBody CatalogLabelDtos.MergeCommand command) {
  labels.merge(CatalogLabel.Kind.MANUFACTURER,command.sourceId(),command.targetId());return ResponseEntity.noContent().build();
 }
 @GetMapping("/catalog/products") public Page<ProductDtos.Item> products(@RequestParam Map<String,String> query){return ProductDtos.page(products.list(null,ProductDtos.query(query)));}
 @GetMapping("/tenants/{tenant}/catalog/products") public Page<ProductDtos.Item> productsTenant(@PathVariable UUID tenant,@RequestParam Map<String,String> query){return ProductDtos.page(products.list(tenant,ProductDtos.query(query)));}
 @GetMapping("/catalog/products/{id}") public ProductDtos.Detail productsDetail(@PathVariable UUID id){return ProductDtos.detail(products.detail(id));}
 @PostMapping("/catalog/products") public ResponseEntity<ProductDtos.Created> createProduct(@RequestBody ProductDtos.Command command){return createProductIn(null,command);}
 @PostMapping("/tenants/{tenant}/catalog/products") public ResponseEntity<ProductDtos.Created> createProductTenant(@PathVariable UUID tenant,@RequestBody ProductDtos.Command command){return createProductIn(tenant,command);}
 private ResponseEntity<ProductDtos.Created> createProductIn(UUID tenant,ProductDtos.Command command){UUID id=products.create(tenant,command.model());return ResponseEntity.created(URI.create("/api/v1/catalog/products/"+id)).body(new ProductDtos.Created(id));}
 @PutMapping("/catalog/products/{id}") public ResponseEntity<Void> editProduct(@PathVariable UUID id,@RequestBody ProductDtos.Command command){products.edit(id,command.model());return ResponseEntity.noContent().build();}
 @DeleteMapping("/catalog/products/{id}") public ResponseEntity<Void> deleteProduct(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body){new CommandInput(body==null?Map.of():body);products.delete(id);return ResponseEntity.noContent().build();}
 @PostMapping("/catalog/products/{id}/publish") public ResponseEntity<Void> publishProduct(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body){new CommandInput(body==null?Map.of():body);products.publish(id);return ResponseEntity.noContent().build();}
}
