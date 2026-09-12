package minisql.expr;

import minisql.ast.DataType;

/**
 * C 侧表达式的“种类/类型”。在 INT / VARCHAR（列与常量）之外，额外引入
 * BOOLEAN（比较结果、AND/OR/NOT 的中间结果），用于语义阶段约束 WHERE 等布尔上下文。
 *
 * <p>列、常量的 INT/VARCHAR 与 B 的 {@link DataType} 一一对应，见 {@link #fromAst(DataType)}。
 */
public enum ExprType {
    INT,
    VARCHAR,
    BOOLEAN;

    /** 是否数值（INT） */
    public boolean isNumeric() {
        return this == INT;
    }

    /** 是否字符串（VARCHAR） */
    public boolean isString() {
        return this == VARCHAR;
    }

    /** 是否布尔 */
    public boolean isBoolean() {
        return this == BOOLEAN;
    }

    /** 由 B 的列类型枚举映射到 C 的类型 */
    public static ExprType fromAst(DataType dataType) {
        return dataType == DataType.INT ? INT : VARCHAR;
    }
}
