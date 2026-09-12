package minisql.optimizer;

import minisql.expr.Expr;
import minisql.plan.DeleteNode;
import minisql.plan.FilterNode;
import minisql.plan.InsertNode;
import minisql.plan.PlanNode;
import minisql.plan.ProjectNode;
import minisql.plan.ScanNode;

import java.util.ArrayList;
import java.util.List;

/**
 * R1 常量折叠：把纯常量子表达式在编译期算出来。
 *
 * <p>典型效果：
 * <ul>
 *   <li>{@code age > 10 + 8} → {@code age > 18}</li>
 *   <li>{@code 1 = 1} → {@code TRUE}</li>
 *   <li>INSERT 值里的 {@code 1 + 2} → {@code 3}</li>
 * </ul>
 */
public final class ConstantFoldingRule implements OptimizationRule {

    @Override
    public String name() {
        return "R1 常量折叠";
    }

    @Override
    public PlanNode apply(PlanNode plan) {
        return rewrite(plan);
    }

    private PlanNode rewrite(PlanNode node) {
        if (node instanceof ScanNode s) {
            if (s.filter() != null) {
                // 注意：这是“替换”过滤条件，不能复用 withFilter(合并语义)
                return new ScanNode(s.table(), s.columns(), ExprOps.fold(s.filter()));
            }
            return s;
        }
        if (node instanceof FilterNode f) {
            PlanNode child = rewrite(f.child());
            return new FilterNode(child, ExprOps.fold(f.predicate()));
        }
        if (node instanceof ProjectNode p) {
            return new ProjectNode(rewrite(p.child()), p.outputs());
        }
        if (node instanceof DeleteNode d) {
            return new DeleteNode(d.table(), rewrite(d.child()));
        }
        if (node instanceof InsertNode i) {
            List<Expr> folded = new ArrayList<>();
            for (Expr v : i.values()) {
                folded.add(ExprOps.fold(v));
            }
            return new InsertNode(i.table(), i.columns(), folded);
        }
        return node;
    }
}
