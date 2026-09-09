package minisql.ast;

/**
 * 常量。kind 区分数字与字符串，供 C 做类型检查。
 */
public record LiteralExpr(
        String raw,
        Kind kind,
        int line,
        int col
) implements Expression {
    public enum Kind {
        NUMBER,
        STRING
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitLiteralExpr(this);
    }
}
