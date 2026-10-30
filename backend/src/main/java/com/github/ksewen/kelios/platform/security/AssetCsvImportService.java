package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.identity.auth.Authorization;
import com.github.ksewen.kelios.inventory.AssetCsvImportRowWriter;
import com.github.ksewen.kelios.platform.BusinessFailure;
import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Best-effort coordinator; only the separate row writer opens mutation transactions. */
@Service
public class AssetCsvImportService {
    public record Input(AssetCsvPreviewService.Input preview,List<Long> selectedRows) {}
    public record Outcome(long rowNumber,String status,UUID assetId,String assetTag,String reason) {}
    public record Result(long successful,long failed,long skipped,List<Outcome> rows,String failureCsv) {}
    private final AssetCsvPreviewService preview;
    private final AssetCsvImportRowWriter writer;
    public AssetCsvImportService(AssetCsvPreviewService preview,AssetCsvImportRowWriter writer){this.preview=preview;this.writer=writer;}

    public Result execute(MultipartFile file,Input input){
        var identity=CurrentIdentity.require();
        Authorization.globalAdmin(identity);
        if(input==null || input.preview()==null || input.selectedRows()==null || input.selectedRows().isEmpty()
            || input.selectedRows().size()>AssetCsvParser.MAX_ROWS || input.selectedRows().stream().anyMatch(Objects::isNull)
            || new HashSet<>(input.selectedRows()).size()!=input.selectedRows().size())
            throw BusinessFailure.invalid("Select distinct Preview rows to import");
        var current=preview.preview(file,input.preview());
        var selected=Set.copyOf(input.selectedRows());
        var known=new HashSet<Long>();
        for(var row:current.rows())known.add(row.rowNumber());
        if(!known.containsAll(selected))throw BusinessFailure.invalid("Selected CSV row does not exist");
        var outcomes=new ArrayList<Outcome>();
        var failedSources=new LinkedHashMap<Long,AssetCsvPreviewService.Row>();
        for(var row:current.rows()){
            Outcome outcome;
            if(!selected.contains(row.rowNumber())){
                outcome=new Outcome(row.rowNumber(),"SKIPPED",null,null,
                    row.status().equals("ERROR")?String.join("; ",row.issues()):"Not selected");
            }else if(row.status().equals("ERROR")){
                outcome=new Outcome(row.rowNumber(),"FAILED",null,null,
                    "Preview is no longer importable: "+String.join("; ",row.issues()));
            }else{
                try{
                    var created=writer.write(new AssetCsvImportRowWriter.Input(row.tenantId(),row.rowNumber(),
                        row.normalized(),row.references()),identity.actor());
                    outcome=new Outcome(row.rowNumber(),"SUCCESS",created.id(),created.tag(),null);
                }catch(BusinessFailure | DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException |
                    OptimisticLockingFailureException ex){
                    outcome=new Outcome(row.rowNumber(),"FAILED",null,null,reason(ex));
                }
            }
            outcomes.add(outcome);
            if(!outcome.status().equals("SUCCESS"))failedSources.put(row.rowNumber(),row);
        }
        return new Result(outcomes.stream().filter(o->o.status().equals("SUCCESS")).count(),
            outcomes.stream().filter(o->o.status().equals("FAILED")).count(),
            outcomes.stream().filter(o->o.status().equals("SKIPPED")).count(),List.copyOf(outcomes),
            failureCsv(current,failedSources,outcomes));
    }
    private static String reason(Exception ex){
        if(ex instanceof BusinessFailure business)return business.code()+": "+business.getMessage();
        if(ex instanceof OptimisticLockingFailureException)return "CONCURRENT_OPERATION: Reference changed; review and retry this row";
        return "CONSTRAINT_CONFLICT: Reference or unique value changed; review and retry this row";
    }
    private static String failureCsv(AssetCsvPreviewService.Preview preview,
        Map<Long,AssetCsvPreviewService.Row> sources,List<Outcome> outcomes){
        if(sources.isEmpty())return "";
        var output=new StringWriter();
        output.write('\ufeff');
        char separator=preview.delimiter().charAt(0);
        var format=CSVFormat.DEFAULT.builder().setDelimiter(separator).setRecordSeparator("\r\n").get();
        try(var csv=new CSVPrinter(output,format)){
            var headers=new ArrayList<>(preview.headers());headers.add("KELIOS import-error");
            csv.printRecord(headers.stream().map(AssetCsvImportService::safeCell).toList());
            for(var outcome:outcomes){
                var row=sources.get(outcome.rowNumber());
                if(row==null)continue;
                var cells=new ArrayList<String>();
                for(String header:preview.headers())cells.add(safeCell(row.source().get(header)));
                cells.add(safeCell(outcome.reason()));
                csv.printRecord(cells);
            }
        }catch(IOException ex){throw new IllegalStateException("Failure CSV could not be generated",ex);}
        if(output.getBuffer().length()>20*1024*1024)throw BusinessFailure.invalid("Failure CSV exceeds 20 MiB");
        return output.toString();
    }
    private static String safeCell(String value){
        if(value==null)return "";
        String leading=value.stripLeading();
        return !leading.isEmpty() && "=+-@".indexOf(leading.charAt(0))>=0?"'"+value:value;
    }
}
