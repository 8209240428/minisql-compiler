package minisql.storage;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 单文件页级 I/O：把数据库映射为一份按固定页大小切分的文件，负责页的读取、写入、
 * 分配（扩展文件）以及文件头的初始化与校验。
 *
 * <p>第 0 页是文件头页，固定布局（大端）：
 * <pre>
 * 0  magic(4)    = {@link #MAGIC} ("MSDB")
 * 4  pageSize(4) = {@link Page#PAGE_SIZE}
 * 8  catalogRoot(4)  目录页链首
 * 12 freeListHead(4) 空闲页链头
 * </pre>
 */
public final class DiskManager implements AutoCloseable {

    public static final int MAGIC = 0x4D534442; // "MSDB"
    public static final int HEADER_MAGIC_OFF = 0;
    public static final int HEADER_PAGE_SIZE_OFF = 4;
    public static final int HEADER_CATALOG_ROOT_OFF = 8;
    public static final int HEADER_FREE_LIST_OFF = 12;

    private final RandomAccessFile file;
    private final int pageSize;
    private int numPages;

    private DiskManager(RandomAccessFile file, int pageSize, int numPages) {
        this.file = file;
        this.pageSize = pageSize;
        this.numPages = numPages;
    }

    /** 创建或打开数据库文件；新建时写入文件头，重开时校验 magic 与 pageSize。 */
    public static DiskManager open(Path path, int pageSize) {
        try {
            boolean empty = !Files.exists(path) || Files.size(path) == 0;
            RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw");
            try {
                if (empty) {
                    DiskManager dm = new DiskManager(file, pageSize, 1);
                    dm.initHeader();
                    return dm;
                }
                int existing = (int) (file.length() / pageSize);
                DiskManager dm = new DiskManager(file, pageSize, existing);
                dm.validateHeader();
                return dm;
            } catch (IOException | RuntimeException ex) {
                // 校验失败等路径：关闭已打开的文件句柄，避免文件被占用（如测试临时目录无法清理）
                try {
                    file.close();
                } catch (IOException ignored) {
                    // 忽略关闭失败
                }
                throw ex;
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法打开数据库文件: " + path, e);
        }
    }

    private void initHeader() throws IOException {
        byte[] page = new byte[pageSize];
        ByteBuffer buf = ByteBuffer.wrap(page);
        buf.putInt(HEADER_MAGIC_OFF, MAGIC);
        buf.putInt(HEADER_PAGE_SIZE_OFF, pageSize);
        buf.putInt(HEADER_CATALOG_ROOT_OFF, 0);
        buf.putInt(HEADER_FREE_LIST_OFF, 0);
        file.seek(0);
        file.write(page);
        file.setLength(pageSize);
    }

    private void validateHeader() throws IOException {
        byte[] page = readPage(0);
        ByteBuffer buf = ByteBuffer.wrap(page);
        int magic = buf.getInt(HEADER_MAGIC_OFF);
        int ps = buf.getInt(HEADER_PAGE_SIZE_OFF);
        if (magic != MAGIC) {
            throw new IllegalStateException("文件不是 MiniSQL 数据库（magic 校验失败）");
        }
        if (ps != pageSize) {
            throw new IllegalStateException("页大小不一致：文件=" + ps + "，期望=" + pageSize);
        }
    }

    /** 读取第 pageId 页的完整字节（pageSize 字节） */
    public byte[] readPage(int pageId) {
        try {
            byte[] data = new byte[pageSize];
            file.seek((long) pageId * pageSize);
            file.readFully(data);
            return data;
        } catch (IOException e) {
            throw new IllegalStateException("读取页 " + pageId + " 失败", e);
        }
    }

    /** 把 data（必须 pageSize 字节）写回第 pageId 页 */
    public void writePage(int pageId, byte[] data) {
        try {
            file.seek((long) pageId * pageSize);
            file.write(data);
        } catch (IOException e) {
            throw new IllegalStateException("写页 " + pageId + " 失败", e);
        }
    }

    /** 分配一个新页（扩展文件一页），返回新页号 */
    public PageId allocatePage() {
        int id = numPages;
        numPages++;
        try {
            file.setLength((long) numPages * pageSize);
        } catch (IOException e) {
            throw new IllegalStateException("扩展数据库文件失败", e);
        }
        return new PageId(id);
    }

    public int numPages() {
        return numPages;
    }

    @Override
    public void close() {
        try {
            file.close();
        } catch (IOException e) {
            throw new IllegalStateException("关闭数据库文件失败", e);
        }
    }
}
