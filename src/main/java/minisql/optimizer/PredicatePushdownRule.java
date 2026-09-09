package minisql.optimizer;

import minisql.plan.DeleteNode;
import minisql.plan.FilterNode;
import minisql.plan.PlanNode;
import minisql.plan.ProjectNode;
import minisql.plan.ScanNode;

/**
 * R4 谓词下推：把 Filter 节点里的条件下压进其子 Scan，成为 Scan 的内部过滤，
 * 从而删除独立的 Filter 算子（过滤在执行时越早做、扫到的中间数据越少）。
 *
 * <pre>
 * 优化前:  Filter (age &gt; 18)
 *            └─ Scan student
 * 优化后:  Scan student filter: (age &gt; 18)
 * </pre>
 */
public final class PredicatePushdownRule implements OptimizationRule {

    @Override
    public String name() {
        return "R4 谓词下推";
    }

    @Override
    public PlanNode apply(PlanNode plan) {
        return rewrite(plan);
    }

    private PlanNode rewrite(PlanNode node) {
        if (node instanceof FilterNode f) {
            // 先处理子计划，保证子计划里已经没有 Filter
            PlanNode child = rewrite(f.child());
            if (child instanceof ScanNode scan) {
                // 过滤下压进 Scan：withFilter 内部会把已有 filter 与新条件 AND 合并
                return scan.withFilter(f.predicate());
            }
            // 子计划不是 Scan（理论不会出现），保守地保留 Filter
            return new FilterNode(child, f.predicate());
        }
        if (node instanceof ProjectNode p) {
            return new ProjectNode(rewrite(p.child()), p.outputs());
        }
        if (node instanceof DeleteNode d) {
            return new DeleteNode(d.table(), rewrite(d.child()));
        }
        // Scan（filter 已在内部） / Insert（叶子）
        return node;
    }
}
