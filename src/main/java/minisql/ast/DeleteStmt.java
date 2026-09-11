package minisql.ast;

/**
 * delete_stmt -> DELETE FROM IDENTIFIER where_opt ';'
 *
 * @param where 无 WHERE 时为 null
 */
public record DeleteStmt(
        String tableName,
        Expression where,
        int line,
        int col
) implements Statement {
    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitDeleteStmt(this);
    }
}
