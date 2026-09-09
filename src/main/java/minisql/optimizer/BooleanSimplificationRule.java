package minisql.optimizer;

import minisql.expr.Expr;
import minisql.plan.DeleteNode;
import minisql.plan.FilterNode;
import minisql.plan.PlanNode;
import minisql.plan.ProjectNode;
import minisql.plan.ScanNode;

/**
 * R2 布尔化简：TRUE/FALSE 的恒等/吸收、双重 NOT、{@code x AND NOT x}、{@code x OR NOT x}。
 *
 * <p>特别地，当整个过滤条件被化简成常量 TRUE 时，说明该过滤恒成立，直接删掉 Filter 节点；
 * 化简成 FALSE 的过滤则保留（下压进 Scan 后表示“空结果”）。
 */
public final class BooleanSimplificationRule implements OptimizationRule {

    @Override
    public String name() {
        return "R2 布尔化简";
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
            Expr simplified = ExprOps.simplify(s.filter());
            if (simplified instanceof Expr.Bool b) {
                if (b.value()) {
                    // 扫描内过滤恒真 → 相当于无条件扫描，清掉 filter
                    return new ScanNode(s.table(), s.columns(), null);
                }
                // 恒假 → 保留 FALSE 作为“空结果”标记
                return new ScanNode(s.table(), s.columns(), Expr.Bool.FALSE);
            }
            // 替换式更新过滤（withFilter 是“合并”语义，不能用于此处）
            return new ScanNode(s.table(), s.columns(), simplified);
        }
        if (node instanceof FilterNode f) {
            PlanNode child = rewrite(f.child());
            Expr simplified = ExprOps.simplify(f.predicate());
            if (simplified instanceof Expr.Bool b && b.value()) {
                // 条件恒真 → 过滤节点无意义，直接去掉
                return child;
            }
            return new FilterNode(child, simplified);
        }
        if (node instanceof ProjectNode p) {
            return new ProjectNode(rewrite(p.child()), p.outputs());
        }
        if (node instanceof DeleteNode d) {
            return new DeleteNode(d.table(), rewrite(d.child()));
        }
        return node; // Scan 已处理 / Insert 叶子
    }
}
