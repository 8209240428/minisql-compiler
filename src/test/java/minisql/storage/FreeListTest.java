package minisql.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 空闲页链表测试：deletePage 头插、newPage 头弹（LIFO 复用）。
 */
class FreeListTest {

    @TempDir
    Path dir;

    @Test
    void deleteThenNewPageReusesFreedPage() {
        DiskManager disk = DiskManager.open(dir.resolve("fl.db"), Page.PAGE_SIZE);
        for (int i = 0; i < 3; i++) {
            disk.allocatePage(); // 页号 1,2,3
        }
        BufferPool pool = new BufferPool(disk, 8);

        pool.deletePage(new PageId(2));

        Page reused = pool.newPage();
        assertEquals(new PageId(2), reused.id());
        pool.unpin(reused, true);

        // 复用后空闲链变空，再次 newPage 应扩展文件得到页 4
        Page next = pool.newPage();
        assertEquals(new PageId(4), next.id());
        pool.unpin(next, true);
        pool.close();
    }

    @Test
    void freeListIsLifo() {
        DiskManager disk = DiskManager.open(dir.resolve("fl2.db"), Page.PAGE_SIZE);
        for (int i = 0; i < 3; i++) {
            disk.allocatePage(); // 页号 1,2,3
        }
        BufferPool pool = new BufferPool(disk, 8);

        pool.deletePage(new PageId(1));
        pool.deletePage(new PageId(2));

        // 后删的 2 在链头，先被复用
        assertEquals(new PageId(2), pool.newPage().id());
        assertEquals(new PageId(1), pool.newPage().id());
        pool.close();
    }
}
