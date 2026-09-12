package minisql.plan;

import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;
import minisql.expr.Expr;

import java.util.List;

/**
 * 插入算子（叶子）：向目标表按顺序写入 values。
 * columns 与 values 下标一一对应，且已通过语义阶段类型检查。
 */
public record InsertNode(
        TableMeta table,
        List<ColumnMeta> columns,
        List<Expr> values
) implements PlanNode {
    public InsertNode {
        columns = List.copyOf(columns);
        values = List.copyOf(values);
    }
}
