package minisql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MiniSqlCompiler（门面）端到端测试：脚本顺序执行 + 语义错误封装。
 */
class MiniSqlCompilerTest {

    @Test
    void compileAllScriptSequentially() {
        MiniSqlCompiler compiler = new MiniSqlCompiler();
        List<CompileResult> results = compiler.compileAll(
                "CREATE TABLE t(id INT, age INT);"
                        + "INSERT INTO t(id, age) VALUES (1, 20);"
                        + "SELECT id FROM t WHERE age > 18;");

        assertEquals(3, results.size());

        // 建表：语义通过，无逻辑计划
        CompileResult create = results.get(0);
        assertTrue(create.ok());
        assertFalse(create.hasPlan());
        assertTrue(compiler.catalog().containsTable("t"));

        // 插入：有计划
        CompileResult insert = results.get(1);
        assertTrue(insert.ok());
        assertTrue(insert.hasPlan());
        assertTrue(insert.before().print().startsWith("Insert into t"));

        // 查询：优化后 Scan 应只读投影+过滤所需列（id 用于输出，age 用于过滤）
        CompileResult select = results.get(2);
        assertTrue(select.ok());
        String after = select.after().print();
        assertTrue(after.contains("cols=[id, age]"), after);
    }

    @Test
    void createCanSeeTablesFromEarlierStatements() {
        MiniSqlCompiler compiler = new MiniSqlCompiler();
        compiler.compileAll("CREATE TABLE a(x INT);");
        // 第二条语句里若再建同名表，应报“已存在”而不是异常抛出
        CompileResult dup = compiler.compile(MiniSqlFrontend.parse("CREATE TABLE A(y INT);"));
        assertFalse(dup.ok());
        assertTrue(dup.error().getMessage().contains("已存在"));
    }

    @Test
    void semanticErrorIsCapturedNotThrown() {
        MiniSqlCompiler compiler = new MiniSqlCompiler();
        TestCatalogFactory.registerStudent(compiler.catalog());
        CompileResult r = compiler.compile(MiniSqlFrontend.parse("SELECT ghost FROM student;"));
        assertFalse(r.ok());
        assertNull(r.analyzed());
        assertTrue(r.error().getMessage().contains("未定义的列"));
    }

    @Test
    void okSelectReturnsPlanAndSteps() {
        MiniSqlCompiler compiler = new MiniSqlCompiler();
        TestCatalogFactory.registerStudent(compiler.catalog());
        CompileResult r = compiler.compile(MiniSqlFrontend.parse(
                "SELECT name FROM student WHERE 1 = 1 AND age > 5;"));
        assertTrue(r.ok());
        assertTrue(r.hasPlan());
        assertNotNull(r.before());
        assertNotNull(r.after());
        assertFalse(r.stepNames().isEmpty());
    }
}
