package minisql.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 页级磁盘管理器测试：文件头初始化 / 校验 / 页分配 / 读写。
 */
class DiskManagerTest {

    @TempDir
    Path dir;

    @Test
    void createWritesHeader() {
        try (DiskManager dm = DiskManager.open(dir.resolve("a.db"), Page.PAGE_SIZE)) {
            assertEquals(1, dm.numPages());
            byte[] h = dm.readPage(0);
            ByteBuffer buf = ByteBuffer.wrap(h);
            assertEquals(DiskManager.MAGIC, buf.getInt(DiskManager.HEADER_MAGIC_OFF));
            assertEquals(Page.PAGE_SIZE, buf.getInt(DiskManager.HEADER_PAGE_SIZE_OFF));
        }
    }

    @Test
    void allocateExtendsFile() {
        try (DiskManager dm = DiskManager.open(dir.resolve("b.db"), Page.PAGE_SIZE)) {
            assertEquals(1, dm.numPages());
            assertEquals(new PageId(1), dm.allocatePage());
            assertEquals(new PageId(2), dm.allocatePage());
            assertEquals(3, dm.numPages());
        }
    }

    @Test
    void writeReadRoundtrip() {
        try (DiskManager dm = DiskManager.open(dir.resolve("c.db"), Page.PAGE_SIZE)) {
            int id = dm.allocatePage().id();
            byte[] data = new byte[Page.PAGE_SIZE];
            ByteBuffer.wrap(data).putInt(0, 123456789);
            ByteBuffer.wrap(data).putLong(100, 0x1122334455667788L);
            dm.writePage(id, data);

            byte[] back = dm.readPage(id);
            assertEquals(123456789, ByteBuffer.wrap(back).getInt(0));
            assertEquals(0x1122334455667788L, ByteBuffer.wrap(back).getLong(100));
        }
    }

    @Test
    void reopenValidatesOk() {
        Path f = dir.resolve("d.db");
        try (DiskManager dm = DiskManager.open(f, Page.PAGE_SIZE)) {
            dm.allocatePage();
            dm.writePage(1, new byte[Page.PAGE_SIZE]);
        }
        // 重开同一文件应成功
        try (DiskManager dm = DiskManager.open(f, Page.PAGE_SIZE)) {
            assertEquals(2, dm.numPages());
        }
    }

    @Test
    void reopenWrongMagicThrows() throws Exception {
        Path f = dir.resolve("e.db");
        Files.write(f, new byte[Page.PAGE_SIZE]); // 全零 → magic 不是 "MSDB"
        assertThrows(IllegalStateException.class,
                () -> DiskManager.open(f, Page.PAGE_SIZE));
    }
}
