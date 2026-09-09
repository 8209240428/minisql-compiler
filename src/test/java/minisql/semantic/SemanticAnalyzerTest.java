package minisql.semantic;

import minisql.MiniSqlFrontend;
import minisql.TestCatalogFactory;
import minisql.catalog.Catalog;
import minisql.catalog.ColumnMeta;
import minisql.expr.Expr;
import minisql.expr.ExprPrinter;
import minisql.expr.ExprType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语义分析（名字绑定 + 类型检查）单元测试：正例与各类语义错误。
 */
class SemanticAnalyzerTest {

    /** 在给定 Catalog 上分析一条 SQL */
    private Analyzed analyze(Catalog catalog, String sql) {
        return new SemanticAnalyzer(catalog).analyze(MiniSqlFrontend.parse(sql));
    }

    // ------------------------------------------------------------------
    // 合法语句
    // ------------------------------------------------------------------

    @Test
    void selectStarExpandsAllColumns() {
        AnalyzedSelect r = (AnalyzedSelect) analyze(TestCatalogFactory.student(),
                "SELECT * FROM student;");
        assertEquals(TestCatalogFactory.studentColumns(),
                r.outputColumns().stream().map(ColumnMeta::name).toList());
        assertNull(r.where());
    }

    @Test
    void selectSpecificColumnsBindsToTableColumns() {
        AnalyzedSelect r = (AnalyzedSelect) analyze(TestCatalogFactory.student(),
                "SELECT id, name FROM student WHERE age > 18 AND name = 'Alice';");
        assertEquals(List.of("id", "name"),
                r.outputColumns().stream().map(ColumnMeta::name).toList());
        assertNotNull(r.where());
        assertTrue(r.where().kind().isBoolean());
    }

    @Test
    void arithmeticInComparisonPasses() {
        AnalyzedSelect r = (AnalyzedSelect) analyze(TestCatalogFactory.student(),
                "SELECT name FROM student WHERE age > 10 + 8;");
        Expr cmp = r.where();
        assertInstanceOf(Expr.Cmp.class, cmp);
        assertEquals("(age > (10 + 8))", ExprPrinter.render(cmp));
    }

    @Test
    void insertCoversAllColumnsPasses() {
        AnalyzedInsert r = (AnalyzedInsert) analyze(TestCatalogFactory.student(),
                "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);");
        assertEquals(3, r.size());
        assertEquals(ExprType.INT, r.values().get(0).kind()); // 第一个值 1 是 INT
        assertEquals(ExprType.VARCHAR, r.values().get(1).kind()); // 'Alice' 是 VARCHAR
    }

    @Test
    void deletePasses() {
        AnalyzedDelete r = (AnalyzedDelete) analyze(TestCatalogFactory.student(),
                "DELETE FROM student WHERE id = 1;");
        assertNotNull(r.where());
    }

    @Test
    void createRegistersIntoCatalog() {
        Catalog c = new Catalog();
        Analyzed r = analyze(c, "CREATE TABLE t(id INT, name VARCHAR);");
        assertInstanceOf(AnalyzedCreate.class, r);
        assertTrue(c.containsTable("t"));
        assertEquals(2, c.table("t").columnCount());
    }

    // ------------------------------------------------------------------
    // 语义错误
    // ------------------------------------------------------------------

    @Test
    void selectUnknownTable() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(), "SELECT * FROM ghost;"));
        assertMessage(ex, "未定义的表");
        assertTrue(ex.getMessage().startsWith("[语义错误]"));
    }

    @Test
    void selectUnknownColumnInProjection() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(), "SELECT nope FROM student;"));
        assertMessage(ex, "未定义的列 'nope'");
    }

    @Test
    void selectUnknownColumnInWhere() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "SELECT * FROM student WHERE no_such = 1;"));
        assertMessage(ex, "未定义的列");
    }

    @Test
    void stringColumnCannotDoOrderComparison() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "SELECT * FROM student WHERE name > 'a';"));
        assertMessage(ex, "大小比较");
    }

    @Test
    void intVsStringTypeMismatch() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "SELECT * FROM student WHERE age >= '18';"));
        assertMessage(ex, "类型不匹配");
    }

    @Test
    void whereMustBeBoolean() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(), "SELECT * FROM student WHERE age;"));
        assertMessage(ex, "必须是布尔表达式");
    }

    @Test
    void andSidesMustBeBoolean() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "SELECT * FROM student WHERE age AND 1 = 1;"));
        assertMessage(ex, "布尔表达式");
    }

    @Test
    void notOnlyOnBoolean() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(), "SELECT * FROM student WHERE NOT age;"));
        assertMessage(ex, "布尔表达式");
    }

    @Test
    void arithmeticOnlyOnInt() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "SELECT * FROM student WHERE 1 + name = 2;"));
        assertMessage(ex, "只支持 INT");
    }

    @Test
    void insertIntoUnknownTable() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "INSERT INTO ghost(id) VALUES (1);"));
        assertMessage(ex, "未定义的表");
    }

    @Test
    void insertUnknownColumn() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "INSERT INTO student(id, nope, age) VALUES (1, 'x', 20);"));
        assertMessage(ex, "未定义的列");
    }

    @Test
    void insertColumnsMustCoverTable() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "INSERT INTO student(id, age) VALUES (1, 20);"));
        assertMessage(ex, "列数量与表");
    }

    @Test
    void insertValueCountMismatch() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20, 99);"));
        assertMessage(ex, "值的个数");
    }

    @Test
    void insertValueTypeMismatch() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 'x');"));
        assertMessage(ex, "类型不匹配");
    }

    @Test
    void insertValueCannotReferenceColumn() {
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(TestCatalogFactory.student(),
                        "INSERT INTO student(id, name, age) VALUES (id, 'Alice', 20);"));
        assertMessage(ex, "不允许出现列引用");
    }

    @Test
    void createDuplicateColumn() {
        Catalog c = new Catalog();
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(c, "CREATE TABLE t(a INT, b INT, a VARCHAR);"));
        assertMessage(ex, "重复定义列");
        assertFalse(c.containsTable("t")); // 出错时不应残留半成品表
    }

    @Test
    void createDuplicateTable() {
        Catalog c = TestCatalogFactory.student();
        SemanticException ex = assertThrows(SemanticException.class,
                () -> analyze(c, "CREATE TABLE student(x INT);"));
        assertMessage(ex, "已存在");
    }

    private void assertMessage(SemanticException ex, String fragment) {
        assertTrue(ex.getMessage().contains(fragment),
                "错误消息应包含「" + fragment + "」，实际: " + ex.getMessage());
        assertTrue(ex.getLine() > 0);
        assertTrue(ex.getCol() > 0);
    }
}
