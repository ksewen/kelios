package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.inventory.PlaceService;
import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class PlaceController {
 private final InventoryOperations ops;
 public PlaceController(InventoryOperations ops){this.ops=ops;}
 @GetMapping("/tenants/{tenant}/locations") public Page<Map<String,Object>> locations(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return ops.places(PlaceService.Kind.LOCATION,tenant,q);}
 @PostMapping("/tenants/{tenant}/locations") public ResponseEntity<Map<String,UUID>> createLOCATION(@PathVariable UUID tenant,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name");UUID id=ops.createPlace(PlaceService.Kind.LOCATION,tenant,c.text("name"));return ResponseEntity.created(URI.create("/api/v1/locations/"+id)).body(Map.of("id",id));}
 @GetMapping("/locations/{id}") public PlaceService.Place getLOCATION(@PathVariable UUID id){return ops.place(PlaceService.Kind.LOCATION,id);}
 @PutMapping("/locations/{id}") public ResponseEntity<Void> editLOCATION(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name");ops.editPlace(PlaceService.Kind.LOCATION,id,c.text("name"));return ResponseEntity.noContent().build();}
 @DeleteMapping("/locations/{id}") public ResponseEntity<Void> deleteLOCATION(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.deletePlace(PlaceService.Kind.LOCATION,id);return ResponseEntity.noContent().build();}
 @GetMapping("/tenants/{tenant}/owners") public Page<Map<String,Object>> owners(@PathVariable UUID tenant,@RequestParam Map<String,String> q){return ops.places(PlaceService.Kind.OWNER,tenant,q);}
 @PostMapping("/tenants/{tenant}/owners") public ResponseEntity<Map<String,UUID>> createOWNER(@PathVariable UUID tenant,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name");UUID id=ops.createPlace(PlaceService.Kind.OWNER,tenant,c.text("name"));return ResponseEntity.created(URI.create("/api/v1/owners/"+id)).body(Map.of("id",id));}
 @GetMapping("/owners/{id}") public PlaceService.Place getOWNER(@PathVariable UUID id){return ops.place(PlaceService.Kind.OWNER,id);}
 @PutMapping("/owners/{id}") public ResponseEntity<Void> editOWNER(@PathVariable UUID id,@RequestBody Map<String,Object> b){var c=new CommandInput(b,"name");ops.editPlace(PlaceService.Kind.OWNER,id,c.text("name"));return ResponseEntity.noContent().build();}
 @DeleteMapping("/owners/{id}") public ResponseEntity<Void> deleteOWNER(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> b){new CommandInput(b==null?Map.of():b);ops.deletePlace(PlaceService.Kind.OWNER,id);return ResponseEntity.noContent().build();}
}
