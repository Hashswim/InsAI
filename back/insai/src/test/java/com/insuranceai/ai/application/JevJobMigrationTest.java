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
            sql.execute("create table jev_analyses(id uuid primary key)");
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V8__jev_jobs.sql")).split(";")) {
                if (!statement.isBlank()) sql.execute(statement);
            }
            try (var result=sql.executeQuery("select count(*) from jev_jobs where status='QUEUED'")) {
                assertTrue(result.next()); assertEquals(0,result.getInt(1));
            }
            assertThrows(java.sql.SQLException.class,() -> sql.execute("insert into jev_jobs(id) values(random_uuid())"));
        }
    }
}
