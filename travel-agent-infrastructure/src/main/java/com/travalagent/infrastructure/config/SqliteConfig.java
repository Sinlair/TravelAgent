package com.travalagent.infrastructure.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Configuration(proxyBeanMethods = false)
public class SqliteConfig {

    @Bean
    InitializingBean sqlitePragmas(DataSource dataSource) {
        return () -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
                statement.execute("PRAGMA busy_timeout=5000");
                ensureColumn(connection, statement, "conversation_message", "metadata_json", "TEXT");
                ensureColumn(connection, statement, "conversation_image_context", "facts_json", "TEXT");
            }
        };
    }

    private void ensureColumn(Connection connection, Statement statement, String tableName, String columnName, String definition)
            throws Exception {
        try (Statement pragmaStatement = connection.createStatement();
             ResultSet rs = pragmaStatement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        statement.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }
}
