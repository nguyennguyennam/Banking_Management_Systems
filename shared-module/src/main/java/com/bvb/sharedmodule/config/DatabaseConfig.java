package com.bvb.sharedmodule.config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.logging.Logger;

@Slf4j
@Configuration

public class DatabaseConfig {
    private static final Logger logger = Logger.getLogger(DatabaseConfig.class.getName());
    @Value("${spring.datasource.url}")
    private String databaseUrl;
    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.hikari.maximum-pool-size:10}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minIdle;

    @Bean
    public DataSource getConnection () {
        HikariConfig cf = new HikariConfig();
        cf.setJdbcUrl(databaseUrl);
        cf.setUsername(username);
        cf.setPassword(password);
        cf.setMaximumPoolSize(maxPoolSize);
        cf.setMinimumIdle(minIdle);

        HikariDataSource ds = new HikariDataSource(cf);

        try (Connection conn = ds.getConnection()) {
            if (conn != null) {
                logger.info("Successfully connected to the database.");
            }
        }
        catch (Exception e) {
            throw  new RuntimeException("Failed to connect to the database: " + e.getMessage(), e);
        }
        return ds;
    }
}
