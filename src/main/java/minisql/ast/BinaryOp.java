package minisql.ast;

import minisql.lexer.TokenType;

/**
 * 二元运算符。优先级由 Parser 的分层递归下降保证，不在枚举里比较。
 */
public enum BinaryOp {
    OR("OR"),
    AND("AND"),
    EQ("="),
    NEQ("<>"),
    GT(">"),
    LT("<"),
    GTE(">="),
    LTE("<="),
    PLUS("+"),
    MINUS("-"),
    MUL("*"),
    DIV("/");

    private final String symbol;

    BinaryOp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public static BinaryOp from(TokenType type) {
        return switch (type) {
            case OR -> OR;
            case AND -> AND;
            case EQ -> EQ;
            case NEQ -> NEQ;
            case GT -> GT;
            case LT -> LT;
            case GTE -> GTE;
            case LTE -> LTE;
            case PLUS -> PLUS;
            case MINUS -> MINUS;
            case STAR -> MUL;
            case SLASH -> DIV;
            default -> throw new IllegalArgumentException("不是二元运算符: " + type);
        };
    }
}
