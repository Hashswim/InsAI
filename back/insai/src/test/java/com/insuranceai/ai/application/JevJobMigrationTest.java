package com.insuranceai.ai.application;

import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JevJobMigrationTest {
    @Test void migrationCreatesQueueAndConstraints() throws Exception {
        try (var connection=DriverManager.getConnection("jdbc:h2:mem:jobmigration;MODE=PostgreSQL","sa","");
             var sql=connection.createStatement()) {
            sql.execute("create table cases(id uuid primary key)");
            sql.execute("create table accounts(id uuid primary key)");
            sql.execute("create table jev_analyses(id uuid primary key, case_id uuid, created_at timestamp with time zone)");
            sql.execute("create table jev_access_audits(id uuid primary key)");
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V8__jev_jobs.sql")).split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
            try (var result=sql.executeQuery("select count(*) from jev_jobs where status='QUEUED'")) {
                assertTrue(result.next()); assertEquals(0,result.getInt(1));
            }
            assertThrows(java.sql.SQLException.class,() -> sql.execute("insert into jev_jobs(id) values(random_uuid())"));
            sql.execute("insert into jev_analyses(id) values(random_uuid())");
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V9__jev_result_execution_audit.sql")).split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
            try (var result=sql.executeQuery("select request_id, chain_version from jev_analyses")) {
                assertTrue(result.next()); assertNull(result.getObject(1)); assertNull(result.getObject(2));
            }
            try (var result=sql.executeQuery("select count(*) from jev_execution_events")) {
                assertTrue(result.next()); assertEquals(0,result.getInt(1));
            }
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V10__jev_metrics.sql")).split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
            try (var result=sql.executeQuery("select input_tokens, output_tokens from jev_analyses")) {
                assertTrue(result.next()); assertNull(result.getObject(1)); assertNull(result.getObject(2));
            }
            assertThrows(java.sql.SQLException.class,() -> sql.execute("update jev_analyses set input_tokens=-1"));
        }
    }
}
