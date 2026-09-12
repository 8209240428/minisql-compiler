package minisql.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 会话级 Catalog：在内存中登记/查询表结构。
 *
 * <p>语义分析过程中，CREATE TABLE 会调用 {@link #register(TableMeta)} 把表登记进来；
 * 后续 INSERT / SELECT / DELETE 用它查表、查列。表名大小写不敏感。
 *
 * <p>Catalog 只负责“登记与查询”，不抛出带行列号的语义异常——
 * 那些判断（表已存在、列重复等）交给 semantic 包，以便报错信息带上位置。
 */
public final class Catalog {
    private final Map<String, TableMeta> tables = new LinkedHashMap<>(); // 小写表名 -> 表

    /**
     * 登记一张表。若同名表已存在返回 false（不覆盖）。
     *
     * @return true 表示登记成功，false 表示表名冲突
     */
    public boolean register(TableMeta table) {
        String key = table.name().toLowerCase(Locale.ROOT);
        if (tables.containsKey(key)) {
            return false;
        }
        tables.put(key, table);
        return true;
    }

    /** 按表名查表（大小写不敏感）；不存在返回 null */
    public TableMeta table(String tableName) {
        return tables.get(tableName.toLowerCase(Locale.ROOT));
    }

    /** 是否存在该表（大小写不敏感） */
    public boolean containsTable(String tableName) {
        return tables.containsKey(tableName.toLowerCase(Locale.ROOT));
    }

    /** 已登记的表数 */
    public int size() {
        return tables.size();
    }

    /** 已登记的表（按登记顺序，只读副本） */
    public List<TableMeta> tables() {
        return new ArrayList<>(tables.values());
    }

    @Override
    public String toString() {
        return "Catalog" + tables.values();
    }
}
