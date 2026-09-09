package minisql.catalog;

import minisql.TestCatalogFactory;
import minisql.ast.DataType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Catalog（会话符号表）单元测试。
 */
class CatalogTest {

    @Test
    void registerAndQuery() {
        Catalog c = TestCatalogFactory.student();
        assertTrue(c.containsTable("student"));
        TableMeta t = c.table("student");
        assertNotNull(t);
        assertEquals(3, t.columnCount());
        assertEquals(List.of("id", "name", "age"),
                t.columns().stream().map(ColumnMeta::name).toList());
    }

    @Test
    void tableNameCaseInsensitive() {
        Catalog c = TestCatalogFactory.student();
        assertNotNull(c.table("STUDENT"));   // 建表名大小写不敏感
        assertNotNull(c.table("Student"));
    }

    @Test
    void findColumnCaseInsensitive() {
        Catalog c = TestCatalogFactory.student();
        TableMeta t = c.table("student");
        assertNotNull(t.findColumn("ID"));
        assertNotNull(t.findColumn("Name"));
        assertNull(t.findColumn("nope"));
    }

    @Test
    void columnKeepsOriginalCaseForDisplay() {
        Catalog c = TestCatalogFactory.student();
        ColumnMeta col = c.table("student").findColumn("name");
        assertNotNull(col);
        assertEquals("name", col.name());
        assertEquals(DataType.VARCHAR, col.type());
    }

    @Test
    void duplicateTableNameRegisterRejected() {
        Catalog c = TestCatalogFactory.student();
        // 同名表重复登记应失败，且不覆盖原表
        assertFalse(c.register(new TableMeta("student", List.of(
                new ColumnMeta("x", DataType.INT)))));
        assertEquals(3, c.table("student").columnCount());
        assertEquals(1, c.size());
    }
}
