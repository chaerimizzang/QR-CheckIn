package com.qrcheckin.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 출결 상태 열을 ENUM 에서 VARCHAR 로 바꾼다(한 번만, 이미 바뀌었으면 아무것도 안 함).
 *
 * 예전 버전의 Hibernate 가 MySQL·H2 에 상태 열을 ENUM('PRESENT','LATE','ABSENT') 로 만들어서,
 * 새 상태(EXCUSED = 공결)를 저장하면 오류가 난다. ddl-auto=update 는 열 종류를 바꿔 주지 않으므로 직접 바꾼다.
 */
@Component
@Order(0)
public class StatusColumnMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StatusColumnMigration.class);

    /** {테이블, 열, NOT NULL 여부} */
    private static final String[][] COLUMNS = {
            {"attendance", "status", "true"},
            {"attendance_history", "old_status", "false"},
            {"attendance_history", "new_status", "true"},
    };

    private final DataSource dataSource;

    public StatusColumnMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            boolean mysql = md.getDatabaseProductName().toLowerCase().contains("mysql");
            for (String[] col : COLUMNS) {
                String type = columnType(md, c.getCatalog(), col[0], col[1]);
                if (type == null || !type.toUpperCase().contains("ENUM")) {
                    continue;
                }
                String notNull = Boolean.parseBoolean(col[2]) ? " NOT NULL" : "";
                String sql = mysql
                        ? "ALTER TABLE " + col[0] + " MODIFY COLUMN " + col[1] + " VARCHAR(20)" + notNull
                        : "ALTER TABLE " + col[0] + " ALTER COLUMN " + col[1] + " SET DATA TYPE VARCHAR(20)";
                try (Statement st = c.createStatement()) {
                    st.execute(sql);
                    log.info("출결 상태 열 변경: {}.{} {} → VARCHAR(20)", col[0], col[1], type);
                }
            }
        } catch (Exception e) {
            log.warn("출결 상태 열 확인 실패(공결 저장이 안 될 수 있음): {}", e.toString());
        }
    }

    /** 테이블·열 이름의 대소문자는 DB 마다 달라서 둘 다 찾아본다 */
    private static String columnType(DatabaseMetaData md, String catalog, String table, String column) throws Exception {
        for (String t : new String[] {table, table.toUpperCase()}) {
            for (String col : new String[] {column, column.toUpperCase()}) {
                try (ResultSet rs = md.getColumns(catalog, null, t, col)) {
                    if (rs.next()) {
                        return rs.getString("TYPE_NAME");
                    }
                }
            }
        }
        return null;
    }
}
