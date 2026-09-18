package minisql.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LRU 缓冲池测试：命中/未命中计数、淘汰顺序、脏页回写、flushAll、新页分配。
 */
class BufferPoolTest {

    @TempDir
    Path dir;

    /** 建一个带若干已分配数据页（页号 1..n）的磁盘管理器 */
    private DiskManager openDisk(int dataPages) {
        DiskManager dm = DiskManager.open(dir.resolve("bp.db"), Page.PAGE_SIZE);
        for (int i = 0; i < dataPages; i++) {
            dm.allocatePage();
        }
        return dm;
    }

    @Test
    void hitAndMissCounting() {
        DiskManager disk = openDisk(2);
        BufferPool pool = new BufferPool(disk, 3);
        pool.getPage(new PageId(1));
        pool.unpin(new PageId(1), false);
        assertEquals(1, pool.missCount());
        assertEquals(0, pool.hitCount());

        pool.getPage(new PageId(1));
        pool.unpin(new PageId(1), false);
        assertEquals(1, pool.missCount());
        assertEquals(1, pool.hitCount());
        pool.close();
    }

    @Test
    void lruEvictsLeastRecentlyUsed() {
        DiskManager disk = openDisk(5);
        BufferPool pool = new BufferPool(disk, 3);
        pool.getPage(new PageId(1));
        pool.unpin(new PageId(1), false);
        pool.getPage(new PageId(2));
        pool.unpin(new PageId(2), false);
        pool.getPage(new PageId(3));
        pool.unpin(new PageId(3), false);   // 驻留 [1,2,3]

        pool.getPage(new PageId(1));
        pool.unpin(new PageId(1), false);   // 访问 1 → 序 [2,3,1]

        pool.getPage(new PageId(4));        // 未命中，淘汰最久未用的 2
        assertEquals(1, pool.evictCount());
        assertEquals(List.of(new PageId(3), new PageId(1), new PageId(4)),
                pool.residentPages());
        pool.unpin(new PageId(4), false);
        pool.close();
    }

    @Test
    void dirtyPageWrittenBackOnEvict() {
        DiskManager disk = openDisk(2);
        BufferPool pool = new BufferPool(disk, 1);
        Page p = pool.getPage(new PageId(1));
        p.writeInt(0, 123456789);
        pool.unpin(new PageId(1), true);    // 脏页

        pool.getPage(new PageId(2));        // 触发淘汰 1 → 写回
        assertEquals(1, pool.writeBackCount());
        byte[] data = disk.readPage(1);
        assertEquals(123456789, ByteBuffer.wrap(data).getInt(0));
        pool.unpin(new PageId(2), false);
        pool.close();
    }

    @Test
    void flushAllWritesAllDirty() {
        DiskManager disk = openDisk(2);
        BufferPool pool = new BufferPool(disk, 4);
        Page p1 = pool.getPage(new PageId(1));
        p1.writeInt(0, 11);
        pool.unpin(new PageId(1), true);
        Page p2 = pool.getPage(new PageId(2));
        p2.writeInt(0, 22);
        pool.unpin(new PageId(2), true);

        pool.flushAll();
        assertEquals(2, pool.writeBackCount());
        assertEquals(11, ByteBuffer.wrap(disk.readPage(1)).getInt(0));
        assertEquals(22, ByteBuffer.wrap(disk.readPage(2)).getInt(0));
        pool.close();
    }

    @Test
    void newPageFromEmptyFileAllocatesNextId() {
        DiskManager disk = DiskManager.open(dir.resolve("np.db"), Page.PAGE_SIZE);
        BufferPool pool = new BufferPool(disk, 8);
        Page p = pool.newPage();
        assertEquals(new PageId(1), p.id());
        pool.unpin(p, true);
        pool.close();
    }
}
