package minisql.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 槽页（Slotted Page）低层格式测试：初始化 / 插入 / 读取 / 删除墓碑 / 页链。
 */
class SlottedPageTest {

    private static Page heapPage(int id) {
        Page p = Page.newEmpty(new PageId(id));
        SlottedPage.init(p, PageType.HEAP);
        return p;
    }

    @Test
    void initWritesTypeAndEmptySlotCount() {
        Page p = heapPage(1);
        assertEquals(0, SlottedPage.slotCount(p));
        assertEquals(PageType.HEAP, PageType.from(p.readByte(SlottedPage.PAGE_TYPE_OFF)));
    }

    @Test
    void insertThenReadRoundtrip() {
        Page p = heapPage(1);
        byte[] rec = {1, 2, 3, 4, 5};
        int slot = SlottedPage.insert(p, rec);
        assertEquals(0, slot);
        assertEquals(1, SlottedPage.slotCount(p));
        assertArrayEquals(rec, SlottedPage.read(p, slot));
    }

    @Test
    void multipleInsertsKeepOrder() {
        Page p = heapPage(1);
        byte[] a = {10, 20};
        byte[] b = {30, 40, 50};
        int sa = SlottedPage.insert(p, a);
        int sb = SlottedPage.insert(p, b);
        assertEquals(2, SlottedPage.slotCount(p));
        assertArrayEquals(a, SlottedPage.read(p, sa));
        assertArrayEquals(b, SlottedPage.read(p, sb));
    }

    @Test
    void deleteMakesTombstone() {
        Page p = heapPage(1);
        int slot = SlottedPage.insert(p, new byte[]{1, 2, 3});
        assertNotNull(SlottedPage.read(p, slot));
        SlottedPage.delete(p, slot);
        assertNull(SlottedPage.read(p, slot));
    }

    @Test
    void hasFreeBoundary() {
        Page p = heapPage(1);
        assertTrue(SlottedPage.hasFree(p, 100));
        // 单页最多能放 PAGE_SIZE - HEADER_SIZE - SLOT_SIZE 字节的记录
        int max = Page.PAGE_SIZE - SlottedPage.HEADER_SIZE - SlottedPage.SLOT_SIZE;
        assertTrue(SlottedPage.hasFree(p, max));
        assertFalse(SlottedPage.hasFree(p, max + 1));
    }

    @Test
    void nextPageLink() {
        Page p = heapPage(1);
        assertEquals(0, SlottedPage.nextPage(p));
        SlottedPage.setNextPage(p, 7);
        assertEquals(7, SlottedPage.nextPage(p));
    }

    @Test
    void insertTooLongThrows() {
        Page p = heapPage(1);
        byte[] huge = new byte[Page.PAGE_SIZE];
        assertThrows(IllegalArgumentException.class, () -> SlottedPage.insert(p, huge));
    }
}
