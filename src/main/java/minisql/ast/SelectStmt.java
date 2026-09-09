package minisql.ast;

import java.util.List;

/**
 * select_stmt -> SELECT select_list FROM IDENTIFIER where_opt ';'
 *
 * @param star    select_list 为 '*' 时为 true
 * @param columns star 为 false 时的列名列表
 * @param where   无 WHERE 时为 null
 */
public record SelectStmt(
        boolean star,
        List<String> columns,
        String tableName,
        Expression where,
        int line,
        int col
) implements Statement {
    public SelectStmt {
        columns = List.copyOf(columns);
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitSelectStmt(this);
    }
}
