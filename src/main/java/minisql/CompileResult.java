package minisql;

import minisql.optimizer.OptimizeResult;
import minisql.plan.LogicalPlan;
import minisql.semantic.Analyzed;
import minisql.semantic.SemanticException;

import java.util.List;

/**
 * 一条语句的编译结果：语义产物 + 优化前后逻辑计划（或语义错误）。
 *
 * <p>供 {@link MiniSqlCompiler} 返回给调用方/测试/演示使用。
 */
public final class CompileResult {
    private final minisql.ast.Statement statement;
    private final Analyzed analyzed;          // 语义成功时为非 null
    private final LogicalPlan before;         // CREATE 或语义失败时为 null
    private final LogicalPlan after;
    private final List<LogicalPlan> steps;    // 优化逐步快照（与 stepNames 对齐）
    private final List<String> stepNames;
    private final SemanticException error;    // 语义失败时非 null

    CompileResult(minisql.ast.Statement statement,
                  Analyzed analyzed,
                  LogicalPlan before,
                  LogicalPlan after,
                  List<LogicalPlan> steps,
                  List<String> stepNames,
                  SemanticException error) {
        this.statement = statement;
        this.analyzed = analyzed;
        this.before = before;
        this.after = after;
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.stepNames = stepNames == null ? List.of() : List.copyOf(stepNames);
        this.error = error;
    }

    /** 语义检查是否通过 */
    public boolean ok() {
        return error == null;
    }

    /** 是否生成了逻辑计划（CREATE 没有） */
    public boolean hasPlan() {
        return before != null;
    }

    public minisql.ast.Statement statement() {
        return statement;
    }

    public Analyzed analyzed() {
        return analyzed;
    }

    public LogicalPlan before() {
        return before;
    }

    public LogicalPlan after() {
        return after;
    }

    public List<LogicalPlan> steps() {
        return steps;
    }

    public List<String> stepNames() {
        return stepNames;
    }

    public SemanticException error() {
        return error;
    }

    /** 语义失败结果 */
    static CompileResult failed(minisql.ast.Statement stmt, SemanticException error) {
        return new CompileResult(stmt, null, null, null, null, null, error);
    }

    /** CREATE：仅语义（登记 Catalog），无计划 */
    static CompileResult create(minisql.ast.Statement stmt, Analyzed analyzed) {
        return new CompileResult(stmt, analyzed, null, null, null, null, null);
    }

    /** 其它语句：语义 + 优化前后计划 */
    static CompileResult planned(minisql.ast.Statement stmt, Analyzed analyzed,
                                 LogicalPlan before, OptimizeResult opt) {
        return new CompileResult(stmt, analyzed, before, opt.after(),
                opt.steps(), opt.stepNames(), null);
    }
}
