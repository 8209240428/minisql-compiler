package minisql.lexer;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Lexer单元测试，A角色负责编写，合并前单测覆盖率>=80%
 */
class LexerTest {

    @Test
    void testBasicSelectToken() {
        String sql = "SELECT id,name FROM t1;";
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.tokenize();

        assertEquals(TokenType.SELECT, tokens.get(0).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals("id", tokens.get(1).value());
        assertEquals(TokenType.COMMA, tokens.get(2).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type());
        assertEquals("name", tokens.get(3).value());
        assertEquals(TokenType.FROM, tokens.get(4).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(5).type());
        assertEquals("t1", tokens.get(5).value());
        assertEquals(TokenType.SEMICOLON, tokens.get(6).type());
        assertEquals(TokenType.EOF, tokens.get(7).type());
    }

    @Test
    void testWhereConditionOperator() {
        String sql = "WHERE age >= 20 AND id <> 5";
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.tokenize();
        assertEquals(TokenType.WHERE, tokens.get(0).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(1).type());
        assertEquals(TokenType.GTE, tokens.get(2).type());
        assertEquals(TokenType.CONST, tokens.get(3).type());
        assertEquals(TokenType.AND, tokens.get(4).type());
        assertEquals(TokenType.IDENTIFIER, tokens.get(5).type());
        assertEquals(TokenType.NEQ, tokens.get(6).type());
    }

    @Test
    void testStringLiteral() {
        String sql = "name = 'zhangsan'";
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.tokenize();
        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type());
        assertEquals(TokenType.EQ, tokens.get(1).type());
        assertEquals(TokenType.STRING, tokens.get(2).type());
        assertEquals("zhangsan", tokens.get(2).value());
    }

    @Test
    void testUnclosedStringThrow() {
        String sql = "name = 'abc";
        Lexer lexer = new Lexer(sql);
        LexicalException ex = assertThrows(LexicalException.class, lexer::tokenize);
        assertTrue(ex.getMessage().contains("字符串未闭合"));
    }

    @Test
    void testStarSymbol() {
        String sql = "SELECT * FROM t";
        Lexer lexer = new Lexer(sql);
        List<Token> tokens = lexer.tokenize();
        assertEquals(TokenType.STAR, tokens.get(1).type());
    }

    @Test
    void testUnknownCharThrow() {
        String sql = "SELECT # FROM t";
        Lexer lexer = new Lexer(sql);
        assertThrows(LexicalException.class, lexer::tokenize);
    }
}
