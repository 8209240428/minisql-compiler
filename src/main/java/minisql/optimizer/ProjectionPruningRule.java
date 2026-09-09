package minisql.optimizer;

import minisql.catalog.ColumnMeta;
import minisql.expr.ExprUtil;
import minisql.plan.DeleteNode;
import minisql.plan.PlanNode;
import minisql.plan.ProjectNode;
import minisql.plan.ScanNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * R5 投影裁剪 / 投影下推：Scan 只需读取“最终被用到”的列。
 *
 * <p>被用到的列 = Project 输出列 ∪ Scan 过滤条件里引用的列；
 * SELECT 里的临时过滤列（如只用于 WHERE 的 age）不进入最终结果，但 Scan 仍须读出供过滤使用。
 * 裁剪后 Scan 更接近“只扫需要的列”，也体现“把投影所需列下推到扫描”。
 */
public final class ProjectionPruningRule implements OptimizationRule {

    @Override
    public String name() {
        return "R5 投影裁剪";
    }

    @Override
    public PlanNode apply(PlanNode plan) {
        return prune(plan);
    }

    /** 自顶向下/自底向上结合：Project 决定输出列 → 反过来裁剪它下面那个 Scan 的列 */
    private PlanNode prune(PlanNode node) {
        if (node instanceof ProjectNode p) {
            PlanNode child = p.child();
            if (child instanceof ScanNode scan) {
                List<ColumnMeta> needed = neededColumns(scan, p.outputs());
                ScanNode pruned = new ScanNode(scan.table(), needed, scan.filter());
                return new ProjectNode(pruned, p.outputs());
            }
            // 子计划不是 Scan（理论不会出现）时，保守地原样传递
            return new ProjectNode(child, p.outputs());
        }
        if (node instanceof DeleteNode d) {
            PlanNode child = d.child();
            if (child instanceof ScanNode scan) {
                // DELETE 不投影：Scan 只需读过滤条件引用的列；无条件删除则回退为全列
                List<ColumnMeta> needed;
                if (scan.filter() == null) {
                    needed = new java.util.ArrayList<>(scan.table().columns());
                } else {
                    needed = neededColumns(scan, List.of());
                }
                ScanNode pruned = new ScanNode(scan.table(), needed, scan.filter());
                return new DeleteNode(d.table(), pruned);
            }
            return new DeleteNode(d.table(), child);
        }
        return node;
    }

    /** 计算 Scan 真正需要读取的列（保持表定义顺序） */
    private static List<ColumnMeta> neededColumns(ScanNode scan, List<ColumnMeta> outputs) {
        Set<ColumnMeta> need = new LinkedHashSet<>();
        need.addAll(outputs);                                    // 投影输出列
        need.addAll(ExprUtil.referencedColumns(scan.filter()));  // 过滤条件引用的列
        return scan.table().keepInOrder(need);
    }
}
