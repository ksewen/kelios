package com.github.ksewen.kelios.platform.security;

import com.github.ksewen.kelios.platform.BusinessFailure;
import java.nio.charset.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class AssetCsvParserTest {
    private final AssetCsvParser parser=new AssetCsvParser();
    private static MockMultipartFile file(byte[] bytes){return new MockMultipartFile("file","legacy.csv","text/csv",bytes);}
    @Test void utf8BomQuotedMultilineAndAutomaticDelimiter(){
        var parsed=parser.parse(file("\ufeffKennung;Notiz\r\nÄ-1;\"erste; Zeile\nzweite \"\"Zeile\"\"\"\r\n".getBytes(StandardCharsets.UTF_8)),"AUTO","AUTO");
        assertEquals("UTF-8",parsed.encoding());assertEquals(";",parsed.delimiter());
        assertEquals(1,parsed.rows().size());assertEquals(2,parsed.rows().getFirst().rowNumber());
        assertEquals("erste; Zeile\nzweite \"Zeile\"",parsed.rows().getFirst().values().get("Notiz"));
    }
    @Test void legacyEncodingsRequireExplicitOverride(){
        byte[] cp="Name;Notiz\nMüller;€\n".getBytes(Charset.forName("windows-1252"));
        assertThrows(BusinessFailure.class,()->parser.parse(file(cp),"AUTO","AUTO"));
        assertEquals("€",parser.parse(file(cp),"WINDOWS-1252","SEMICOLON").rows().getFirst().values().get("Notiz"));
        byte[] latin="Name;Notiz\nMüller;äöüß\n".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("äöüß",parser.parse(file(latin),"ISO-8859-1",";").rows().getFirst().values().get("Notiz"));
        assertEquals("|",parser.parse(file("Name|Ort\nMüller|A315\n".getBytes(StandardCharsets.UTF_8)),"UTF-8","|").delimiter());
    }
    @Test void rejectsAmbiguousOrInvalidInput(){
        assertThrows(BusinessFailure.class,()->parser.parse(file("A;B,C\nx;y,z\n".getBytes(StandardCharsets.UTF_8)),"UTF-8","AUTO"));
        assertThrows(BusinessFailure.class,()->parser.parse(file("A;A\nx;y\n".getBytes(StandardCharsets.UTF_8)),"UTF-8",";"));
        assertThrows(BusinessFailure.class,()->parser.parse(file("A;B\nx\n".getBytes(StandardCharsets.UTF_8)),"UTF-8",";"));
        assertThrows(BusinessFailure.class,()->parser.parse(file("A;B\n\"unfinished;y".getBytes(StandardCharsets.UTF_8)),"UTF-8",";"));
        assertThrows(BusinessFailure.class,()->parser.parse(file(new byte[AssetCsvParser.MAX_FILE+1]),"AUTO",";"));
        assertThrows(BusinessFailure.class,()->parser.parse(file(("A;B\n"+"x;y\n".repeat(AssetCsvParser.MAX_ROWS+1)).getBytes(StandardCharsets.UTF_8)),"UTF-8",";"));
        assertThrows(BusinessFailure.class,()->parser.parse(file(("A;B\n"+"x".repeat(AssetCsvParser.MAX_CELL+1)+";y\n").getBytes(StandardCharsets.UTF_8)),"UTF-8",";"));
        assertThrows(BusinessFailure.class,()->parser.parse(file(("C;".repeat(AssetCsvParser.MAX_COLUMNS)+"Last\n").getBytes(StandardCharsets.UTF_8)),"UTF-8",";"));
    }
}
