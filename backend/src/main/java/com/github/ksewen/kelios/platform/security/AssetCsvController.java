package com.github.ksewen.kelios.platform.security;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/asset-csv")
public class AssetCsvController {
    private final AssetCsvExportService exporter;
    public AssetCsvController(AssetCsvExportService exporter){this.exporter=exporter;}
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String,String> query){
        return ResponseEntity.ok()
            .contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=assets.csv")
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .body(exporter.export(query));
    }
}
