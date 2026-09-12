package minisql.catalog;

import minisql.ast.DataType;

/**
 * 表列元信息：列名 + 数据类型。
 *
 * <p>列类型直接复用 B 的 {@link minisql.ast.DataType}（INT / VARCHAR），
 * C 不重复定义类型枚举，避免与 B 的 AST 语义冲突。
 */
public record ColumnMeta(String name, DataType type) {

    /** 该列是否为数值列（INT） */
    public boolean isNumeric() {
        return type == DataType.INT;
    }

    /** 该列是否为字符串列（VARCHAR） */
    public boolean isString() {
        return type == DataType.VARCHAR;
    }
}
