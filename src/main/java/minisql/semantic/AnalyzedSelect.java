package minisql.semantic;

import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;
import minisql.expr.Expr;

import java.util.List;

/**
 * SELECT 的语义结果。
 *
 * <p>{@code outputColumns}：输出列。若 SQL 写的是 {@code SELECT *}，此处已展开为表的全部列；
 * {@code where}：WHERE 条件（已翻译成 C 侧 IR 且保证为 BOOLEAN），无 WHERE 时为 null。
 */
public record AnalyzedSelect(
        TableMeta table,
        List<ColumnMeta> outputColumns,
        Expr where
) implements Analyzed {
    public AnalyzedSelect {
        outputColumns = List.copyOf(outputColumns);
    }
}
