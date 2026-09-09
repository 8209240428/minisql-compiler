package minisql.optimizer;

import minisql.plan.LogicalPlan;

import java.util.List;

/**
 * 优化结果：优化前计划、优化后计划，以及每次规则改写后的中间快照（答辩“逐步优化”用）。
 */
public record OptimizeResult(
        LogicalPlan before,
        LogicalPlan after,
        List<LogicalPlan> steps,
        List<String> stepNames
) {
    public OptimizeResult {
        steps = List.copyOf(steps);
        stepNames = List.copyOf(stepNames);
    }

    /** 是否产生过任何改写 */
    public boolean changed() {
        return !steps.isEmpty();
    }
}
