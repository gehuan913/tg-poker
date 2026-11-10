package com.tgpoker.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;

/**
 * 轻量结构升级器。
 *
 * <p>项目没有引入 Flyway/Liquibase（单人开发，不想为一个库再背一套元数据表），
 * 但建表脚本用的是 {@code CREATE TABLE IF NOT EXISTS}：<b>表已存在时不会补新列</b>，
 * 于是"代码新了、库没新"就会在登录这种必经路径上直接炸。</p>
 *
 * <p>所以对"只加列"的变更，在这里做幂等检查 + ALTER：先查 JDBC 元数据，
 * 没有才执行。生产建议换成正式迁移工具，这里保证本地与演示环境不会卡住。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaUpgrader implements ApplicationRunner {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    /** 需要保证存在的列：表名、列名、补列 DDL。 */
    private record ColumnPatch(String table, String column, String ddl) {
    }

    private static final List<ColumnPatch> PATCHES = List.of(
            new ColumnPatch("app_user", "insurance_opt_in",
                    "ALTER TABLE app_user ADD COLUMN insurance_opt_in TINYINT DEFAULT 1 "
                            + "COMMENT '是否开启保险：1=开，0=关'")
    );

    @Override
    public void run(ApplicationArguments args) {
        for (ColumnPatch p : PATCHES) {
            try {
                if (!hasColumn(p.table(), p.column())) {
                    jdbc.execute(p.ddl());
                    log.warn("结构升级：{}.{} 不存在，已自动补列", p.table(), p.column());
                }
            } catch (Exception e) {
                log.error("结构升级失败 {}.{}：{}", p.table(), p.column(), e.getMessage());
            }
        }
    }

    /** 用 JDBC 元数据判断列是否存在（大小写不敏感，兼容 H2/MySQL）。 */
    private boolean hasColumn(String table, String column) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            String catalog = c.getCatalog();
            for (String t : new String[]{table, table.toUpperCase()}) {
                try (ResultSet rs = md.getColumns(catalog, null, t, null)) {
                    while (rs.next()) {
                        String name = rs.getString("COLUMN_NAME");
                        if (name != null && name.equalsIgnoreCase(column)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("读取表结构失败 {}.{}：{}", table, column, e.getMessage());
            return true; // 读不到就当存在，避免反复 ALTER 报错
        }
    }
}
