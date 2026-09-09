package minisql.ast;

/** 列名 / 标识符 */
public record IdentifierExpr(String name, int line, int col) implements Expression {
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitIdentifierExpr(this);
    }
}
