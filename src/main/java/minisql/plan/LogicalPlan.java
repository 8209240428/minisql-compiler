package minisql.plan;

import minisql.catalog.ColumnMeta;
import minisql.expr.ExprPrinter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 逻辑执行计划：包装一棵计划树，并提供缩进文本打印（答辩/测试用“优化前后对比”）。
 *
 * <p>示例输出：
 * <pre>
 * Project [id]
 *   Scan student cols=[id, age] filter: (age &gt; 18)
 * </pre>
 */
public final class LogicalPlan {
    private final PlanNode root;

    public LogicalPlan(PlanNode root) {
        this.root = root;
    }

    public PlanNode root() {
        return root;
    }

    /** 打印整棵计划树 */
    public String print() {
        return print(root);
    }

    /** 静态打印入口（优化器内部也用它做“是否发生变化”的比较） */
    public static String print(PlanNode node) {
        StringBuilder sb = new StringBuilder();
        render(node, 0, sb);
        return sb.toString();
    }

    /** 递归渲染：节点行 + 子节点行 */
    private static void render(PlanNode node, int depth, StringBuilder sb) {
        String pad = "  ".repeat(depth);
        if (node instanceof ScanNode scan) {
            sb.append(pad).append("Scan ").append(scan.table().name())
                    .append(" cols=[").append(names(scan.columns())).append(']');
            if (scan.filter() != null) {
                sb.append(" filter: ").append(ExprPrinter.render(scan.filter()));
            }
        } else if (node instanceof FilterNode filter) {
            sb.append(pad).append("Filter ").append(ExprPrinter.render(filter.predicate()));
        } else if (node instanceof ProjectNode proj) {
            sb.append(pad).append("Project [").append(names(proj.outputs())).append(']');
        } else if (node instanceof InsertNode ins) {
            sb.append(pad).append("Insert into ").append(ins.table().name())
                    .append(" values ").append(ins.values().stream()
                    .map(ExprPrinter::render).collect(Collectors.joining(", ")));
        } else if (node instanceof DeleteNode del) {
            sb.append(pad).append("Delete from ").append(del.table().name());
        }

        // 追加子节点（Scan / Insert 为叶子）
        PlanNode child = childOf(node);
        if (child != null) {
            sb.append('\n');
            render(child, depth + 1, sb);
        }
    }

    private static PlanNode childOf(PlanNode node) {
        if (node instanceof FilterNode f) {
            return f.child();
        }
        if (node instanceof ProjectNode p) {
            return p.child();
        }
        if (node instanceof DeleteNode d) {
            return d.child();
        }
        return null; // Scan / Insert
    }

    private static String names(List<ColumnMeta> columns) {
        return columns.stream().map(ColumnMeta::name).collect(Collectors.joining(", "));
    }

    @Override
    public String toString() {
        return print();
    }
}
