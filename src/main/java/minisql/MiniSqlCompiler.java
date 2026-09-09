package minisql;

import minisql.ast.Statement;
import minisql.catalog.Catalog;
import minisql.optimizer.OptimizeResult;
import minisql.optimizer.Optimizer;
import minisql.plan.LogicalPlan;
import minisql.plan.PlanBuilder;
import minisql.semantic.Analyzed;
import minisql.semantic.AnalyzedCreate;
import minisql.semantic.SemanticAnalyzer;
import minisql.semantic.SemanticException;

import java.util.ArrayList;
import java.util.List;

/**
 * C 部分门面：串起「B 的 AST → C 的语义分析 → 计划生成 → 优化」。
 *
 * <p>持有一个有状态的 {@link Catalog}：同一实例内，先执行 CREATE 再执行
 * INSERT/SELECT/DELETE 时，后者能看到前者登记的表。用法：
 * <pre>
 * MiniSqlCompiler c = new MiniSqlCompiler();
 * List&lt;CompileResult&gt; rs = c.compileAll(
 *     "CREATE TABLE student(id INT, name VARCHAR, age INT);" +
 *     "SELECT id FROM student WHERE age &gt; 18;");
 * </pre>
 */
public final class MiniSqlCompiler {

    private final Catalog catalog = new Catalog();

    /** 暴露 Catalog，便于测试/演示预置或查看会话状态 */
    public Catalog catalog() {
        return catalog;
    }

    /** 编译一条语句（AST 来自 B 的 MiniSqlFrontend） */
    public CompileResult compile(Statement statement) {
        // 每次新建分析器，但共享同一个 Catalog（保证跨语句的会话状态）
        SemanticAnalyzer analyzer = new SemanticAnalyzer(catalog);
        try {
            Analyzed analyzed = analyzer.analyze(statement);
            if (analyzed instanceof AnalyzedCreate) {
                return CompileResult.create(statement, analyzed);
            }
            LogicalPlan before = PlanBuilder.build(analyzed);
            OptimizeResult opt = Optimizer.optimize(before);
            return CompileResult.planned(statement, analyzed, before, opt);
        } catch (SemanticException ex) {
            // 语义错误不抛出，封装成结果返回（便于演示 / 测试断言）
            return CompileResult.failed(statement, ex);
        }
    }

    /**
     * 编译整段 SQL 脚本（按 ; 拆分，逐条编译，Catalog 状态累积）。
     * 语法/词法错误会由 B 的 MiniSqlFrontend.parseAll 抛出，语义错误则封装在结果里。
     */
    public List<CompileResult> compileAll(String sql) {
        List<CompileResult> results = new ArrayList<>();
        for (Statement statement : MiniSqlFrontend.parseAll(sql)) {
            results.add(compile(statement));
        }
        return results;
    }
}
