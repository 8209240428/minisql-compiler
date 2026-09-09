package minisql.expr;

/**
 * 算术运算符（C 侧 IR 使用）。只允许作用在 INT 上。
 */
public enum ArithOp {
    PLUS("+"),
    MINUS("-"),
    MUL("*"),
    DIV("/");

    private final String symbol;

    ArithOp(String symbol) {
        this.symbol = symbol;
    }

    /** 用于打印（计划树 / 测试断言） */
    public String symbol() {
        return symbol;
    }
}
