package minisql.storage;

/**
 * 页类型：文件头页 / 数据堆页 / 目录页。
 *
 * <p>槽页（HEAP、CATALOG）在页头首字节记录自己的类型；
 * 文件头页（HEADER）是第 0 页，不参与槽页格式。
 */
public enum PageType {
    HEADER(0),
    HEAP(1),
    CATALOG(2);

    private final int code;

    PageType(int code) {
        this.code = code;
    }

    /** 落盘的 1 字节编码 */
    public int code() {
        return code;
    }

    public static PageType from(int code) {
        return switch (code) {
            case 0 -> HEADER;
            case 1 -> HEAP;
            case 2 -> CATALOG;
            default -> throw new IllegalArgumentException("未知页类型: " + code);
        };
    }
}
