package minisql.semantic;

/**
 * CREATE TABLE 的语义结果：表结构已在分析时写入 {@link minisql.catalog.Catalog}，
 * 该结果仅用于告知上层“建表成功”，不参与逻辑计划生成。
 */
public record AnalyzedCreate(String tableName) implements Analyzed {
}
