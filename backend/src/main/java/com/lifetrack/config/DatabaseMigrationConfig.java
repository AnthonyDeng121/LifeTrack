package com.lifetrack.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.util.Set;

@Configuration
public class DatabaseMigrationConfig {
    @Bean
    TransactionOperations recordTransactions(PlatformTransactionManager manager) {
        TransactionTemplate tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        return tx;
    }

    @Bean
    FlywayMigrationStrategy migrationStrategy(@Value("${FLYWAY_ADOPT_BASELINE:false}") boolean adopt) {
        return flyway -> {
            if (adopt) {
                try (Connection c = flyway.getConfiguration().getDataSource().getConnection()) {
                    // 只接管已执行总脚本的数据库，不能给任意旧库静默盖章。
                    try (ResultSet rs = c.getMetaData().getTables(c.getCatalog(), null, "flyway_schema_history", null)) {
                        if (!rs.next()) {
                            Set<String> required = new java.util.HashSet<>(Set.of(
                                    "fk_task_user", "fk_sub_task", "fk_log_plan_owner", "fk_log_step",
                                    "fk_check_plan_owner", "fk_deferred_plan_owner", "fk_profile_user",
                                    "chk_plan_period", "chk_plan_target", "chk_plan_progress", "chk_step_progress",
                                    "chk_step_weight", "chk_log_contribution", "chk_duration_nonnegative",
                                    "uk_user_task_check_date", "uk_task_owner"));
                            try (PreparedStatement p = c.prepareStatement("select CONSTRAINT_NAME from information_schema.TABLE_CONSTRAINTS where CONSTRAINT_SCHEMA = ?")) {
                                p.setString(1, c.getCatalog());
                                try (ResultSet constraints = p.executeQuery()) {
                                    while (constraints.next()) required.remove(constraints.getString(1));
                                }
                            }
                            try (ResultSet version = c.getMetaData().getColumns(c.getCatalog(), null, "tasks", "version")) {
                                if (!version.next()) required.add("tasks.version");
                            }
                            if (!required.isEmpty()) throw new IllegalStateException("请先执行 backend/sql/init_db.sql，缺少基线结构: " + required);
                            flyway.baseline();
                        }
                    }
                } catch (SQLException e) {
                    throw new IllegalStateException("检查 Flyway 基线失败", e);
                }
            }
            flyway.migrate();
        };
    }
}
