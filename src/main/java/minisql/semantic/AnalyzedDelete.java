package minisql.semantic;

import minisql.catalog.TableMeta;
import minisql.expr.Expr;

/**
 * DELETE 的语义结果。{@code where} 无 WHERE 时为 null。
 */
public record AnalyzedDelete(TableMeta table, Expr where) implements Analyzed {
}
