package minisql.plan;

import minisql.catalog.ColumnMeta;

import java.util.List;

/**
 * 投影算子：只输出指定的列。SELECT 语句的最终结果集由它决定。
 */
public record ProjectNode(PlanNode child, List<ColumnMeta> outputs) implements PlanNode {
    public ProjectNode {
        outputs = List.copyOf(outputs);
    }
}
