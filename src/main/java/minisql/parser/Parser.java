package minisql.parser;

import minisql.ast.*;
import minisql.lexer.Token;
import minisql.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * MiniSQL 递归下降 Parser（成员 B）。
 * 每个非终结符对应一个方法，lookahead 决定走哪条产生式。
 * 表达式用分层函数保证优先级：OR &lt; AND &lt; NOT &lt; 比较 &lt; + - &lt; * /
 */
public class Parser {
    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            throw new IllegalArgumentException("Token 流不能为空");
        }
        this.tokens = tokens;
        this.pos = 0;
    }

    /** 解析一条语句，之后必须到达 EOF */
    public Statement parse() {
        Statement stmt = parseStatement();
        if (!isAtEnd()) {
            throw error(peek(), "语句结束后仍有多余 Token", SyntaxException.of(TokenType.EOF));
        }
        return stmt;
    }

    /** 解析直到 EOF 的多条语句 */
    public List<Statement> parseAll() {
        List<Statement> stmts = new ArrayList<>();
        while (!isAtEnd()) {
            stmts.add(parseStatement());
        }
        return stmts;
    }

    /**
     * statement -> create_stmt | insert_stmt | select_stmt | delete_stmt
     */
    private Statement parseStatement() {
        if (check(TokenType.SELECT)) {
            return parseSelectStmt();
        }
        if (check(TokenType.CREATE)) {
            return parseCreateStmt();
        }
        if (check(TokenType.INSERT)) {
            return parseInsertStmt();
        }
        if (check(TokenType.DELETE)) {
            return parseDeleteStmt();
        }
        throw error(peek(), "期望一条 SQL 语句",
                SyntaxException.of(TokenType.SELECT, TokenType.CREATE, TokenType.INSERT, TokenType.DELETE));
    }

    /**
     * select_stmt -> SELECT select_list FROM IDENTIFIER where_opt ';'
     */
    private SelectStmt parseSelectStmt() {
        Token selectTk = consume(TokenType.SELECT, SyntaxException.of(TokenType.SELECT));
        boolean star;
        List<String> columns = new ArrayList<>();
        if (match(TokenType.STAR)) {
            star = true;
        } else {
            star = false;
            columns.add(consumeIdentifier("SELECT 列表需要列名或 *"));
            while (match(TokenType.COMMA)) {
                columns.add(consumeIdentifier("逗号后需要列名"));
            }
        }
        consume(TokenType.FROM, SyntaxException.of(TokenType.FROM));
        String table = consumeIdentifier("FROM 后需要表名");
        Expression where = parseWhereOpt();
        consume(TokenType.SEMICOLON, SyntaxException.of(TokenType.SEMICOLON));
        return new SelectStmt(star, columns, table, where, selectTk.line(), selectTk.col());
    }

    /**
     * delete_stmt -> DELETE FROM IDENTIFIER where_opt ';'
     */
    private DeleteStmt parseDeleteStmt() {
        Token deleteTk = consume(TokenType.DELETE, SyntaxException.of(TokenType.DELETE));
        consume(TokenType.FROM, SyntaxException.of(TokenType.FROM));
        String table = consumeIdentifier("DELETE FROM 后需要表名");
        Expression where = parseWhereOpt();
        consume(TokenType.SEMICOLON, SyntaxException.of(TokenType.SEMICOLON));
        return new DeleteStmt(table, where, deleteTk.line(), deleteTk.col());
    }

    /**
     * create_stmt -> CREATE TABLE IDENTIFIER '(' column_def { ',' column_def } ')' ';'
     */
    private CreateTableStmt parseCreateStmt() {
        Token createTk = consume(TokenType.CREATE, SyntaxException.of(TokenType.CREATE));
        consume(TokenType.TABLE, SyntaxException.of(TokenType.TABLE));
        String table = consumeIdentifier("CREATE TABLE 后需要表名");
        consume(TokenType.LPAREN, SyntaxException.of(TokenType.LPAREN));
        List<ColumnDef> cols = new ArrayList<>();
        cols.add(parseColumnDef());
        while (match(TokenType.COMMA)) {
            cols.add(parseColumnDef());
        }
        consume(TokenType.RPAREN, SyntaxException.of(TokenType.RPAREN));
        consume(TokenType.SEMICOLON, SyntaxException.of(TokenType.SEMICOLON));
        return new CreateTableStmt(table, cols, createTk.line(), createTk.col());
    }

    /** column_def -> IDENTIFIER type ; type -> INT | VARCHAR */
    private ColumnDef parseColumnDef() {
        Token nameTk = peek();
        String name = consumeIdentifier("列定义需要列名");
        DataType type;
        if (match(TokenType.INT)) {
            type = DataType.INT;
        } else if (match(TokenType.VARCHAR)) {
            type = DataType.VARCHAR;
        } else {
            throw error(peek(), "列类型只支持 INT / VARCHAR",
                    SyntaxException.of(TokenType.INT, TokenType.VARCHAR));
        }
        return new ColumnDef(name, type, nameTk.line(), nameTk.col());
    }

    /**
     * insert_stmt -> INSERT INTO IDENTIFIER '(' id_list ')' VALUES '(' value_list ')' ';'
     */
    private InsertStmt parseInsertStmt() {
        Token insertTk = consume(TokenType.INSERT, SyntaxException.of(TokenType.INSERT));
        consume(TokenType.INTO, SyntaxException.of(TokenType.INTO));
        String table = consumeIdentifier("INSERT INTO 后需要表名");
        consume(TokenType.LPAREN, SyntaxException.of(TokenType.LPAREN));
        List<String> columns = new ArrayList<>();
        columns.add(consumeIdentifier("需要列名"));
        while (match(TokenType.COMMA)) {
            columns.add(consumeIdentifier("逗号后需要列名"));
        }
        consume(TokenType.RPAREN, SyntaxException.of(TokenType.RPAREN));
        consume(TokenType.VALUES, SyntaxException.of(TokenType.VALUES));
        consume(TokenType.LPAREN, SyntaxException.of(TokenType.LPAREN));
        List<Expression> values = new ArrayList<>();
        values.add(parseExpression());
        while (match(TokenType.COMMA)) {
            values.add(parseExpression());
        }
        consume(TokenType.RPAREN, SyntaxException.of(TokenType.RPAREN));
        consume(TokenType.SEMICOLON, SyntaxException.of(TokenType.SEMICOLON));
        return new InsertStmt(table, columns, values, insertTk.line(), insertTk.col());
    }

    /** where_opt -> WHERE expression | ε */
    private Expression parseWhereOpt() {
        if (match(TokenType.WHERE)) {
            return parseExpression();
        }
        return null;
    }

    /** expression -> or_expr */
    private Expression parseExpression() {
        return parseOrExpr();
    }

    /** or_expr -> and_expr { OR and_expr } */
    private Expression parseOrExpr() {
        Expression left = parseAndExpr();
        while (check(TokenType.OR)) {
            Token op = advance();
            Expression right = parseAndExpr();
            left = new BinaryExpr(left, BinaryOp.OR, right, op.line(), op.col());
        }
        return left;
    }

    /** and_expr -> not_expr { AND not_expr } */
    private Expression parseAndExpr() {
        Expression left = parseNotExpr();
        while (check(TokenType.AND)) {
            Token op = advance();
            Expression right = parseNotExpr();
            left = new BinaryExpr(left, BinaryOp.AND, right, op.line(), op.col());
        }
        return left;
    }

    /** not_expr -> NOT not_expr | comparison */
    private Expression parseNotExpr() {
        if (check(TokenType.NOT)) {
            Token op = advance();
            return new UnaryExpr(UnaryOp.NOT, parseNotExpr(), op.line(), op.col());
        }
        return parseComparison();
    }

    /** comparison -> additive [ comp_op additive ] */
    private Expression parseComparison() {
        Expression left = parseAdditive();
        if (isCompOp(peek().type())) {
            Token op = advance();
            Expression right = parseAdditive();
            return new BinaryExpr(left, BinaryOp.from(op.type()), right, op.line(), op.col());
        }
        return left;
    }

    /** additive -> multiplicative { ('+' | '-') multiplicative } */
    private Expression parseAdditive() {
        Expression left = parseMultiplicative();
        while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
            Token op = advance();
            Expression right = parseMultiplicative();
            left = new BinaryExpr(left, BinaryOp.from(op.type()), right, op.line(), op.col());
        }
        return left;
    }

    /** multiplicative -> unary { ('*' | '/') unary } */
    private Expression parseMultiplicative() {
        Expression left = parseUnary();
        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            Token op = advance();
            Expression right = parseUnary();
            left = new BinaryExpr(left, BinaryOp.from(op.type()), right, op.line(), op.col());
        }
        return left;
    }

    /** unary -> '-' unary | primary */
    private Expression parseUnary() {
        if (check(TokenType.MINUS)) {
            Token op = advance();
            return new UnaryExpr(UnaryOp.NEGATE, parseUnary(), op.line(), op.col());
        }
        return parsePrimary();
    }

    /** primary -> IDENTIFIER | CONST | STRING | '(' expression ')' */
    private Expression parsePrimary() {
        Token tk = peek();
        if (match(TokenType.IDENTIFIER)) {
            return new IdentifierExpr(tk.value(), tk.line(), tk.col());
        }
        if (match(TokenType.CONST)) {
            return new LiteralExpr(tk.value(), LiteralExpr.Kind.NUMBER, tk.line(), tk.col());
        }
        if (match(TokenType.STRING)) {
            return new LiteralExpr(tk.value(), LiteralExpr.Kind.STRING, tk.line(), tk.col());
        }
        if (match(TokenType.LPAREN)) {
            Expression inner = parseExpression();
            consume(TokenType.RPAREN, SyntaxException.of(TokenType.RPAREN));
            return inner;
        }
        throw error(tk, "期望标识符、常量或 '('",
                SyntaxException.of(TokenType.IDENTIFIER, TokenType.CONST, TokenType.STRING, TokenType.LPAREN, TokenType.NOT));
    }

    private boolean isCompOp(TokenType type) {
        return type == TokenType.EQ || type == TokenType.NEQ
                || type == TokenType.GT || type == TokenType.LT
                || type == TokenType.GTE || type == TokenType.LTE;
    }

    private String consumeIdentifier(String message) {
        if (check(TokenType.IDENTIFIER)) {
            return advance().value();
        }
        throw error(peek(), message, SyntaxException.of(TokenType.IDENTIFIER));
    }

    private boolean match(TokenType type) {
        if (check(type)) {
            advance();
            return true;
        }
        return false;
    }

    private Token consume(TokenType type, Set<TokenType> expected) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), "期望 " + type.name(), expected);
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return type == TokenType.EOF;
        }
        return peek().type() == type;
    }

    private Token advance() {
        Token current = peek();
        if (!isAtEnd()) {
            pos++;
        }
        return current;
    }

    private boolean isAtEnd() {
        return peek().type() == TokenType.EOF;
    }

    private Token peek() {
        if (pos >= tokens.size()) {
            Token last = tokens.get(tokens.size() - 1);
            if (last.type() == TokenType.EOF) {
                return last;
            }
            return new Token(TokenType.EOF, "", last.line(), last.col());
        }
        return tokens.get(pos);
    }

    private SyntaxException error(Token token, String message, Set<TokenType> expected) {
        return new SyntaxException(token, message, expected);
    }
}
