package minisql.exec;

/**
 * 物理记录标识：某条记录在哪个页（pageId）的哪个槽（slot）。
 *
 * <p>DELETE 需要先扫描取得待删行的 Rid，再据此删除，避免边扫边删导致偏移失效。
 */
public record Rid(int pageId, int slot) {
}
