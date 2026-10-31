package com.github.ksewen.kelios.platform.security;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/asset-csv")
public class AssetCsvController {
    private final AssetCsvExportService exporter;
    private final AssetCsvPreviewService preview;
    private final AssetCsvImportService importer;
    public AssetCsvController(AssetCsvExportService exporter,AssetCsvPreviewService preview,AssetCsvImportService importer){
        this.exporter=exporter;this.preview=preview;this.importer=importer;
    }
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam Map<String,String> query){
        return ResponseEntity.ok()
            .contentType(new MediaType("text","csv",java.nio.charset.StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=assets.csv")
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .body(exporter.export(query));
    }
    @PostMapping(value="/parse-preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetCsvParser.ParsePreview parsePreview(@RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue="AUTO") String encoding,@RequestParam(defaultValue="AUTO") String delimiter,
        @RequestParam(defaultValue="true") boolean headerPresent){
        return preview.parsePreview(file,encoding,delimiter,headerPresent);
    }
    @PostMapping(value="/preview",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetCsvPreviewService.Preview preview(@RequestPart("file") MultipartFile file,
        @RequestPart("config") AssetCsvPreviewService.Input config){
        return preview.preview(file,config);
    }
    @PostMapping(value="/execute",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AssetCsvImportService.Result execute(@RequestPart("file") MultipartFile file,
        @RequestPart("config") AssetCsvImportService.Input config){
        return importer.execute(file,config);
    }
}
