package com.caciopee.loganalyzer.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "app.archive.enabled", havingValue = "true")
public class ArchiveDataSourceConfig {

    @Bean(name = "archiveDataSource")
    public DataSource archiveDataSource(ImportArchiveProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.getDatasource().getUrl());
        dataSource.setUsername(properties.getDatasource().getUsername());
        dataSource.setPassword(properties.getDatasource().getPassword());
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(1);
        dataSource.setPoolName("archive-pool");
        return dataSource;
    }

    @Bean(name = "archiveJdbcTemplate")
    public JdbcTemplate archiveJdbcTemplate(DataSource archiveDataSource) {
        return new JdbcTemplate(archiveDataSource);
    }
}
