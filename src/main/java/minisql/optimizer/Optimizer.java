package minisql.optimizer;

import minisql.plan.LogicalPlan;
import minisql.plan.PlanNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 优化器调度器：按固定顺序套用 5 条规则，直到一轮没有变化（或达到轮数上限）。
 *
 * <p>每应用成功一条规则都会记录一步快照，供演示“优化前 → 每步 → 优化后”。
 * 是否有变化 = 计划树打印文本是否不同（简单、稳定、适合答辩打印对比）。
 */
public final class Optimizer {

    /** 5 条规则固定顺序。谓词下推须在裁剪前，裁剪才能看到并入 Scan 的过滤条件。 */
    private static final List<OptimizationRule> RULES = List.of(
            new ConstantFoldingRule(),              // R1
            new BooleanSimplificationRule(),        // R2
            new RedundantPredicateEliminationRule(),// R3
            new PredicatePushdownRule(),            // R4
            new ProjectionPruningRule()             // R5
    );

    /** 轮数上限：防止规则间来回改写死循环（幂等规则通常 1~2 轮即收敛） */
    private static final int MAX_PASS = 8;

    private Optimizer() {
    }

    /** 对计划整体做优化，返回前后对比与逐步快照 */
    public static OptimizeResult optimize(LogicalPlan before) {
        PlanNode root = before.root();
        List<LogicalPlan> steps = new ArrayList<>();
        List<String> stepNames = new ArrayList<>();

        for (int pass = 1; pass <= MAX_PASS; pass++) {
            boolean changedInPass = false;
            for (OptimizationRule rule : RULES) {
                PlanNode next = rule.apply(root);
                if (!LogicalPlan.print(next).equals(LogicalPlan.print(root))) {
                    root = next;
                    changedInPass = true;
                    steps.add(new LogicalPlan(root));
                    stepNames.add("第 " + pass + " 轮 · " + rule.name());
                }
            }
            if (!changedInPass) {
                break; // 本轮没有任何改写，已收敛
            }
        }
        return new OptimizeResult(before, new LogicalPlan(root), steps, stepNames);
    }
}
