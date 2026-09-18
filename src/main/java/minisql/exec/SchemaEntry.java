package minisql.exec;

import minisql.catalog.TableMeta;

/**
 * 目录里的一条表记录：表结构 + 该表数据页链的根页号。
 * 由 {@link SchemaCodec} 编解码，{@link CatalogStore} 持久化到目录页。
 */
public record SchemaEntry(TableMeta table, int dataRootPage) {
}
