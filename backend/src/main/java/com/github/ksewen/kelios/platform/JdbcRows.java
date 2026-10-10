package com.github.ksewen.kelios.platform;
import java.sql.*;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.databind.json.JsonMapper;
/** Read DTO conversion only; queries and authorization stay in their concrete services. */
public final class JdbcRows {
    private JdbcRows() {}
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public static final RowMapper<Map<String,Object>> MAP=(rs,n)->{
        Map<String,Object> row=new LinkedHashMap<>();
        for(int i=1;i<=rs.getMetaData().getColumnCount();i++){
            String name=rs.getMetaData().getColumnLabel(i);StringBuilder key=new StringBuilder();boolean upper=false;
            for(char c:name.toCharArray()){if(c=='_')upper=true;else{key.append(upper?Character.toUpperCase(c):c);upper=false;}}
            Object value=rs.getObject(i);
            if(value instanceof Timestamp t)value=t.toInstant();
            if(name.equals("data") && value!=null)value=JSON.readValue(value.toString(),Map.class);
            row.put(key.toString(),value);
        }return row;
    };
}
