package minisql.lexer;

/**
 * 词法分析异常，携带行号列号
 */
public class LexicalException extends RuntimeException {
    private final int line;
    private final int col;

    public LexicalException(String message, int line, int col) {
        super(String.format("[词法错误] line:%d, col:%d %s", line, col, message));
        this.line = line;
        this.col = col;
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }
}
