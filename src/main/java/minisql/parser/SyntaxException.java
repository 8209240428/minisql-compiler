package minisql.parser;

import minisql.lexer.Token;
import minisql.lexer.TokenType;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 语法错误：携带位置、实际 Token、期望集合，便于答辩展示错误诊断。
 */
public class SyntaxException extends RuntimeException {
    private final int line;
    private final int col;
    private final String unexpected;
    private final Set<TokenType> expected;

    public SyntaxException(Token token, String message, Set<TokenType> expected) {
        super(format(token, message, expected));
        this.line = token.line();
        this.col = token.col();
        this.unexpected = token.type() == TokenType.EOF
                ? "EOF"
                : token.type().name() + (token.value().isEmpty() ? "" : "(" + token.value() + ")");
        this.expected = expected == null ? Set.of() : Set.copyOf(expected);
    }

    private static String format(Token token, String message, Set<TokenType> expected) {
        String unexpected = token.type() == TokenType.EOF
                ? "EOF"
                : token.type().name() + (token.value() == null || token.value().isEmpty()
                ? "" : "(" + token.value() + ")");
        String expect = (expected == null || expected.isEmpty())
                ? ""
                : "\nexpected: " + expected.stream().map(TokenType::name).collect(Collectors.joining(" | "));
        return String.format("[语法错误] line:%d, col:%d unexpected token: %s%s%s",
                token.line(), token.col(), unexpected,
                message == null || message.isEmpty() ? "" : " — " + message,
                expect);
    }

    public int getLine() {
        return line;
    }

    public int getCol() {
        return col;
    }

    public String getUnexpected() {
        return unexpected;
    }

    public Set<TokenType> getExpected() {
        return expected;
    }

    public static Set<TokenType> of(TokenType... types) {
        Set<TokenType> set = new LinkedHashSet<>();
        for (TokenType t : types) {
            set.add(t);
        }
        return set;
    }
}
