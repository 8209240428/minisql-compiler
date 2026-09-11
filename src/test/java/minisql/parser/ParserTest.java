package minisql.parser;

import minisql.MiniSqlFrontend;
import minisql.ast.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser / AST 单元测试（成员 B）。
 */
class ParserTest {

    private Statement parse(String sql) {
        return MiniSqlFrontend.parse(sql);
    }

    @Test
    void testSelectStar() {
        SelectStmt stmt = (SelectStmt) parse("SELECT * FROM t1;");
        assertTrue(stmt.star());
        assertTrue(stmt.columns().isEmpty());
        assertEquals("t1", stmt.tableName());
        assertNull(stmt.where());
    }

    @Test
    void testSelectColumnsAndWhere() {
        SelectStmt stmt = (SelectStmt) parse(
                "SELECT id, name FROM student WHERE age > 18 AND id != 3;");
        assertFalse(stmt.star());
        assertEquals(List.of("id", "name"), stmt.columns());
        assertEquals("student", stmt.tableName());
        assertNotNull(stmt.where());
        assertInstanceOf(BinaryExpr.class, stmt.where());
        BinaryExpr and = (BinaryExpr) stmt.where();
        assertEquals(BinaryOp.AND, and.op());
        BinaryExpr gt = (BinaryExpr) and.left();
        assertEquals(BinaryOp.GT, gt.op());
        assertEquals("age", ((IdentifierExpr) gt.left()).name());
        BinaryExpr neq = (BinaryExpr) and.right();
        assertEquals(BinaryOp.NEQ, neq.op());
        assertEquals("3", ((LiteralExpr) neq.right()).raw());
    }

    @Test
    void testCreateTable() {
        CreateTableStmt stmt = (CreateTableStmt) parse(
                "CREATE TABLE student(id INT, name VARCHAR, age INT);");
        assertEquals("student", stmt.tableName());
        assertEquals(3, stmt.columns().size());
        assertEquals("id", stmt.columns().get(0).name());
        assertEquals(DataType.INT, stmt.columns().get(0).type());
        assertEquals("name", stmt.columns().get(1).name());
        assertEquals(DataType.VARCHAR, stmt.columns().get(1).type());
    }

    @Test
    void testInsert() {
        InsertStmt stmt = (InsertStmt) parse(
                "INSERT INTO student(id,name,age) VALUES (1,'Alice',20);");
        assertEquals("student", stmt.tableName());
        assertEquals(List.of("id", "name", "age"), stmt.columns());
        assertEquals(3, stmt.values().size());
        LiteralExpr name = (LiteralExpr) stmt.values().get(1);
        assertEquals(LiteralExpr.Kind.STRING, name.kind());
        assertEquals("Alice", name.raw());
        LiteralExpr id = (LiteralExpr) stmt.values().get(0);
        assertEquals(LiteralExpr.Kind.NUMBER, id.kind());
        assertEquals("1", id.raw());
    }

    @Test
    void testDelete() {
        DeleteStmt stmt = (DeleteStmt) parse("DELETE FROM student WHERE id = 1;");
        assertEquals("student", stmt.tableName());
        BinaryExpr eq = (BinaryExpr) stmt.where();
        assertEquals(BinaryOp.EQ, eq.op());
    }

    @Test
    void testOrAndPrecedence() {
        // a = 1 OR b = 2 AND c = 3  等价于  a=1 OR (b=2 AND c=3)
        SelectStmt stmt = (SelectStmt) parse(
                "SELECT * FROM t WHERE a = 1 OR b = 2 AND c = 3;");
        BinaryExpr or = (BinaryExpr) stmt.where();
        assertEquals(BinaryOp.OR, or.op());
        BinaryExpr leftEq = (BinaryExpr) or.left();
        assertEquals("a", ((IdentifierExpr) leftEq.left()).name());
        BinaryExpr and = (BinaryExpr) or.right();
        assertEquals(BinaryOp.AND, and.op());
        assertEquals("b", ((IdentifierExpr) ((BinaryExpr) and.left()).left()).name());
        assertEquals("c", ((IdentifierExpr) ((BinaryExpr) and.right()).left()).name());

        String infix = new AstPrinter().toInfix(stmt.where());
        assertEquals("((a = 1) OR ((b = 2) AND (c = 3)))", infix);
    }

    @Test
    void testParenthesesChangePrecedence() {
        SelectStmt stmt = (SelectStmt) parse(
                "SELECT * FROM t WHERE (a = 1 OR b = 2) AND c = 3;");
        BinaryExpr and = (BinaryExpr) stmt.where();
        assertEquals(BinaryOp.AND, and.op());
        BinaryExpr or = (BinaryExpr) and.left();
        assertEquals(BinaryOp.OR, or.op());
    }

    @Test
    void testNotAndArithmetic() {
        SelectStmt stmt = (SelectStmt) parse(
                "SELECT name FROM student WHERE NOT age > 10 + 8;");
        UnaryExpr not = (UnaryExpr) stmt.where();
        assertEquals(UnaryOp.NOT, not.op());
        BinaryExpr gt = (BinaryExpr) not.operand();
        assertEquals(BinaryOp.GT, gt.op());
        BinaryExpr plus = (BinaryExpr) gt.right();
        assertEquals(BinaryOp.PLUS, plus.op());
        assertEquals("10", ((LiteralExpr) plus.left()).raw());
        assertEquals("8", ((LiteralExpr) plus.right()).raw());
    }

    @Test
    void testKeywordCaseInsensitive() {
        Statement stmt = parse("select * from Student where Age >= 18;");
        assertInstanceOf(SelectStmt.class, stmt);
        SelectStmt sel = (SelectStmt) stmt;
        assertEquals("Student", sel.tableName());
    }

    @Test
    void testParseAllMultipleStatements() {
        List<Statement> stmts = MiniSqlFrontend.parseAll(
                "CREATE TABLE t(id INT); INSERT INTO t(id) VALUES (1); SELECT * FROM t;");
        assertEquals(3, stmts.size());
        assertInstanceOf(CreateTableStmt.class, stmts.get(0));
        assertInstanceOf(InsertStmt.class, stmts.get(1));
        assertInstanceOf(SelectStmt.class, stmts.get(2));
    }

    @Test
    void testMissingSemicolon() {
        SyntaxException ex = assertThrows(SyntaxException.class,
                () -> parse("SELECT * FROM t"));
        assertTrue(ex.getMessage().contains("SEMICOLON"));
        assertFalse(ex.getExpected().isEmpty());
    }

    @Test
    void testUnexpectedTokenInWhere() {
        SyntaxException ex = assertThrows(SyntaxException.class,
                () -> parse("SELECT name FROM student WHERE age > 18 AND;"));
        assertTrue(ex.getMessage().contains("语法错误"));
        assertTrue(ex.getMessage().contains("unexpected"));
        assertTrue(ex.getExpected().contains(minisql.lexer.TokenType.IDENTIFIER)
                || ex.getMessage().contains("IDENTIFIER"));
    }

    @Test
    void testVisitorAccept() {
        SelectStmt stmt = (SelectStmt) parse("SELECT id FROM t;");
        String kind = stmt.accept(new AstVisitor<>() {
            @Override
            public String visitCreateTableStmt(CreateTableStmt s) {
                return "create";
            }

            @Override
            public String visitInsertStmt(InsertStmt s) {
                return "insert";
            }

            @Override
            public String visitSelectStmt(SelectStmt s) {
                return "select";
            }

            @Override
            public String visitDeleteStmt(DeleteStmt s) {
                return "delete";
            }

            @Override
            public String visitBinaryExpr(BinaryExpr e) {
                return "bin";
            }

            @Override
            public String visitUnaryExpr(UnaryExpr e) {
                return "unary";
            }

            @Override
            public String visitIdentifierExpr(IdentifierExpr e) {
                return "id";
            }

            @Override
            public String visitLiteralExpr(LiteralExpr e) {
                return "lit";
            }
        });
        assertEquals("select", kind);
    }
}
