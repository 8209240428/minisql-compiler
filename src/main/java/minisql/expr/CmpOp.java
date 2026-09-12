package minisql.expr;

/**
 * 比较运算符（C 侧 IR 使用）。
 *
 * <p>与 B 的 {@code ast.BinaryOp} 解耦，避免 expr 包反向依赖 AST；
 * 语义分析负责把 B 的比较运算符映射到这里。
 */
public enum CmpOp {
    EQ("="),
    NEQ("<>"),
    GT(">"),
    LT("<"),
    GTE(">="),
    LTE("<=");

    private final String symbol;

    CmpOp(String symbol) {
        this.symbol = symbol;
    }

    /** 用于打印（计划树 / 测试断言） */
    public String symbol() {
        return symbol;
    }

    /** 是否为大小比较（字符串列不允许做大小比较，只允许 = / <>） */
    public boolean isOrder() {
        return this == GT || this == LT || this == GTE || this == LTE;
    }
}
