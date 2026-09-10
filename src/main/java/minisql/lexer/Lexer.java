package minisql.lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 词法分析器 Lexer
 * 输入SQL原始字符串，输出Token列表
 * 角色A实现：词法分析 + 行列号跟踪
 */
public class Lexer {
    private final String source;
    private int pos;        // 当前字符下标
    private int line;       // 当前行号，从1开始
    private int col;        // 当前列号，从1开始
    private char currentCh;
    private boolean isEnd;

    // SQL关键字集合，大写匹配
    private static final Set<String> KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT",
            "CREATE", "TABLE", "INSERT", "INTO", "VALUES", "DELETE",
            "INT", "VARCHAR"
    );

    public Lexer(String source) {
        this.source = source;
        this.line = 1;
        this.col = 1;
        this.pos = 0;
        if (source != null && !source.isEmpty()) {
            currentCh = source.charAt(0);
            isEnd = false;
        } else {
            isEnd = true;
            currentCh = '\0';
        }
    }

    /** 偷看当前字符，不移动指针 */
    private char peek() {
        return currentCh;
    }

    /** 消费一个字符，移动指针，更新行列 */
    private char advance() {
        char ch = currentCh;
        pos++;
        if (pos >= source.length()) {
            isEnd = true;
            currentCh = '\0';
        } else {
            currentCh = source.charAt(pos);
        }

        if (ch == '\n') {
            line++;
            col = 1;
        } else {
            col++;
        }
        return ch;
    }

    /** 跳过空白字符：空格、\t、\r、\n */
    private void skipWhitespace() {
        while (!isEnd && Character.isWhitespace(currentCh)) {
            advance();
        }
    }

    /** 读取标识符 / 关键字：字母、下划线开头，后续字母数字下划线 */
    private Token scanIdentifierOrKeyword() {
        int startLine = line;
        int startCol = col;
        StringBuilder sb = new StringBuilder();
        while (!isEnd && (Character.isLetterOrDigit(currentCh) || currentCh == '_')) {
            sb.append(advance());
        }
        String text = sb.toString();
        String upper = text.toUpperCase();
        if (KEYWORDS.contains(upper)) {
            TokenType type = TokenType.valueOf(upper);
            return new Token(type, text, startLine, startCol);
        } else {
            return new Token(TokenType.IDENTIFIER, text, startLine, startCol);
        }
    }

    /** 读取数字常量（仅整数） */
    private Token scanNumber() {
        int startLine = line;
        int startCol = col;
        StringBuilder sb = new StringBuilder();
        while (!isEnd && Character.isDigit(currentCh)) {
            sb.append(advance());
        }
        return new Token(TokenType.CONST, sb.toString(), startLine, startCol);
    }

    /** 读取单引号字符串常量 */
    private Token scanString() {
        int startLine = line;
        int startCol = col;
        advance(); // 吃掉开头 '
        StringBuilder sb = new StringBuilder();
        while (!isEnd && currentCh != '\'') {
            sb.append(advance());
        }
        if (isEnd) {
            throw new LexicalException("字符串未闭合，缺少单引号", startLine, startCol);
        }
        advance(); // 吃掉结尾 '
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    /** 读取下一个Token，词法主逻辑 */
    public Token nextToken() {
        skipWhitespace();
        if (isEnd) {
            return new Token(TokenType.EOF, "", line, col);
        }

        char ch = peek();
        // 标识符/关键字
        if (Character.isLetter(ch) || ch == '_') {
            return scanIdentifierOrKeyword();
        }
        // 数字
        if (Character.isDigit(ch)) {
            return scanNumber();
        }
        // 字符串 '...'
        if (ch == '\'') {
            return scanString();
        }

        // 符号
        return scanSymbol();
    }

    /** 处理运算符、分隔符 */
    private Token scanSymbol() {
        int startLine = line;
        int startCol = col;
        char ch = advance();
        return switch (ch) {
            case '*' -> new Token(TokenType.STAR, "*", startLine, startCol);
            case ',' -> new Token(TokenType.COMMA, ",", startLine, startCol);
            case '(' -> new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')' -> new Token(TokenType.RPAREN, ")", startLine, startCol);
            case ';' -> new Token(TokenType.SEMICOLON, ";", startLine, startCol);
            case '=' -> new Token(TokenType.EQ, "=", startLine, startCol);
            case '+' -> new Token(TokenType.PLUS, "+", startLine, startCol);
            case '-' -> new Token(TokenType.MINUS, "-", startLine, startCol);
            case '/' -> new Token(TokenType.SLASH, "/", startLine, startCol);
            case '!' -> {
                if (!isEnd && peek() == '=') {
                    advance();
                    yield new Token(TokenType.NEQ, "!=", startLine, startCol);
                }
                throw new LexicalException("无法识别的字符 '!'，是否想写 '!=' ?", startLine, startCol);
            }
            case '>' -> {
                if (!isEnd && peek() == '=') {
                    advance();
                    yield new Token(TokenType.GTE, ">=", startLine, startCol);
                }
                yield new Token(TokenType.GT, ">", startLine, startCol);
            }
            case '<' -> {
                if (!isEnd && peek() == '=') {
                    advance();
                    yield new Token(TokenType.LTE, "<=", startLine, startCol);
                } else if (!isEnd && peek() == '>') {
                    advance();
                    yield new Token(TokenType.NEQ, "<>", startLine, startCol);
                }
                yield new Token(TokenType.LT, "<", startLine, startCol);
            }
            default -> throw new LexicalException("无法识别的字符 '" + ch + "'", startLine, startCol);
        };
    }

    /**
     * 对外API：一次性获取全部Token流（包含EOF）
     */
    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        Token tk;
        do {
            tk = nextToken();
            tokens.add(tk);
        } while (tk.type() != TokenType.EOF);
        return tokens;
    }

    // 简单main测试入口，A角色本地调试用
    public static void main(String[] args) {
        String sql = "SELECT id,name FROM t1 WHERE age > 18 AND name = 'alice';";
        Lexer lexer = new Lexer(sql);
        List<Token> tokenList = lexer.tokenize();
        for (Token t : tokenList) {
            System.out.println(t);
        }
    }
}
