package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/audit-log")
public class AuditController {
 private final InventoryOperations ops;
 public AuditController(InventoryOperations ops){this.ops=ops;}
 @GetMapping public Page<Map<String,Object>> list(@RequestParam Map<String,String> q){return ops.audit(q);}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable UUID id){return ops.audit(id);}
}
