package com.bvb.sharedmodule.config;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

public class FlywayConfig {
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)

                .locations("classpath:db/migration", "classpath:db.migration")

                .baselineOnMigrate(true)
                .baselineVersion("0")

                .validateOnMigrate(true)
                .outOfOrder(false)

                .load();
    }
}
