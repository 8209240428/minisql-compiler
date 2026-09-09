package minisql.lexer;

/**
 * Token类型枚举，词法单元种类
 */
public enum TokenType {
    // 关键字
    SELECT,
    FROM,
    WHERE,
    AND,
    OR,
    NOT,
    CREATE,
    TABLE,
    INSERT,
    INTO,
    VALUES,
    DELETE,
    INT,
    VARCHAR,

    // 标识符：表名、列名
    IDENTIFIER,
    // 常量：数字仍用 CONST，字符串用 STRING（语义分析需要区分类型）
    CONST,
    STRING,

    // 符号
    COMMA,      // ,
    STAR,       // *
    LPAREN,     // (
    RPAREN,     // )
    SEMICOLON,  // ;

    // 比较运算符
    EQ,         // =
    NEQ,        // <>
    GT,         // >
    LT,         // <
    GTE,        // >=
    LTE,        // <=

    // 算术运算符（扩展：供常量折叠等优化使用）
    PLUS,       // +
    MINUS,      // -
    SLASH,      // /

    EOF         // 输入结束
}
