package minisql.storage;

/**
 * 槽页（Slotted Page）格式的低层操作：在一个 {@link Page} 上维护「槽目录 + 变长记录」。
 *
 * <p>页头 12 字节，槽目录从 {@link #HEADER_SIZE} 向上增长，记录从页尾向下增长。
 * 删除只打墓碑（recordOffset = -1），不做压缩——压缩会破坏扫描过程中已取得的其它 Rid。
 *
 * <pre>
 * 页头（12B）: pageType(1) reserved(1) slotCount(2) freeSpaceOffset(2) freeSpaceEnd(2) nextPageId(4)
 * 槽条目（6B）: recordOffset(int) + recordLength(short)
 * </pre>
 */
public final class SlottedPage {

    public static final int PAGE_TYPE_OFF = 0;
    public static final int SLOT_COUNT_OFF = 2;
    public static final int FREE_START_OFF = 4;
    public static final int FREE_END_OFF = 6;
    public static final int NEXT_PAGE_OFF = 8;
    public static final int HEADER_SIZE = 12;
    public static final int SLOT_SIZE = 6;
    public static final int DELETED = -1;

    private SlottedPage() {
    }

    /** 初始化页头：清零、写页类型、槽目录起点与空闲区 */
    public static void init(Page page, PageType type) {
        page.writeByte(PAGE_TYPE_OFF, type.code());
        page.writeByte(PAGE_TYPE_OFF + 1, 0);
        page.writeShort(SLOT_COUNT_OFF, 0);
        page.writeShort(FREE_START_OFF, HEADER_SIZE);
        page.writeShort(FREE_END_OFF, Page.PAGE_SIZE);
        page.writeInt(NEXT_PAGE_OFF, 0);
    }

    public static int slotCount(Page page) {
        return page.readShort(SLOT_COUNT_OFF);
    }

    /** 是否有足够空间放一条长度为 recordLen 的记录（还需额外一个槽条目） */
    public static boolean hasFree(Page page, int recordLen) {
        int freeStart = page.readShort(FREE_START_OFF);
        int freeEnd = page.readShort(FREE_END_OFF);
        return (freeEnd - freeStart) >= recordLen + SLOT_SIZE;
    }

    /**
     * 插入一条记录，返回槽号。空间不足抛异常（上层应先 {@link #hasFree} 判断）。
     */
    public static int insert(Page page, byte[] record) {
        if (record.length > Page.PAGE_SIZE - HEADER_SIZE - SLOT_SIZE) {
            throw new IllegalArgumentException("记录过长，无法放入单页: " + record.length);
        }
        if (!hasFree(page, record.length)) {
            throw new IllegalStateException("页无可用空间（" + page.id() + "）");
        }
        int slot = slotCount(page);
        int freeStart = page.readShort(FREE_START_OFF);
        int freeEnd = page.readShort(FREE_END_OFF);

        int recordOffset = freeEnd - record.length;
        page.writeBytes(recordOffset, record, record.length);

        int slotOff = slotOffset(slot);
        page.writeInt(slotOff, recordOffset);
        page.writeShort(slotOff + 4, record.length);

        page.writeShort(SLOT_COUNT_OFF, slot + 1);
        page.writeShort(FREE_START_OFF, freeStart + SLOT_SIZE);
        page.writeShort(FREE_END_OFF, recordOffset);
        return slot;
    }

    /** 读取某槽的记录；已删除（墓碑）返回 null */
    public static byte[] read(Page page, int slot) {
        int slotOff = slotOffset(slot);
        int recordOffset = page.readInt(slotOff);
        if (recordOffset == DELETED) {
            return null;
        }
        int len = page.readShort(slotOff + 4);
        byte[] out = new byte[len];
        page.readBytes(recordOffset, out, len);
        return out;
    }

    /** 删除某槽：打墓碑，不回收空间 */
    public static void delete(Page page, int slot) {
        page.writeInt(slotOffset(slot), DELETED);
    }

    /** 下一页页号（0 表示链尾） */
    public static int nextPage(Page page) {
        return page.readInt(NEXT_PAGE_OFF);
    }

    public static void setNextPage(Page page, int nextPageId) {
        page.writeInt(NEXT_PAGE_OFF, nextPageId);
    }

    private static int slotOffset(int slot) {
        return HEADER_SIZE + slot * SLOT_SIZE;
    }
}
