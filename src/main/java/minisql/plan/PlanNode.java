package minisql.plan;

/**
 * 逻辑执行计划节点（sealed 层级）。
 *
 * <p>C 的执行计划与真实关系代数对应关系：
 * <ul>
 *   <li>{@link ScanNode}     —— 扫描某张表（可携带下推后的过滤条件，见优化规则“谓词下推”）</li>
 *   <li>{@link FilterNode}   —— 过滤算子（优化器会把它下压进 Scan，见 R4）</li>
 *   <li>{@link ProjectNode}  —— 投影算子（决定最终输出哪些列）</li>
 *   <li>{@link InsertNode}   —— 插入算子（叶子）</li>
 *   <li>{@link DeleteNode}   —— 删除算子（子计划为待删除行集的扫描）</li>
 * </ul>
 */
public sealed interface PlanNode
        permits ScanNode, FilterNode, ProjectNode, InsertNode, DeleteNode {
}
