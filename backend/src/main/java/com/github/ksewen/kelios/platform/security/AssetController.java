package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class AssetController {
 private final InventoryOperations ops;
 public AssetController(InventoryOperations ops){this.ops=ops;}
 @GetMapping("/tenants/{tenant}/assets") public Page<AssetDtos.Item> list(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return AssetDtos.page(ops.list(tenant,AssetDtos.query(q)));}
 @GetMapping("/assets") public Page<AssetDtos.Item> all(@RequestParam Map<String,String> input){var q=new LinkedHashMap<>(input);UUID tenant=com.github.ksewen.kelios.platform.ListInput.uuid(q.remove("tenantId"));return AssetDtos.page(ops.all(tenant,AssetDtos.query(q)));}
 @GetMapping("/assets/{id}") public AssetDtos.Detail detail(@PathVariable UUID id){return AssetDtos.detail(ops.detail(id));}
 @GetMapping("/assets/{id}/events") public Page<AssetDtos.Event> events(@PathVariable UUID id,@RequestParam Map<String,String> q){return AssetDtos.events(ops.events(id,AssetDtos.history(q)));}
 @PostMapping("/tenants/{tenant}/assets") public ResponseEntity<AssetDtos.State> register(@PathVariable UUID tenant,@RequestBody AssetDtos.Registration b){var a=ops.register(tenant,b.model());return ResponseEntity.created(URI.create("/api/v1/assets/"+a.id())).body(AssetDtos.state(a));}
 @PutMapping("/assets/{id}/metadata") public AssetDtos.State text(@PathVariable UUID id,@RequestBody AssetDtos.Text b){return AssetDtos.state(ops.text(id,b.version(),b.serialNumber(),b.externalInventoryNumber(),b.note(),b.localIdentifier()));}
 @PutMapping("/assets/{id}/catalog") public AssetDtos.State catalog(@PathVariable UUID id,@RequestBody AssetDtos.Catalog b){return AssetDtos.state(ops.catalog(id,b.version(),b.selection()));}
 @PutMapping("/assets/{id}/loanability") public AssetDtos.State loanability(@PathVariable UUID id,@RequestBody AssetDtos.Loanability b){return AssetDtos.state(ops.loanability(id,b.version(),b.loanable()));}
 @PostMapping("/assets/{id}/lifecycle-transitions") public AssetDtos.State lifecycle(@PathVariable UUID id,@RequestBody AssetDtos.Lifecycle b){return AssetDtos.state(ops.lifecycle(id,b.version(),b.target(),b.reason(),b.note()));}
 @PostMapping("/assets/{id}/condition-transitions") public AssetDtos.State condition(@PathVariable UUID id,@RequestBody AssetDtos.Condition b){return AssetDtos.state(ops.condition(id,b.version(),b.target(),b.note()));}
 @PutMapping("/assets/{id}/location") public AssetDtos.State location(@PathVariable UUID id,@RequestBody AssetDtos.Location b){return AssetDtos.state(ops.location(id,b.version(),b.locationId()));}
 @PutMapping("/assets/{id}/owner") public AssetDtos.State owner(@PathVariable UUID id,@RequestBody AssetDtos.Owner b){return AssetDtos.state(ops.owner(id,b.version(),b.ownerId()));}
 @PostMapping("/assets/{id}/loan") public AssetDtos.State loan(@PathVariable UUID id,@RequestBody AssetDtos.Loan b){return AssetDtos.state(ops.loan(id,b.version(),b.borrower(),b.expectedReturnAt(),b.loanNote()));}
 @PostMapping("/assets/{id}/return") public AssetDtos.State returned(@PathVariable UUID id,@RequestBody AssetDtos.Return b){return AssetDtos.state(ops.returned(id,b.version()));}
}
