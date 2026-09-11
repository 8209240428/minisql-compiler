package minisql.ast;

/**
 * AST 访问者。语义分析、计划生成、打印都可以实现此接口，
 * 避免在 Parser 里散落后续阶段逻辑。
 */
public interface AstVisitor<R> {
    R visitCreateTableStmt(CreateTableStmt stmt);

    R visitInsertStmt(InsertStmt stmt);

    R visitSelectStmt(SelectStmt stmt);

    R visitDeleteStmt(DeleteStmt stmt);

    R visitBinaryExpr(BinaryExpr expr);

    R visitUnaryExpr(UnaryExpr expr);

    R visitIdentifierExpr(IdentifierExpr expr);

    R visitLiteralExpr(LiteralExpr expr);
}
