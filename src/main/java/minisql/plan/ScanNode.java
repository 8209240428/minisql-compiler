package minisql.plan;

import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;
import minisql.expr.Expr;

import java.util.List;

/**
 * 扫描算子：读取某张表的若干列，并可携带过滤条件。
 *
 * <p>{@code columns} 是本次扫描实际需要读取的列（优化前=全列；优化后=被投影/谓词引用到的列，见“投影裁剪”）。
 * {@code filter} 为可选的扫描内过滤条件（无过滤时为 null）。
 */
public record ScanNode(
        TableMeta table,
        List<ColumnMeta> columns,
        Expr filter
) implements PlanNode {
    public ScanNode {
        columns = List.copyOf(columns);
    }

    /** 追加（合并）过滤条件：把上层 Filter 下压进来（R4 谓词下推） */
    public ScanNode withFilter(Expr extra) {
        Expr merged = (filter == null) ? extra : new Expr.And(filter, extra);
        return new ScanNode(table, columns, merged);
    }

    /** 替换需读取的列集合（R5 投影裁剪） */
    public ScanNode withColumns(List<ColumnMeta> newColumns) {
        return new ScanNode(table, newColumns, filter);
    }
}
