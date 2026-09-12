package minisql;

import minisql.ast.Statement;
import minisql.lexer.Token;
import minisql.parser.AstPrinter;
import minisql.plan.LogicalPlan;

import java.util.List;
import java.util.stream.Collectors;

/**
 * C 部分端到端演示主程序（答辩演示用）。
 *
 * <p>运行：{@code mvn -q exec:java -Dexec.mainClass=minisql.Demo} 或 IDE 直接 Run。
 * 三段演示：
 * <ul>
 *   <li>A 全流程：建表 → 插入 → 查询 → 删除（Token / AST / 优化前后计划）</li>
 *   <li>B 优化对比：{@code WHERE 1=1 AND age>10+8} → 只下压 {@code age>18} 并裁剪列</li>
 *   <li>C 语义错误：未定义列、类型不匹配、WHERE 非布尔等，展示行列号报错</li>
 * </ul>
 */
public final class Demo {

    private Demo() {
    }

    public static void main(String[] args) {
        System.out.println("========== 演示 A：合法 SQL 全流程（Token / AST / 计划） ==========");
        runSection(sectionA());
        System.out.println();
        System.out.println("========== 演示 B：优化前后对比（WHERE 1=1 AND age>10+8） ==========");
        runSection(sectionB());
        System.out.println();
        System.out.println("========== 演示 C：非法 SQL 的语义错误提示 ==========");
        runSection(sectionC());
    }

    /** 依次执行一段脚本（共享同一个 MiniSqlCompiler 以保留 Catalog 状态） */
    private static void runSection(String[] script) {
        MiniSqlCompiler compiler = new MiniSqlCompiler();
        for (String sql : script) {
            System.out.println("--------------------------------------------------");
            System.out.println("SQL: " + sql);
            try {
                // Token 流（复用 A 的 Lexer）
                List<Token> tokens = MiniSqlFrontend.tokenize(sql);
                System.out.println("Token: " + tokens.stream()
                        .map(Demo::tokenBrief).collect(Collectors.joining("  ")));

                Statement stmt = MiniSqlFrontend.parse(sql);
                System.out.println("AST:");
                System.out.println(new AstPrinter().print(stmt));

                CompileResult r = compiler.compile(stmt);
                if (!r.ok()) {
                    System.out.println(">> " + r.error().getMessage());
                    continue;
                }
                if (!r.hasPlan()) {
                    System.out.println(">> 建表成功（已登记进 Catalog，当前表: "
                            + compiler.catalog().tables().stream()
                            .map(t -> t.name()).collect(Collectors.joining(", ")) + "）");
                    continue;
                }
                System.out.println("计划(优化前):");
                System.out.println(indent(r.before().print()));
                if (!r.stepNames().isEmpty()) {
                    for (int i = 0; i < r.stepNames().size(); i++) {
                        System.out.println("  -- 优化步骤 " + (i + 1) + " (" + r.stepNames().get(i) + ") --");
                        LogicalPlan step = r.steps().get(i);
                        System.out.println(indent(step.print()));
                    }
                }
                if (!r.before().print().equals(r.after().print())) {
                    System.out.println("计划(优化后):");
                    System.out.println(indent(r.after().print()));
                }
            } catch (RuntimeException ex) {
                // 词法/语法错误由 A/B 抛出，原样打印
                System.out.println(">> " + ex.getMessage());
            }
        }
    }

    /** 演示 A：四类语句各一条 */
    private static String[] sectionA() {
        return new String[]{
                "CREATE TABLE student(id INT, name VARCHAR, age INT, score INT);",
                "INSERT INTO student(id, name, age, score) VALUES (1, 'Alice', 20, 90);",
                "SELECT id, name FROM student WHERE age >= 20 AND score > 80;",
                "DELETE FROM student WHERE id = 1;"
        };
    }

    /** 演示 B：常量折叠 → 布尔化简 → 冗余消除 → 谓词下推 → 投影裁剪 */
    private static String[] sectionB() {
        return new String[]{
                "CREATE TABLE student(id INT, name VARCHAR, age INT);",
                "SELECT id, name FROM student WHERE 1 = 1 AND age > 10 + 8;",
                "SELECT name FROM student WHERE age > 20 AND age > 20 AND age > 5;"
        };
    }

    /** 演示 C：各类语义错误（都先建好表） */
    private static String[] sectionC() {
        return new String[]{
                "CREATE TABLE student(id INT, name VARCHAR, age INT);",
                "SELECT id FROM nosuch_table;",                            // 表不存在
                "SELECT no_such_col FROM student;",                        // 列不存在
                "SELECT * FROM student WHERE name > 'a';",                 // VARCHAR 大小比较
                "SELECT * FROM student WHERE age >= '18';",                // 类型不匹配
                "SELECT * FROM student WHERE age;",                        // WHERE 非布尔
                "INSERT INTO student(id, name, age) VALUES (1, 20, 'x');", // 值类型不匹配
                "DELETE FROM student WHERE age;",                          // DELETE WHERE 非布尔
                "CREATE TABLE student(id INT, other INT);"                 // 表已存在
        };
    }

    private static String tokenBrief(Token t) {
        String v = t.value();
        return t.type().name() + (v == null || v.isEmpty() ? "" : "(" + v + ")");
    }

    private static String indent(String text) {
        return text.lines().map(l -> "    " + l).collect(Collectors.joining("\n"));
    }
}
