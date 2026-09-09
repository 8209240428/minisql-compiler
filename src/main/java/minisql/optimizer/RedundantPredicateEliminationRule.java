package minisql.optimizer;

import minisql.expr.Expr;
import minisql.plan.DeleteNode;
import minisql.plan.FilterNode;
import minisql.plan.PlanNode;
import minisql.plan.ProjectNode;
import minisql.plan.ScanNode;

/**
 * R3 冗余谓词消除：在 AND/OR 列表中去掉重复的相同谓词、恒真/恒假项。
 *
 * <p>典型效果：{@code (a > 1) AND (a > 1) → (a > 1)}。
 * 若整个条件化简为 TRUE/FALSE 常量，交由上层（R2/R4 或 Scan 清理）处理。
 */
public final class RedundantPredicateEliminationRule implements OptimizationRule {

    @Override
    public String name() {
        return "R3 冗余谓词消除";
    }

    @Override
    public PlanNode apply(PlanNode plan) {
        return rewrite(plan);
    }

    private PlanNode rewrite(PlanNode node) {
        if (node instanceof ScanNode s) {
            if (s.filter() == null) {
                return s;
            }
            Expr cleaned = ExprOps.dedupe(s.filter());
            if (cleaned instanceof Expr.Bool b) {
                if (b.value()) {
                    return new ScanNode(s.table(), s.columns(), null); // 恒真 → 无条件扫描
                }
                return new ScanNode(s.table(), s.columns(), Expr.Bool.FALSE); // 恒假 → 空结果
            }
            // 替换式更新过滤（withFilter 是“合并”语义，不能用于此处）
            return new ScanNode(s.table(), s.columns(), cleaned);
        }
        if (node instanceof FilterNode f) {
            PlanNode child = rewrite(f.child());
            Expr cleaned = ExprOps.dedupe(f.predicate());
            if (cleaned instanceof Expr.Bool b && b.value()) {
                return child; // 恒真 → 去掉过滤
            }
            return new FilterNode(child, cleaned);
        }
        if (node instanceof ProjectNode p) {
            return new ProjectNode(rewrite(p.child()), p.outputs());
        }
        if (node instanceof DeleteNode d) {
            return new DeleteNode(d.table(), rewrite(d.child()));
        }
        return node;
    }
}
