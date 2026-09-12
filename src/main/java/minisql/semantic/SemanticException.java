package minisql.semantic;

import minisql.ast.AstNode;

/**
 * 语义错误，携带行号列号，消息形如
 * {@code [语义错误] line:1, col:20 未定义的列 'foo'}
 *
 * <p>格式与词法/语法异常保持一致（成员分工约定：词法 LexicalException / 语法 SyntaxException / 语义 SemanticException）。
 */
public class SemanticException extends RuntimeException {
    private final int line;
    private final int col;

    public SemanticException(String message, int line, int col) {
        super(String.format("[语义错误] line:%d, col:%d %s", line, col, message));
        this.line = line;
        this.col = col;
    }

    /** 定位到某个 AST 节点（record 节点都带 line()/col()） */
    public static SemanticException at(AstNode node, String message) {
        return new SemanticException(message, node.line(), node.col());
    }

    /** 定位到任意带行列的载体（如 CreateTable 的 ColumnDef，其不实现 AstNode） */
    public static SemanticException at(int line, int col, String message) {
        return new SemanticException(message, line, col);
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }
}
