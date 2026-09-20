package minisql.exec;

import minisql.ast.Statement;
import minisql.catalog.ColumnMeta;
import minisql.lexer.Lexer;
import minisql.lexer.LexicalException;
import minisql.lexer.Token;
import minisql.lexer.TokenType;
import minisql.parser.Parser;
import minisql.parser.SyntaxException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * MiniSQL 命令行入口：串起「SQL 输入 → 编译 → 执行 → 存储 → 结果返回」的完整链路。
 *
 * 用法：
 *
 *   java -cp target/classes minisql.exec.Cli            # REPL，数据落在 data.db
 *   java -cp target/classes minisql.exec.Cli data.db    # REPL，指定数据文件
 *   java -cp target/classes minisql.exec.Cli data.db demo.sql   # 脚本模式（UTF-8）
 *
 * REPL 中 `exit` / `quit` 退出；退出时回写缓存并落盘，下次打开同一文件即恢复数据。
 */
public final class Cli {

    private static final String PROMPT = "minisql> ";

    private Cli() {
    }

    public static void main(String[] args) throws Exception {
        // 输出统一用 UTF-8，避免中文在部分控制台（GBK）下乱码
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        Path dbPath = Path.of(args.length > 0 ? args[0] : "data.db");

        try (Database db = Database.open(dbPath)) {
            if (args.length > 1) {
                String script = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
                runScript(db, script, out);
                out.println("脚本执行完毕，数据库文件: " + dbPath.toAbsolutePath());
            } else {
                repl(db, out);
            }
        }
    }

    /** 交互模式：攒到分号或 EOF 再执行一条；exit/quit 退出。 */
    private static void repl(Database db, PrintStream out) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        StringBuilder buf = new StringBuilder();
        out.println("MiniSQL 交互终端（输入 exit/quit 退出，输入 SQL 后以 ';' 结束）");
        while (true) {
            out.print(PROMPT);
            out.flush();
            String line = in.readLine();
            if (line == null) {
                if (!buf.isEmpty()) {
                    runScript(db, buf.toString(), out);
                }
                out.println();
                return;
            }
            String trimmed = line.trim();
            if (buf.isEmpty() && (trimmed.equalsIgnoreCase("exit") || trimmed.equalsIgnoreCase("quit"))) {
                return;
            }
            buf.append(line).append('\n');
            if (hasStatementEnd(trimmed)) {
                runScript(db, buf.toString(), out);
                buf.setLength(0);
            }
        }
    }

    private static boolean hasStatementEnd(String line) {
        return line.indexOf(';') >= 0;
    }

    /** 编译并执行一段 SQL 文本；逐条打印结果，词法/语法/语义错误不中断后续语句。 */
    static void runScript(Database db, String sql, PrintStream out) {
        final List<Token> all;
        try {
            all = new Lexer(sql).tokenize();
        } catch (LexicalException ex) {
            out.println("[错误] " + ex.getMessage());
            return;
        }
        List<Token> body = all.subList(0, all.size() - 1); // 去掉末尾 EOF
        Token eof = all.get(all.size() - 1);

        List<Token> current = new ArrayList<>();
        for (Token t : body) {
            current.add(t);
            if (t.type() == TokenType.SEMICOLON) {
                runOne(db, current, eof, out);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            runOne(db, current, eof, out);
        }
    }

    private static void runOne(Database db, List<Token> toks, Token eof, PrintStream out) {
        // 单独一个 ';' 视为空语句
        if (toks.size() == 1 && toks.get(0).type() == TokenType.SEMICOLON) {
            return;
        }
        List<Token> parseTokens = new ArrayList<>(toks);
        Token last = parseTokens.get(parseTokens.size() - 1);
        if (last.type() != TokenType.SEMICOLON) {
            parseTokens.add(new Token(TokenType.SEMICOLON, ";", eof.line(), eof.col()));
        }
        parseTokens.add(eof);

        final Statement stmt;
        try {
            stmt = new Parser(parseTokens).parse();
        } catch (SyntaxException ex) {
            out.println("[错误] " + ex.getMessage());
            return;
        }

        ExecutionResult r = db.run(stmt);
        print(out, r);
    }

    private static void print(PrintStream out, ExecutionResult r) {
        if (r.isError()) {
            out.println("[错误] " + r.errorText());
            return;
        }
        if (r.isMessage()) {
            out.println(r.message());
            return;
        }
        if (r.isResultSet()) {
            StringJoiner header = new StringJoiner(" | ");
            for (ColumnMeta c : r.columns()) {
                header.add(c.name());
            }
            out.println(header);
            out.println("-".repeat(header.length()));
            for (Row row : r.rows()) {
                StringJoiner line = new StringJoiner(" | ");
                for (Object cell : row.cells()) {
                    line.add(String.valueOf(cell));
                }
                out.println(line);
            }
            out.println("(" + r.rows().size() + " 行)");
            return;
        }
        out.println(r.affected() + " 行受影响");
    }
}
