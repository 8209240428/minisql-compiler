package minisql.ast;

/** 一元表达式：NOT expr、一元负号 */
public record UnaryExpr(
        UnaryOp op,
        Expression operand,
        int line,
        int col
) implements Expression {
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitUnaryExpr(this);
    }
}
