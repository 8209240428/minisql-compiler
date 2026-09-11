package minisql.ast;

import java.util.List;

/**
 * create_stmt -> CREATE TABLE IDENTIFIER '(' column_def { ',' column_def } ')' ';'
 */
public record CreateTableStmt(
        String tableName,
        List<ColumnDef> columns,
        int line,
        int col
) implements Statement {
    public CreateTableStmt {
        columns = List.copyOf(columns);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitCreateTableStmt(this);
    }
}
