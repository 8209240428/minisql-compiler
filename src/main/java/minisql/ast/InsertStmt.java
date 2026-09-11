package minisql.ast;

import java.util.List;

/**
 * insert_stmt -> INSERT INTO IDENTIFIER '(' id_list ')' VALUES '(' value_list ')' ';'
 */
public record InsertStmt(
        String tableName,
        List<String> columns,
        List<Expression> values,
        int line,
        int col
) implements Statement {
    public InsertStmt {
        columns = List.copyOf(columns);
        values = List.copyOf(values);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitInsertStmt(this);
    }
}
