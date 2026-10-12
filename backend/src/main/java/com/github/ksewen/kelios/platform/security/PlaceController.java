package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.inventory.PlaceService;
import com.github.ksewen.kelios.inventory.PlaceOperations;
import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1")
public class PlaceController {
 private final PlaceOperations places;
 public PlaceController(PlaceOperations places){this.places=places;}
 @GetMapping("/tenants/{tenant}/locations") public Page<PlaceDtos.Item> locations(@PathVariable UUID tenant,@RequestParam Map<String,String> query){return PlaceDtos.page(places.list(PlaceService.Kind.LOCATION,tenant,PlaceDtos.query(query)));}
 @PostMapping("/tenants/{tenant}/locations") public ResponseEntity<PlaceDtos.Created> createLOCATION(@PathVariable UUID tenant,@RequestBody PlaceDtos.Command command){UUID id=places.create(PlaceService.Kind.LOCATION,tenant,command.model());return ResponseEntity.created(URI.create("/api/v1/locations/"+id)).body(new PlaceDtos.Created(id));}
 @GetMapping("/locations/{id}") public PlaceDtos.Detail getLOCATION(@PathVariable UUID id){return PlaceDtos.detail(places.detail(PlaceService.Kind.LOCATION,id));}
 @PutMapping("/locations/{id}") public ResponseEntity<Void> editLOCATION(@PathVariable UUID id,@RequestBody PlaceDtos.Command command){places.edit(PlaceService.Kind.LOCATION,id,command.model());return ResponseEntity.noContent().build();}
 @DeleteMapping("/locations/{id}") public ResponseEntity<Void> deleteLOCATION(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body){new CommandInput(body==null?Map.of():body);places.delete(PlaceService.Kind.LOCATION,id);return ResponseEntity.noContent().build();}
 @GetMapping("/tenants/{tenant}/owners") public Page<PlaceDtos.Item> owners(@PathVariable UUID tenant,@RequestParam Map<String,String> query){return PlaceDtos.page(places.list(PlaceService.Kind.OWNER,tenant,PlaceDtos.query(query)));}
 @PostMapping("/tenants/{tenant}/owners") public ResponseEntity<PlaceDtos.Created> createOWNER(@PathVariable UUID tenant,@RequestBody PlaceDtos.Command command){UUID id=places.create(PlaceService.Kind.OWNER,tenant,command.model());return ResponseEntity.created(URI.create("/api/v1/owners/"+id)).body(new PlaceDtos.Created(id));}
 @GetMapping("/owners/{id}") public PlaceDtos.Detail getOWNER(@PathVariable UUID id){return PlaceDtos.detail(places.detail(PlaceService.Kind.OWNER,id));}
 @PutMapping("/owners/{id}") public ResponseEntity<Void> editOWNER(@PathVariable UUID id,@RequestBody PlaceDtos.Command command){places.edit(PlaceService.Kind.OWNER,id,command.model());return ResponseEntity.noContent().build();}
 @DeleteMapping("/owners/{id}") public ResponseEntity<Void> deleteOWNER(@PathVariable UUID id,@RequestBody(required=false) Map<String,Object> body){new CommandInput(body==null?Map.of():body);places.delete(PlaceService.Kind.OWNER,id);return ResponseEntity.noContent().build();}
}
