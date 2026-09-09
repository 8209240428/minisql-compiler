package minisql.ui;

import minisql.CompileResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 交互会话模型 {@link Workbench} 与文本渲染 {@link ReportText} 的单测（纯逻辑，无 Swing，可无头运行）。
 */
class WorkbenchTest {

    @Test
    void fullFlowProducesOneEntryPerStatement() {
        Workbench wb = new Workbench();
        String sql = "CREATE TABLE student(id INT, name VARCHAR, age INT, score INT);"
                + "INSERT INTO student(id, name, age, score) VALUES (1, 'Alice', 20, 90);"
                + "SELECT id, name FROM student WHERE age >= 20 AND score > 80;"
                + "DELETE FROM student WHERE id = 1;";

        Workbench.Report r = wb.run(sql);

        assertTrue(r.lexOk());
        assertEquals(4, r.entries().size());
        assertEquals(0, r.failureCount());
        for (Workbench.Entry e : r.entries()) {
            assertTrue(e.parseOk(), "语句 " + e.index() + " 应能通过语法分析");
            assertTrue(e.result().ok(), "语句 " + e.index() + " 不应有语义错误");
        }
        // CREATE 无执行计划，其余三类都有
        assertFalse(r.entries().get(0).result().hasPlan());
        assertTrue(r.entries().get(1).result().hasPlan());
        assertTrue(r.entries().get(2).result().hasPlan());
        assertTrue(r.entries().get(3).result().hasPlan());
    }

    @Test
    void syntaxErrorDoesNotStopLaterStatements() {
        Workbench wb = new Workbench();
        String sql = "CREATE TABLE student(id INT, name VARCHAR);"
                + "INSERT INTO student(id, name) VALUES (1, 'a');"
                + "SELEC id FROM student;"        // 语法错误：SELEC 不是关键字
                + "SELECT id FROM student;";

        Workbench.Report r = wb.run(sql);

        assertTrue(r.lexOk());
        assertEquals(4, r.entries().size());
        Workbench.Entry bad = r.entries().get(2);
        assertFalse(bad.parseOk());
        assertTrue(bad.parseError().contains("[语法错误]"));
        // 出错语句之后的语句仍然被编译，且能看到之前 CREATE 的表
        Workbench.Entry last = r.entries().get(3);
        assertTrue(last.parseOk());
        assertTrue(last.result().ok());
        assertTrue(last.result().hasPlan());
        assertTrue(r.failureCount() >= 1);
    }

    @Test
    void missingTrailingSemicolonIsAccepted() {
        Workbench wb = new Workbench();
        String sql = "CREATE TABLE student(id INT);" + "SELECT id FROM student"; // 末句无分号

        Workbench.Report r = wb.run(sql);

        assertTrue(r.lexOk());
        assertEquals(2, r.entries().size());
        assertTrue(r.entries().get(1).parseOk());
        assertTrue(r.entries().get(1).result().ok());
        assertTrue(r.entries().get(1).result().hasPlan());
    }

    @Test
    void lexicalErrorIsReportedWithoutPartialEntries() {
        Workbench wb = new Workbench();
        Workbench.Report r = wb.run("SELECT @ FROM t;"); // '@' 是非法字符

        assertFalse(r.lexOk());
        assertTrue(r.lexicalError().contains("[词法错误]"));
        assertTrue(r.entries().isEmpty());
        assertTrue(r.failureCount() >= 1);
    }

    @Test
    void sessionStatePersistsAcrossRunsAndResetClearsIt() {
        Workbench wb = new Workbench();
        wb.run("CREATE TABLE student(id INT, name VARCHAR);");

        Workbench.Report ok = wb.run("SELECT * FROM student;");
        assertEquals(1, ok.entries().size());
        assertTrue(ok.entries().get(0).result().ok(), "同会话内应能看到之前建的表");

        assertEquals(List.of("student"), wb.tableNames());

        wb.reset();
        Workbench.Report afterReset = wb.run("SELECT * FROM student;");
        assertEquals(1, afterReset.entries().size());
        assertTrue(afterReset.entries().get(0).parseOk());
        CompileResult failed = afterReset.entries().get(0).result();
        assertFalse(failed.ok(), "重置会话后不应再看到 student 表");
        assertTrue(failed.error().getMessage().contains("未定义的表"));
        assertTrue(wb.tableNames().isEmpty());
    }

    @Test
    void loneSemicolonsAreIgnoredAsEmptyStatements() {
        Workbench wb = new Workbench();
        Workbench.Report r = wb.run("   ; ; ;  ");

        assertTrue(r.lexOk());
        assertTrue(r.entries().isEmpty());
    }

    @Test
    void reportTextRenderersReturnSomethingForFullFlow() {
        Workbench wb = new Workbench();
        Workbench.Report r = wb.run("CREATE TABLE t(id INT);"
                + "SELECT id FROM t WHERE 1 = 1 AND id > 2 + 3;");

        assertTrue(ReportText.tokens(r).contains("Token("));
        assertTrue(ReportText.ast(r).contains("SelectStmt"));
        assertTrue(ReportText.plans(r).contains("Scan t"));
        assertTrue(ReportText.summary(r, wb.tableNames()).contains("会话已建表"));
    }
}
