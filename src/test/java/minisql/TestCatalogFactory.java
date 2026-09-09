package minisql;

import minisql.ast.DataType;
import minisql.catalog.Catalog;
import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;

import java.util.List;

/**
 * 测试公共工具：快速构造带常用演示表的 Catalog，避免每个测试重复造表。
 */
public final class TestCatalogFactory {

    private TestCatalogFactory() {
    }

    /**
     * 一个空 Catalog 里登记一张学生表：
     * student(id INT, name VARCHAR, age INT)
     */
    public static Catalog student() {
        Catalog c = new Catalog();
        registerStudent(c);
        return c;
    }

    /** 若尚未存在，把 student 表登记进给定 Catalog（可复用、幂等） */
    public static void registerStudent(Catalog catalog) {
        if (!catalog.containsTable("student")) {
            catalog.register(new TableMeta("student", List.of(
                    new ColumnMeta("id", DataType.INT),
                    new ColumnMeta("name", DataType.VARCHAR),
                    new ColumnMeta("age", DataType.INT))));
        }
    }

    /** 学生表的期望列名（与 registerStudent 的定义顺序一致） */
    public static List<String> studentColumns() {
        return List.of("id", "name", "age");
    }
}
