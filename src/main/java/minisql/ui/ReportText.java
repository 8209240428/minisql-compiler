package minisql.ui;

import minisql.CompileResult;
import minisql.ast.CreateTableStmt;
import minisql.ast.DeleteStmt;
import minisql.ast.InsertStmt;
import minisql.ast.SelectStmt;
import minisql.ast.Statement;
import minisql.lexer.Token;
import minisql.lexer.TokenType;
import minisql.parser.AstPrinter;

import java.util.List;

/**
 * 把 {@link Workbench.Report} 渲染成<b>纯文本</b>（供 Swing 文本区等直接展示）。
 *
 * <p>刻意与 Swing 解耦：只做字符串拼接，方便单测；若后续把界面换成别的（网页/命令行），
 * 只要不改这套渲染、或换一套自己的渲染即可。所有方法都是静态的。
 */
public final class ReportText {

    private ReportText() {
    }

    /** 分隔提示：文件顶部说明用 */
    public static final String HELP =
            "MiniSQL 编译器 · 交互演示\n"
                    + "────────────────────────────\n"
                    + "支持语句：CREATE TABLE / INSERT INTO / SELECT … FROM … [WHERE] / DELETE FROM … [WHERE]\n"
                    + "运算符：比较 = <> != > >= < <= ；逻辑 AND OR NOT ；算术 + - * / ；括号 ( )\n"
                    + "类型：INT、VARCHAR（字符串请用单引号，如 'Alice'）\n"
                    + "────────────────────────────\n"
                    + "操作：\n"
                    + "  · 上方输入区输入 SQL，多条语句用 ; 分隔（末句分号可省）\n"
                    + "  · Ctrl+Enter 或点「运行」编译；错误不会中断整段脚本\n"
                    + "  · 「示例」按钮会重置会话并载入一段演示 SQL\n"
                    + "  · 「重置会话」清空已登记的表（相当于新开一个连接）\n"
                    + "结果：Token 流 / AST / 优化前后执行计划 / 运行摘要 分 4 个 Tab 展示\n";

    // ------------------------------------------------------------------
    // Token 流
    // ------------------------------------------------------------------

    /** Token 流文本：每行一个 Token，每条语句之间空一行；词法失败时给出错误。 */
    public static String tokens(Workbench.Report report) {
        if (!report.lexOk()) {
            return "词法分析失败：\n" + report.lexicalError();
        }
        StringBuilder sb = new StringBuilder();
        for (Token t : report.tokens()) {
            sb.append(t).append('\n');
            if (t.type() == TokenType.SEMICOLON) {
                sb.append('\n'); // 语句间空行，便于阅读
            }
        }
        return sb.length() == 0 ? "（空输入）" : sb.toString();
    }

    // ------------------------------------------------------------------
    // AST
    // ------------------------------------------------------------------

    /** AST 文本：逐条语句打印语法树。 */
    public static String ast(Workbench.Report report) {
        StringBuilder sb = new StringBuilder();
        for (Workbench.Entry e : report.entries()) {
            sb.append("—— 语句 ").append(e.index()).append(" ——").append('\n');
            if (e.parseOk()) {
                sb.append(new AstPrinter().print(e.statement()));
            } else {
                sb.append("（语法错误，无 AST）：").append(e.parseError());
            }
            sb.append("\n\n");
        }
        if (report.lexOk() && report.entries().isEmpty()) {
            return "（没有可解析的语句）";
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 执行计划（优化前 / 每步 / 优化后）
    // ------------------------------------------------------------------

    /** 执行计划与优化过程文本。 */
    public static String plans(Workbench.Report report) {
        StringBuilder sb = new StringBuilder();
        for (Workbench.Entry e : report.entries()) {
            sb.append("—— 语句 ").append(e.index()).append(" ——").append('\n');
            if (!e.parseOk()) {
                sb.append("（语法错误，无执行计划）：").append(e.parseError()).append("\n\n");
                continue;
            }
            CompileResult r = e.result();
            if (r == null) {
                sb.append("（无编译结果）\n\n");
                continue;
            }
            if (!r.ok()) {
                sb.append("（语义错误，无执行计划）：\n  ").append(r.error().getMessage()).append("\n\n");
                continue;
            }
            if (!r.hasPlan()) {
                sb.append("CREATE TABLE：不生成执行计划（表已登记进 Catalog）\n\n");
                continue;
            }
            sb.append("计划（优化前）：\n").append(indent(r.before().print())).append('\n');
            if (r.stepNames().isEmpty()) {
                sb.append("（没有触发任何优化改写）\n");
            } else {
                for (int i = 0; i < r.stepNames().size(); i++) {
                    sb.append("  —— 优化步骤 ").append(i + 1).append("（")
                            .append(r.stepNames().get(i)).append("）——\n");
                    sb.append(indent(r.steps().get(i).print())).append('\n');
                }
                sb.append("计划（优化后）：\n").append(indent(r.after().print())).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 运行摘要（每条一句结果 + 会话表）
    // ------------------------------------------------------------------

    /** 运行摘要文本：逐条语句的成功/失败 + 当前会话表。 */
    public static String summary(Workbench.Report report, List<String> tables) {
        StringBuilder sb = new StringBuilder();
        if (!report.lexOk()) {
            sb.append("✗ 词法错误：").append(report.lexicalError()).append('\n');
        }
        for (Workbench.Entry e : report.entries()) {
            sb.append("语句 ").append(e.index()).append(" · ");
            if (!e.parseOk()) {
                sb.append("✗ 语法错误：").append(e.parseError()).append('\n');
                continue;
            }
            CompileResult r = e.result();
            if (r == null) {
                sb.append("？ 无编译结果\n");
                continue;
            }
            if (!r.ok()) {
                sb.append("✗ 语义错误：").append(r.error().getMessage()).append('\n');
                continue;
            }
            if (!r.hasPlan()) {
                sb.append("✓ CREATE · 表已登记\n");
                continue;
            }
            int steps = r.stepNames().size();
            sb.append("✓ ").append(kindOf(e.statement()))
                    .append(steps == 0 ? " · 计划无改写" : " · 优化改写 " + steps + " 步")
                    .append('\n');
        }
        sb.append("────────────────────────────\n");
        sb.append("会话已建表：").append(tables.isEmpty() ? "（无）" : String.join(", ", tables)).append('\n');
        sb.append("提示：同一会话内先 CREATE 的表现在后序语句可见；点「重置会话」可清空。\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 语句 → 中文类型名（摘要行用） */
    private static String kindOf(Statement stmt) {
        if (stmt instanceof CreateTableStmt) {
            return "CREATE TABLE";
        }
        if (stmt instanceof InsertStmt) {
            return "INSERT";
        }
        if (stmt instanceof SelectStmt) {
            return "SELECT";
        }
        if (stmt instanceof DeleteStmt) {
            return "DELETE";
        }
        return stmt.getClass().getSimpleName();
    }

    private static String indent(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            sb.append("  ").append(line).append('\n');
        }
        return sb.toString();
    }
}
