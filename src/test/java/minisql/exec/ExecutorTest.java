package minisql.exec;

import minisql.MiniSqlFrontend;
import minisql.catalog.ColumnMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 执行引擎测试：SeqScan / Filter / Project / Insert / Delete 五个算子真实执行。
 */
class ExecutorTest {

    @TempDir
    Path dir;

    private Database db() {
        return Database.open(dir.resolve("exec.db"));
    }

    private static ExecutionResult run(Database db, String sql) {
        return db.run(MiniSqlFrontend.parse(sql));
    }

    @Test
    void insertThenSelectAll() {
        try (Database db = db()) {
            assertTrue(run(db, "CREATE TABLE student(id INT, name VARCHAR, age INT);").isMessage());
            assertEquals(1, run(db, "INSERT INTO student(id,name,age) VALUES (1,'Alice',20);").affected());
            assertEquals(1, run(db, "INSERT INTO student(id,name,age) VALUES (2,'Bob',17);").affected());

            ExecutionResult r = run(db, "SELECT id,name,age FROM student;");
            assertTrue(r.isResultSet());
            assertEquals(List.of("id", "name", "age"),
                    r.columns().stream().map(ColumnMeta::name).toList());
            assertEquals(2, r.rows().size());
            assertEquals(1L, r.rows().get(0).cell(0));
            assertEquals("Alice", r.rows().get(0).cell(1));
            assertEquals(17L, r.rows().get(1).cell(2));
        }
    }

    @Test
    void selectWithFilter() {
        try (Database db = db()) {
            run(db, "CREATE TABLE student(id INT, name VARCHAR, age INT);");
            run(db, "INSERT INTO student(id,name,age) VALUES (1,'Alice',20);");
            run(db, "INSERT INTO student(id,name,age) VALUES (2,'Bob',17);");
            ExecutionResult r = run(db, "SELECT id FROM student WHERE age >= 18;");
            assertEquals(1, r.rows().size());
            assertEquals(1L, r.rows().get(0).cell(0));
        }
    }

    @Test
    void filterWithArithmetic() {
        try (Database db = db()) {
            run(db, "CREATE TABLE t(a INT);");
            run(db, "INSERT INTO t(a) VALUES (5);");
            run(db, "INSERT INTO t(a) VALUES (9);");
            ExecutionResult r = run(db, "SELECT a FROM t WHERE a + 1 >= 7;");
            assertEquals(1, r.rows().size());
            assertEquals(9L, r.rows().get(0).cell(0));
        }
    }

    @Test
    void insertReordersColumnsToTableOrder() {
        try (Database db = db()) {
            run(db, "CREATE TABLE t(a INT, b VARCHAR);");
            run(db, "INSERT INTO t(b,a) VALUES ('x',5);");
            ExecutionResult r = run(db, "SELECT a,b FROM t;");
            assertEquals(List.of(5L, "x"), r.rows().get(0).cells());
        }
    }

    @Test
    void deleteFilteredRows() {
        try (Database db = db()) {
            run(db, "CREATE TABLE student(id INT, name VARCHAR);");
            run(db, "INSERT INTO student(id,name) VALUES (1,'A');");
            run(db, "INSERT INTO student(id,name) VALUES (2,'B');");
            assertEquals(1, run(db, "DELETE FROM student WHERE id = 2;").affected());
            ExecutionResult r = run(db, "SELECT id FROM student;");
            assertEquals(1, r.rows().size());
            assertEquals(1L, r.rows().get(0).cell(0));
        }
    }
}
