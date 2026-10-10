package com.github.ksewen.kelios.platform;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SQLErrorCodeSQLExceptionTranslator;

@Configuration
public class JdbcConfiguration {
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Spring's PostgreSQL codes classify lock timeout (55P03) and deadlock (40P01).
        jdbc.setExceptionTranslator(new SQLErrorCodeSQLExceptionTranslator(dataSource));
        return jdbc;
    }
}
