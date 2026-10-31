package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.platform.BusinessFailure;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Bounded CSV decode and parse only; this class performs no business lookups or writes. */
@Component
public class AssetCsvParser {
    static final int MAX_FILE=5*1024*1024,MAX_ROWS=5_000,MAX_COLUMNS=64,MAX_CELL=32*1024;
    public record Row(long rowNumber,long lineNumber,Map<String,String> values) {}
    record Parsed(String sha256,String encoding,String delimiter,List<String> headers,List<Row> rows) {}
    public record ParsePreview(String sha256,String encoding,String delimiter,List<String> headers,long totalRows,List<Row> rows) {}

    Parsed parse(MultipartFile file,String encoding,String delimiter){
        return parse(file,encoding,delimiter,true);
    }
    Parsed parse(MultipartFile file,String encoding,String delimiter,boolean headerPresent){
        if(file==null || file.isEmpty() || file.getSize()>MAX_FILE)throw BusinessFailure.invalid("CSV file required (maximum 5 MiB)");
        try{
            byte[] bytes=file.getBytes();
            if(bytes.length>MAX_FILE)throw BusinessFailure.invalid("CSV file exceeds 5 MiB");
            String selectedEncoding=encoding==null?"AUTO":encoding.toUpperCase(Locale.ROOT);
            String text=decode(bytes,selectedEncoding);
            String usedEncoding=selectedEncoding.equals("AUTO")?"UTF-8":canonicalEncoding(selectedEncoding);
            String requested=delimiter==null?"AUTO":delimiter;
            if(requested.equalsIgnoreCase("AUTO"))requested="AUTO";
            if(!requested.equals("AUTO")){
                char c=separator(requested);
                return records(bytes,text,usedEncoding,c,headerPresent);
            }
            Parsed detected=null;
            for(char c:new char[]{';',',','\t'}){
                try{
                    Parsed candidate=records(bytes,text,usedEncoding,c,headerPresent);
                    if(candidate.headers().size()<2)continue;
                    if(detected!=null)throw BusinessFailure.invalid("CSV delimiter is ambiguous; select one explicitly");
                    detected=candidate;
                }catch(BusinessFailure ex){
                    if(ex.getMessage().contains("ambiguous"))throw ex;
                }
            }
            if(detected==null)throw BusinessFailure.invalid("CSV delimiter could not be detected; select one explicitly");
            return detected;
        }catch(IOException ex){throw BusinessFailure.invalid("CSV file could not be read");}
    }
    ParsePreview parsePreview(MultipartFile file,String encoding,String delimiter,boolean headerPresent){
        Parsed parsed=parse(file,encoding,delimiter,headerPresent);
        return new ParsePreview(parsed.sha256(),parsed.encoding(),parsed.delimiter(),parsed.headers(),parsed.rows().size(),
            parsed.rows().subList(0,Math.min(10,parsed.rows().size())));
    }
    private static String canonicalEncoding(String encoding){
        return switch(encoding){
            case "UTF-8","UTF8"->"UTF-8";
            case "WINDOWS-1252","WINDOWS1252","CP1252"->"windows-1252";
            case "ISO-8859-1","LATIN1","LATIN-1"->"ISO-8859-1";
            default->throw BusinessFailure.invalid("Unsupported CSV encoding");
        };
    }
    private static String decode(byte[] bytes,String encoding){
        String name=encoding.equals("AUTO")?"UTF-8":canonicalEncoding(encoding);
        int offset=bytes.length>=3 && (bytes[0]&255)==0xef && (bytes[1]&255)==0xbb && (bytes[2]&255)==0xbf?3:0;
        if(offset!=0 && !name.equals("UTF-8"))throw BusinessFailure.invalid("UTF-8 BOM conflicts with selected encoding");
        try{
            return Charset.forName(name).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes,offset,bytes.length-offset)).toString();
        }catch(CharacterCodingException ex){
            throw BusinessFailure.invalid(encoding.equals("AUTO")?"Select the CSV encoding explicitly":"CSV bytes do not match selected encoding");
        }
    }
    private static char separator(String value){
        char c=switch(value.toUpperCase(Locale.ROOT)){
            case "SEMICOLON"->';';case "COMMA"->',';case "TAB"->'\t';
            default->{if(value.length()!=1)throw BusinessFailure.invalid("Invalid CSV delimiter");yield value.charAt(0);}
        };
        if(c=='"'||c=='\r'||c=='\n'||(Character.isISOControl(c)&&c!='\t'))throw BusinessFailure.invalid("Invalid CSV delimiter");
        return c;
    }
    private static Parsed records(byte[] bytes,String text,String encoding,char delimiter,boolean headerPresent){
        var format=CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setQuote('"').setIgnoreEmptyLines(false)
            .setLenientEof(false).setTrailingData(false).get();
        try(var csv=format.parse(new StringReader(text))){
            var iterator=csv.iterator();
            if(!iterator.hasNext())throw BusinessFailure.invalid("CSV record required");
            var first=iterator.next();
            if(first.size()==0 || first.size()>MAX_COLUMNS)throw BusinessFailure.invalid("CSV has invalid column count");
            var headers=new ArrayList<String>();
            if(headerPresent){
                var seen=new HashSet<String>();
                for(String field:first){
                    String header=field.strip();
                    if(header.isEmpty() || header.getBytes(StandardCharsets.UTF_8).length>MAX_CELL ||
                        header.codePoints().anyMatch(c->Character.isISOControl(c) || c==0x2028 || c==0x2029) ||
                        !seen.add(header.toLowerCase(Locale.ROOT)))throw BusinessFailure.invalid("CSV has empty or duplicate headers");
                    headers.add(header);
                }
            }else{
                for(int i=0;i<first.size();i++)headers.add("column"+(i+1));
            }
            var rows=new ArrayList<Row>();
            var lines=new LineCounter(text);
            if(!headerPresent)addRow(rows,first,headers,lines);
            while(iterator.hasNext()){
                var record=iterator.next();
                if(rows.size()>=MAX_ROWS)throw BusinessFailure.invalid("CSV exceeds 5000 data rows at record "+record.getRecordNumber()+", line "+lines.at(record.getCharacterPosition()));
                addRow(rows,record,headers,lines);
            }
            return new Parsed(hex(bytes),encoding,String.valueOf(delimiter),List.copyOf(headers),List.copyOf(rows));
        }catch(IOException|UncheckedIOException ex){throw BusinessFailure.invalid("Malformed CSV");}
    }
    private static void addRow(List<Row> rows,CSVRecord record,List<String> headers,LineCounter lines){
        long line=lines.at(record.getCharacterPosition());
        if(record.size()!=headers.size())throw BusinessFailure.invalid("CSV record "+record.getRecordNumber()+", line "+line+" has a different column count");
        var values=new LinkedHashMap<String,String>();
        for(int i=0;i<headers.size();i++){
            String value=record.get(i);
            if(value.getBytes(StandardCharsets.UTF_8).length>MAX_CELL)throw BusinessFailure.invalid("CSV cell exceeds 32 KiB at record "+record.getRecordNumber()+", line "+line);
            values.put(headers.get(i),value);
        }
        rows.add(new Row(record.getRecordNumber(),line,values));
    }
    private static final class LineCounter {
        private final String text;
        private int position;
        private long line=1;
        private LineCounter(String text){this.text=text;}
        private long at(long target){
            while(position<target){
                char c=text.charAt(position++);
                if(c=='\r' || (c=='\n' && (position<2 || text.charAt(position-2)!='\r')))line++;
            }
            return line;
        }
    }
    private static String hex(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException ex){throw new AssertionError(ex);}
    }
}
