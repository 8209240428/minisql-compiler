package minisql.lexer;

/**
 * 词法Token，记录类型、文本值、行列位置，用于报错定位
 */
public record Token(TokenType type, String value, int line, int col) {
    @Override
    public String toString() {
        return String.format("Token(%s, value=%s, line=%d, col=%d)",
                type.name(), value, line, col);
    }
}
