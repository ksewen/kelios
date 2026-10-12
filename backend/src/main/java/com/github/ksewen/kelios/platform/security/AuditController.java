package com.github.ksewen.kelios.platform.security;
import com.github.ksewen.kelios.platform.Page;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/audit-log")
public class AuditController {
 private final InventoryOperations ops;
 public AuditController(InventoryOperations ops){this.ops=ops;}
 @GetMapping public Page<AuditDtos.Item> list(@RequestParam Map<String,String> q){return AuditDtos.page(ops.audit(AuditDtos.query(q)));}
 @GetMapping("/{id}") public AuditDtos.Item detail(@PathVariable UUID id){return AuditDtos.item(ops.audit(id));}
}
