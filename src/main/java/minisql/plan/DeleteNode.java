package minisql.plan;

import minisql.catalog.TableMeta;

/**
 * 删除算子：子计划扫描并过滤出待删除的行。child 通常是 ScanNode（或优化前的 Filter→Scan）。
 */
public record DeleteNode(TableMeta table, PlanNode child) implements PlanNode {
}
