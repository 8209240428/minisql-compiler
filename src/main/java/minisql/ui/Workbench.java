package minisql.ui;

import minisql.CompileResult;
import minisql.MiniSqlCompiler;
import minisql.ast.Statement;
import minisql.catalog.Catalog;
import minisql.lexer.Lexer;
import minisql.lexer.LexicalException;
import minisql.lexer.Token;
import minisql.lexer.TokenType;
import minisql.parser.Parser;
import minisql.parser.SyntaxException;

import java.util.ArrayList;
import java.util.List;

/**
 * 交互界面的<b>会话模型</b>（不依赖 Swing，便于单测、也便于他人迭代界面时只替换窗口层）。
 *
 * <p>职责：把一段 SQL 文本编译成结构化结果 {@link Report}，供 UI 分 Tab 展示
 * Token / AST / 执行计划 / 运行摘要。相对 {@link MiniSqlCompiler#compileAll} 的差异（更友好）：
 * <ul>
 *   <li><b>词法/语法错误不中断整段脚本</b>：词法错会整段失败并给出位置；语法错只跳过出错那一条语句，
 *       后续语句仍继续编译（如“先 CREATE 后某句写错，后面的 SELECT 仍能看到已建的表”）。</li>
 *   <li><b>允许省略最后一条语句末尾的分号</b>（交互输入更顺手）。</li>
 *   <li>把词法流按顶层 {@code ';'} 切分成一条条语句，而不是整段一起 parse。</li>
 * </ul>
 *
 * <p>会话状态：{@link Workbench} 内部持有一个 {@link MiniSqlCompiler}（其 Catalog 为会话共享），
 * 多次调用 {@link #run} 之间的表定义会累积；调用 {@link #reset()} 清空会话。
 *
 * <p>对外只暴露三件事，其他人接 UI 只用这三个入口即可：
 * <pre>
 * Workbench wb = new Workbench();
 * Workbench.Report rep = wb.run(sql);   // 编译一段 SQL
 * List&lt;String&gt; tables = wb.tableNames(); // 会话内当前已建的表
 * wb.reset();                             // 清空会话（等价于“新开会话”）
 * </pre>
 * 展示文本可交给 {@link ReportText}（纯字符串渲染），或自行决定怎么画。
 */
public final class Workbench {

    private MiniSqlCompiler compiler = new MiniSqlCompiler();

    /** 重置会话：丢弃已登记的表结构等全部状态（重新 new 一个编译器）。 */
    public void reset() {
        compiler = new MiniSqlCompiler();
    }

    /** 会话内当前 Catalog（只读使用：查表名等）。 */
    public Catalog catalog() {
        return compiler.catalog();
    }

    /** 会话内当前已建表名（按登记顺序），仅用于状态栏/摘要展示。 */
    public List<String> tableNames() {
        List<String> names = new ArrayList<>();
        for (minisql.catalog.TableMeta t : compiler.catalog().tables()) {
            names.add(t.name());
        }
        return names;
    }

    /**
     * 编译一段 SQL 文本。
     *
     * @param sql 可含多条语句；语句之间用 {@code ';'} 分隔；末句分号可省
     * @return 结构化编译结果；不会抛出词法/语法异常（都已封装进 {@link Report}）
     */
    public Report run(String sql) {
        String input = sql == null ? "" : sql;

        // 1) 词法：整段失败则直接返回错误（无法继续切分）
        final List<Token> all;
        try {
            all = new Lexer(input).tokenize();
        } catch (LexicalException ex) {
            return new Report(List.of(), ex.getMessage(), List.of());
        }

        // 2) 按顶层 ';' 把 Token 切成一条条语句
        List<Token> body = all.isEmpty() ? List.of() : all.subList(0, all.size() - 1); // 去掉末尾 EOF
        Token eof = all.isEmpty() ? null : all.get(all.size() - 1);
        List<Entry> entries = new ArrayList<>();
        List<Token> current = new ArrayList<>();
        for (Token t : body) {
            current.add(t);
            if (t.type() == TokenType.SEMICOLON) {
                compileOne(entries, current, eof);
                current = new ArrayList<>();
            }
        }
        if (!current.isEmpty()) {
            compileOne(entries, current, eof);
        }

        return new Report(List.copyOf(all), null, List.copyOf(entries));
    }

    /** 把一段（已切好的）语句 Token 编译成一条 Entry；若该段为“空语句”则忽略。 */
    private void compileOne(List<Entry> entries, List<Token> toks, Token eof) {
        // 单独一个 ';' 视为空语句，忽略
        if (toks.size() == 1 && toks.get(0).type() == TokenType.SEMICOLON) {
            return;
        }
        int index = entries.size() + 1;

        // Parser 要求语句以 ';' 结束并以 EOF 收尾：缺末尾 ';' 时补一个（位置借用 EOF）
        List<Token> parseTokens = new ArrayList<>(toks);
        Token last = parseTokens.get(parseTokens.size() - 1);
        if (last.type() != TokenType.SEMICOLON) {
            Token at = eof != null ? eof : new Token(TokenType.EOF, "", 1, 1);
            parseTokens.add(new Token(TokenType.SEMICOLON, ";", at.line(), at.col()));
        }
        parseTokens.add(eof != null ? eof : new Token(TokenType.EOF, "", 1, 1));

        final Statement stmt;
        try {
            stmt = new Parser(parseTokens).parse();
        } catch (SyntaxException ex) {
            entries.add(new Entry(index, null, ex.getMessage(), null));
            return;
        }
        // MiniSqlCompiler.compile 永不抛语义异常：语义错误被封装进 CompileResult
        CompileResult result = compiler.compile(stmt);
        entries.add(new Entry(index, stmt, null, result));
    }

    // ------------------------------------------------------------------
    // 结果模型（均为不可变记录）
    // ------------------------------------------------------------------

    /** 一次 {@link #run} 的结构化结果。 */
    public static final class Report {
        private final List<Token> tokens;   // 整段词法流（含末尾 EOF；词法失败时为空）
        private final String lexicalError;  // 词法失败信息；成功时为 null
        private final List<Entry> entries;  // 逐条语句结果（空语句会被忽略）

        Report(List<Token> tokens, String lexicalError, List<Entry> entries) {
            this.tokens = List.copyOf(tokens);
            this.lexicalError = lexicalError;
            this.entries = List.copyOf(entries);
        }

        public List<Token> tokens() {
            return tokens;
        }

        public boolean lexOk() {
            return lexicalError == null;
        }

        public String lexicalError() {
            return lexicalError;
        }

        public List<Entry> entries() {
            return entries;
        }

        /** 含词法失败在内的错误语句数（供状态栏/摘要） */
        public int failureCount() {
            if (!lexOk()) {
                return 1;
            }
            int n = 0;
            for (Entry e : entries) {
                if (!e.parseOk() || (e.result() != null && !e.result().ok())) {
                    n++;
                }
            }
            return n;
        }
    }

    /** 一条语句的编译结果。 */
    public static final class Entry {
        private final int index;          // 1-based 语句序号
        private final Statement statement; // parse 成功时非 null
        private final String parseError;   // parse 失败时非 null（信息含行列号）
        private final CompileResult result;// parse 成功时为编译结果（语义错误也被封装在内）

        Entry(int index, Statement statement, String parseError, CompileResult result) {
            this.index = index;
            this.statement = statement;
            this.parseError = parseError;
            this.result = result;
        }

        public int index() {
            return index;
        }

        public boolean parseOk() {
            return statement != null;
        }

        public Statement statement() {
            return statement;
        }

        public String parseError() {
            return parseError;
        }

        /** 仅当 parse 成功时为非 null（语义失败也在此结果内，见 {@link CompileResult#ok()}）。 */
        public CompileResult result() {
            return result;
        }
    }
}
