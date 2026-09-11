package minisql;

import minisql.ast.Statement;
import minisql.lexer.Lexer;
import minisql.lexer.Token;
import minisql.parser.Parser;

import java.util.List;

/**
 * 给 C 模块用的入口：SQL 文本 → AST。
 */
public final class MiniSqlFrontend {
    private MiniSqlFrontend() {
    }

    public static List<Token> tokenize(String sql) {
        return new Lexer(sql).tokenize();
    }

    public static Statement parse(String sql) {
        return new Parser(tokenize(sql)).parse();
    }

    public static List<Statement> parseAll(String sql) {
        return new Parser(tokenize(sql)).parseAll();
    }
}
