package minisql.exec;

import minisql.MiniSqlFrontend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 持久化与恢复测试：写入 → 关闭 → 重开同一文件，数据与删除结果仍在。
 */
class PersistenceTest {

    @TempDir
    Path dir;

    private static ExecutionResult run(Database db, String sql) {
        return db.run(MiniSqlFrontend.parse(sql));
    }

    @Test
    void dataSurvivesReopen() {
        Path f = dir.resolve("p.db");
        try (Database db = Database.open(f)) {
            run(db, "CREATE TABLE student(id INT, name VARCHAR, age INT);");
            run(db, "INSERT INTO student(id,name,age) VALUES (1,'Alice',20);");
            run(db, "INSERT INTO student(id,name,age) VALUES (2,'Bob',17);");
        }
        // 重开同一文件：目录与数据都应恢复
        try (Database db = Database.open(f)) {
            ExecutionResult r = run(db, "SELECT id,name FROM student WHERE age >= 18;");
            assertEquals(1, r.rows().size());
            assertEquals("Alice", r.rows().get(0).cell(1));
        }
    }

    @Test
    void deleteResultSurvivesReopen() {
        Path f = dir.resolve("p2.db");
        try (Database db = Database.open(f)) {
            run(db, "CREATE TABLE t(a INT);");
            run(db, "INSERT INTO t(a) VALUES (1);");
            run(db, "INSERT INTO t(a) VALUES (2);");
            run(db, "DELETE FROM t WHERE a = 1;");
        }
        try (Database db = Database.open(f)) {
            ExecutionResult r = run(db, "SELECT a FROM t;");
            assertEquals(1, r.rows().size());
            assertEquals(2L, r.rows().get(0).cell(0));
        }
    }
}
