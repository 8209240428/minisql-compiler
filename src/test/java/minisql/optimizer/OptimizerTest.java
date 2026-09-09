package minisql.optimizer;

import minisql.CompileResult;
import minisql.MiniSqlCompiler;
import minisql.MiniSqlFrontend;
import minisql.TestCatalogFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 5 条优化规则的端到端断言（演示口径：{@code WHERE 1=1 AND age>10+8} → 仅保留 {@code age>18}）。
 * 各用例都走完整管线（语义 → 计划 → 优化），再对优化前后计划文本做断言。
 */
class OptimizerTest {

    /** 在一个预置了 student 表的编译器上编译单条语句 */
    private CompileResult compile(String sql) {
        MiniSqlCompiler cc = new MiniSqlCompiler();
        TestCatalogFactory.registerStudent(cc.catalog());
        return cc.compile(MiniSqlFrontend.parse(sql));
    }

    // ------------------------------------------------------------------
    // R1 常量折叠 + R2 布尔化简 + R4 谓词下推 + R5 投影裁剪（综合）
    // ------------------------------------------------------------------

    @Test
    void foldAndSimplifyAndPushdownAndPrune() {
        CompileResult r = compile("SELECT id FROM student WHERE 1 = 1 AND age > 10 + 8;");
        assertTrue(r.ok());
        assertTrue(r.hasPlan());

        String before = r.before().print();
        String after = r.after().print();

        // 优化前：还有独立的 Filter，且能看到未折叠的 10+8
        assertTrue(before.contains("Filter"));
        assertTrue(before.contains("10 + 8"));

        // 优化后：Filter 消失、算术已折叠、谓词下压进 Scan、列被裁剪到 {id, age}
        assertFalse(after.contains("Filter"), "Filter 应被谓词下推消除:\n" + after);
        assertFalse(after.contains("10 + 8"), "10+8 应被常量折叠:\n" + after);
        assertTrue(after.contains("filter: (age > 18)"), "应只剩 age>18 谓词:\n" + after);
        assertTrue(after.contains("cols=[id, age]"), "Scan 应只读 id 与过滤用的 age:\n" + after);
        assertTrue(r.steps().size() >= 3, "应有逐步优化快照");
    }

    // ------------------------------------------------------------------
    // R2 恒假 / 恒真
    // ------------------------------------------------------------------

    @Test
    void whereAlwaysFalseKeepsFalseness() {
        CompileResult r = compile("SELECT * FROM student WHERE 1 = 0 AND age > 18;");
        assertTrue(r.ok());
        assertTrue(r.after().print().contains("filter: FALSE"), r.after().print());
    }

    @Test
    void whereAlwaysTrueDropsFilter() {
        CompileResult r = compile("SELECT id FROM student WHERE 1 = 1;");
        assertTrue(r.ok());
        String after = r.after().print();
        assertFalse(after.contains("Filter"), after);
        assertFalse(after.contains("filter:"), "恒真条件不应残留过滤:\n" + after);
        assertTrue(after.contains("cols=[id]"), after);
    }

    // ------------------------------------------------------------------
    // R3 冗余谓词消除
    // ------------------------------------------------------------------

    @Test
    void duplicatePredicateRemoved() {
        CompileResult r = compile("SELECT id FROM student WHERE age > 20 AND age > 20;");
        assertTrue(r.ok());
        String after = r.after().print();
        assertFalse(after.contains("AND"), "重复谓词应被去重:\n" + after);
        assertTrue(after.contains("filter: (age > 20)"), after);
    }

    // ------------------------------------------------------------------
    // DELETE 也走同一套过滤/裁剪
    // ------------------------------------------------------------------

    @Test
    void deleteScanReadsOnlyFilterColumns() {
        CompileResult r = compile("DELETE FROM student WHERE age > 18;");
        assertTrue(r.ok());
        String after = r.after().print();
        assertTrue(after.startsWith("Delete from student"), after);
        assertTrue(after.contains("filter: (age > 18)"), after);
        assertTrue(after.contains("cols=[age]"), "DELETE 扫描只需读出 age 供过滤:\n" + after);
        assertFalse(after.contains("Filter"), after);
    }

    // ------------------------------------------------------------------
    // INSERT 的计划（值常量折叠）
    // ------------------------------------------------------------------

    @Test
    void insertPlanUnchanged() {
        CompileResult r = compile("INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        assertTrue(r.ok());
        assertTrue(r.hasPlan());
        assertTrue(r.before().print().startsWith("Insert into student values 1, 'Alice', 20"),
                r.before().print());
    }
}
