package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.inventory.*;
import com.github.ksewen.kelios.platform.*;
import java.util.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class AssetController {
 private final InventoryOperations ops;
 public AssetController(InventoryOperations ops){this.ops=ops;}
 @GetMapping("/tenants/{tenant}/assets") public Page<Map<String,Object>> list(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return ops.list(tenant,q);}
 @GetMapping("/assets") public Page<Map<String,Object>> all(@RequestParam Map<String,String> q){return ops.all(q);}
 @GetMapping("/assets/{id}") public Map<String,Object> detail(@PathVariable UUID id){return ops.detail(id);}
 @GetMapping("/assets/{id}/events") public Page<Map<String,Object>> events(@PathVariable UUID id,@RequestParam Map<String,String> q){return ops.events(id,q);}
 @PostMapping("/tenants/{tenant}/assets") public ResponseEntity<AssetService.View> register(@PathVariable UUID tenant,@RequestBody Map<String,Object> body){var c=new CommandInput(body,"productId","categoryId","manufacturerId","locationId","ownerId","loanable","serialNumber","externalInventoryNumber","note","localIdentifier");var a=ops.register(tenant,new AssetService.Registration(c.catalog(),c.id("locationId",true),c.id("ownerId",false),c.bool("loanable"),c.text("serialNumber"),c.text("externalInventoryNumber"),c.text("note"),c.text("localIdentifier")));return ResponseEntity.created(URI.create("/api/v1/assets/"+a.id())).body(a);}
 @PutMapping("/assets/{id}/metadata") public AssetService.View text(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","serialNumber","externalInventoryNumber","note","localIdentifier");return ops.text(id,c.version(),c.text("serialNumber"),c.text("externalInventoryNumber"),c.text("note"),c.text("localIdentifier"));}
 @PutMapping("/assets/{id}/catalog") public AssetService.View catalog(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","productId","categoryId","manufacturerId");return ops.catalog(id,c.version(),c.catalog());}
 @PutMapping("/assets/{id}/loanability") public AssetService.View loanability(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","loanable");return ops.loanability(id,c.version(),c.bool("loanable"));}
 @PostMapping("/assets/{id}/lifecycle-transitions") public AssetService.View lifecycle(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","target","reason","note");return ops.lifecycle(id,c.version(),c.state("target",LifecycleState.class,true),c.state("reason",DeregistrationReason.class,false),c.text("note"));}
 @PostMapping("/assets/{id}/condition-transitions") public AssetService.View condition(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","target","note");return ops.condition(id,c.version(),c.state("target",ConditionState.class,true),c.text("note"));}
 @PutMapping("/assets/{id}/location") public AssetService.View location(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","locationId");return ops.location(id,c.version(),c.id("locationId",true));}
 @PutMapping("/assets/{id}/owner") public AssetService.View owner(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","ownerId");c.present("ownerId");return ops.owner(id,c.version(),c.id("ownerId",false));}
 @PostMapping("/assets/{id}/loan") public AssetService.View loan(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion","borrower","expectedReturnAt","loanNote");return ops.loan(id,c.version(),c.text("borrower"),c.instant("expectedReturnAt"),c.text("loanNote"));}
 @PostMapping("/assets/{id}/return") public AssetService.View returned(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"expectedVersion");return ops.returned(id,c.version());}
}
