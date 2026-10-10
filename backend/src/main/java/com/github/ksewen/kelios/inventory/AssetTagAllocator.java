package com.github.ksewen.kelios.inventory;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
@Component
public class AssetTagAllocator {
    private final JdbcTemplate jdbc;
    public AssetTagAllocator(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    @Transactional(propagation=Propagation.MANDATORY)
    public String allocate(Instant registeredAt) {
        var date=registeredAt.atOffset(ZoneOffset.UTC).toLocalDate();
        Long sequence=jdbc.queryForObject("INSERT INTO asset_tag_counter(registration_date,last_value) VALUES(?,1) ON CONFLICT(registration_date) DO UPDATE SET last_value=asset_tag_counter.last_value+1 RETURNING last_value",Long.class,java.sql.Date.valueOf(date));
        return date.format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%03d",sequence);
    }
}
