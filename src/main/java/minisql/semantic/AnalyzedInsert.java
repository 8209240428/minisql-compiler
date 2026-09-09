package minisql.semantic;

import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;
import minisql.expr.Expr;

import java.util.List;

/**
 * INSERT 的语义结果。
 *
 * <p>{@code columns} 与 {@code values} 按下标一一对应（均为解析时给出的顺序）；
 * values 已是类型化 IR 常量（不含列引用，且类型与对应列一致）。
 */
public record AnalyzedInsert(
        TableMeta table,
        List<ColumnMeta> columns,
        List<Expr> values
) implements Analyzed {
    public AnalyzedInsert {
        columns = List.copyOf(columns);
        values = List.copyOf(values);
    }

    /** 待插入条数（= 列数） */
    public int size() {
        return columns.size();
    }
}
