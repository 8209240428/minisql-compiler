package minisql.ast;

/** 二元表达式：比较、AND/OR、算术 */
public record BinaryExpr(
        Expression left,
        BinaryOp op,
        Expression right,
        int line,
        int col
) implements Expression {
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitBinaryExpr(this);
    }
}
