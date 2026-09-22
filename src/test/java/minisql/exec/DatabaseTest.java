package minisql.exec;

import minisql.MiniSqlFrontend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库门面测试：打开/关闭、目录登记、错误封装、缓冲池暴露。
 */
class DatabaseTest {

    @TempDir
    Path dir;

    private Database db() {
        return Database.open(dir.resolve("db.db"));
    }

    private static ExecutionResult run(Database db, String sql) {
        return db.run(MiniSqlFrontend.parse(sql));
    }

    @Test
    void createRegistersTableInCatalog() {
        try (Database db = db()) {
            run(db, "CREATE TABLE student(id INT, name VARCHAR);");
            assertTrue(db.compiler().catalog().containsTable("student"));
            assertEquals(1, db.compiler().catalog().size());
        }
    }

    @Test
    void unknownTableIsError() {
        try (Database db = db()) {
            ExecutionResult r = run(db, "SELECT id FROM missing;");
            assertTrue(r.isError());
            assertNotNull(r.errorText());
        }
    }

    @Test
    void duplicateCreateIsError() {
        try (Database db = db()) {
            run(db, "CREATE TABLE t(a INT);");
            ExecutionResult r = run(db, "CREATE TABLE t(b INT);");
            assertTrue(r.isError());
        }
    }

    @Test
    void bufferPoolIsExposed() {
        try (Database db = db()) {
            assertNotNull(db.bufferPool());
        }
    }
}
