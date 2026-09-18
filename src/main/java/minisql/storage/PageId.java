package minisql.storage;

/**
 * 页号值对象（0 = 文件头页）。
 *
 * <p>存储层内部用页号定位每一页；页号从 0 开始，第 0 页恒为文件头页。
 */
public record PageId(int id) {

    /** 文件头页的固定页号 */
    public static final PageId ZERO = new PageId(0);

    public static PageId of(int id) {
        return new PageId(id);
    }
}
