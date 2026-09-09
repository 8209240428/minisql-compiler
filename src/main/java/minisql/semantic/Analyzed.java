package minisql.semantic;

/**
 * 语义分析产物（已绑定 Catalog 与类型检查的中间表示）。
 *
 * <p>语义分析消费 B 的 AST，产出这套结构；执行计划 PlanBuilder 再据此生成逻辑计划。
 * 各实现的职责：
 * <ul>
 *   <li>{@link AnalyzedCreate}：建表（副作用已写入 Catalog，不生成计划）</li>
 *   <li>{@link AnalyzedInsert}：插入（目标列 + 已类型检查的值）</li>
 *   <li>{@link AnalyzedSelect}：查询（输出列已按 * 展开、WHERE 已绑定为 IR）</li>
 *   <li>{@link AnalyzedDelete}：删除（WHERE 已绑定为 IR）</li>
 * </ul>
 */
public sealed interface Analyzed
        permits AnalyzedCreate, AnalyzedInsert, AnalyzedSelect, AnalyzedDelete {
}
