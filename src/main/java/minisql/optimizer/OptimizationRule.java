package minisql.optimizer;

import minisql.plan.PlanNode;

/**
 * 单条逻辑优化规则：输入一棵计划树，输出重写后的计划树。
 * 约定：没有可优化点时返回与输入“打印等价”的计划（是否变化由 Optimizer 比较打印文本判定）。
 */
public interface OptimizationRule {
    /** 规则名（用于演示“逐步优化”说明） */
    String name();

    /** 对整棵计划树应用本规则 */
    PlanNode apply(PlanNode plan);
}
