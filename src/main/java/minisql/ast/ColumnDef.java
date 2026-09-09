package minisql.ast;

/**
 * CREATE TABLE 中的列定义：column_def -> IDENTIFIER type
 */
public record ColumnDef(String name, DataType type, int line, int col) {
}
