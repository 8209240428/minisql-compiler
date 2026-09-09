package minisql.parser;

import minisql.ast.Statement;
import minisql.lexer.Lexer;
import minisql.lexer.Token;

import java.util.List;

/**
 * 成员 B 本地演示入口：SQL → Token → AST。
 * 运行：mvn -q exec:java -Dexec.mainClass=minisql.parser.ParserDemo
 * 或在 IDE 里直接 Run。
 */
public class ParserDemo {
    private static final String[] SAMPLES = {
            "CREATE TABLE student(id INT, name VARCHAR, age INT);",
            "INSERT INTO student(id,name,age) VALUES (1,'Alice',20);",
            "SELECT id, name FROM student WHERE age > 18 AND id != 3;",
            "SELECT * FROM student WHERE a = 1 OR b = 2 AND c = 3;",
            "DELETE FROM student WHERE id = 1;",
            "SELECT name FROM student WHERE 1 = 1 AND age > 10 + 8;",
            "SELECT name FROM student WHERE age > 18 AND;"
    };

    public static void main(String[] args) {
        if (args.length > 0) {
            run(String.join(" ", args));
            return;
        }
        for (String sql : SAMPLES) {
            System.out.println("==================================================");
            run(sql);
            System.out.println();
        }
    }

    private static void run(String sql) {
        System.out.println("SQL: " + sql);
        try {
            List<Token> tokens = new Lexer(sql).tokenize();
            System.out.println("-- Token --");
            for (Token t : tokens) {
                System.out.println("  " + t);
            }
            List<Statement> stmts = new Parser(tokens).parseAll();
            AstPrinter printer = new AstPrinter();
            System.out.println("-- AST --");
            System.out.println(printer.printAll(stmts));
            if (stmts.size() == 1 && stmts.get(0) instanceof minisql.ast.SelectStmt sel && sel.where() != null) {
                System.out.println("-- WHERE 中缀（核对优先级） --");
                System.out.println("  " + printer.toInfix(sel.where()));
            }
        } catch (RuntimeException ex) {
            System.out.println("-- 错误 --");
            System.out.println(ex.getMessage());
        }
    }
}
