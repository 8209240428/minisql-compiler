package minisql.catalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 表结构元信息：表名 + 有序列集合。
 *
 * <p>语义分析（列名绑定）、执行计划（Scan 需读取哪些列）都依赖它。
 * 列查找按<b>大小写不敏感</b>处理（与多数 MiniSQL 行为一致），但保留原名字用于展示。
 */
public final class TableMeta {
    private final String name;
    private final List<ColumnMeta> columns;          // 保持定义顺序
    private final Map<String, ColumnMeta> byLower;   // 小写名 -> 列元信息

    /**
     * @param name    表名（原始大小写）
     * @param columns 有序列定义
     */
    public TableMeta(String name, List<ColumnMeta> columns) {
        this.name = name;
        this.columns = List.copyOf(columns);
        Map<String, ColumnMeta> index = new LinkedHashMap<>();
        for (ColumnMeta col : this.columns) {
            // 若上层已做重复列检查，这里 putIfAbsent 只为兜底，不抛错
            index.putIfAbsent(col.name().toLowerCase(Locale.ROOT), col);
        }
        this.byLower = Map.copyOf(index);
    }

    public String name() {
        return name;
    }

    /** 全部列（只读，保持建表顺序） */
    public List<ColumnMeta> columns() {
        return columns;
    }

    /** 按名字查列（大小写不敏感）；不存在返回 null */
    public ColumnMeta findColumn(String columnName) {
        return byLower.get(columnName.toLowerCase(Locale.ROOT));
    }

    /** 是否存在该列（大小写不敏感） */
    public boolean hasColumn(String columnName) {
        return byLower.containsKey(columnName.toLowerCase(Locale.ROOT));
    }

    /** 列数 */
    public int columnCount() {
        return columns.size();
    }

    /** 按给定名字集合，返回「表定义顺序」中保留下来的列（供投影裁剪 / 星号展开用） */
    public List<ColumnMeta> keepInOrder(java.util.Set<ColumnMeta> needed) {
        List<ColumnMeta> result = new ArrayList<>();
        for (ColumnMeta col : columns) {
            if (needed.contains(col)) {
                result.add(col);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "Table(" + name + ", cols=" + columns + ")";
    }
}
