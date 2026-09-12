package minisql.plan;

import minisql.catalog.TableMeta;
import minisql.semantic.Analyzed;
import minisql.semantic.AnalyzedDelete;
import minisql.semantic.AnalyzedInsert;
import minisql.semantic.AnalyzedSelect;

import java.util.ArrayList;
import java.util.List;

/**
 * 由“语义分析产物 Analyzed*”构造<b>未优化</b>的逻辑执行计划。
 *
 * <ul>
 *   <li>SELECT → Project( [Filter(Scan) | Scan] )，星号在语义层已展开</li>
 *   <li>DELETE → DeleteNode 包一棵 Filter(Scan) 子计划</li>
 *   <li>INSERT → InsertNode 叶子</li>
 *   <li>CREATE 不生成计划（语义已把表登记进 Catalog）</li>
 * </ul>
 */
public final class PlanBuilder {

    private PlanBuilder() {
    }

    /**
     * 生成初始（未优化）计划。
     *
     * @throws IllegalStateException 若传入的是 CREATE 产物（它不产生计划）
     */
    public static LogicalPlan build(Analyzed analyzed) {
        if (analyzed instanceof AnalyzedSelect sel) {
            return buildSelect(sel);
        }
        if (analyzed instanceof AnalyzedDelete del) {
            return buildDelete(del);
        }
        if (analyzed instanceof AnalyzedInsert ins) {
            return new LogicalPlan(new InsertNode(ins.table(), ins.columns(), ins.values()));
        }
        throw new IllegalStateException("CREATE TABLE 不生成逻辑计划");
    }

    /** SELECT：Project [输出列] ← [Filter(WHERE) ←] Scan(表, 全列) */
    private static LogicalPlan buildSelect(AnalyzedSelect sel) {
        TableMeta table = sel.table();
        // 初始 Scan 先读全列，优化规则 R5 再裁剪
        PlanNode child = new ScanNode(table, new ArrayList<>(table.columns()), null);
        if (sel.where() != null) {
            child = new FilterNode(child, sel.where());
        }
        PlanNode root = new ProjectNode(child, sel.outputColumns());
        return new LogicalPlan(root);
    }

    /** DELETE：DeleteNode(表) ← [Filter(WHERE) ←] Scan(表, 全列) */
    private static LogicalPlan buildDelete(AnalyzedDelete del) {
        TableMeta table = del.table();
        PlanNode child = new ScanNode(table, new ArrayList<>(table.columns()), null);
        if (del.where() != null) {
            child = new FilterNode(child, del.where());
        }
        return new LogicalPlan(new DeleteNode(table, child));
    }
}
