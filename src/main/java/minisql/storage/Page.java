package minisql.storage;

import java.nio.ByteBuffer;

/**
 * 一个固定大小的内存页（默认 {@link #PAGE_SIZE} = 4096 字节）。
 *
 * <p>提供大端（Big-Endian）的基本类型读写访问器，供 {@link SlottedPage}、
 * {@link DiskManager} 与 {@link BufferPool} 共同使用。写访问会把页标记为 dirty
 * （dirty 仅作调试镜像，真正决定是否写回的是 {@code BufferPool.Frame.dirty}）。
 */
public final class Page {

    /** 页大小（字节）。新建/重开数据库时与文件头里记录的 pageSize 校验一致。 */
    public static final int PAGE_SIZE = 4096;

    private final PageId id;
    private final byte[] data;
    private boolean dirty;

    public Page(PageId id, byte[] data) {
        if (data.length != PAGE_SIZE) {
            throw new IllegalArgumentException("页数据长度必须为 " + PAGE_SIZE + "，实际 " + data.length);
        }
        this.id = id;
        this.data = data;
    }

    /** 新建一个全零页（未标记 dirty） */
    public static Page newEmpty(PageId id) {
        return new Page(id, new byte[PAGE_SIZE]);
    }

    public PageId id() {
        return id;
    }

    public byte[] data() {
        return data;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markDirty(boolean dirty) {
        this.dirty = dirty;
    }

    // ---- 大端读写访问器（用绝对 get/put，避免共享 position 状态） ----

    public int readInt(int off) {
        return ByteBuffer.wrap(data).getInt(off);
    }

    public void writeInt(int off, int v) {
        ByteBuffer.wrap(data).putInt(off, v);
        dirty = true;
    }

    public long readLong(int off) {
        return ByteBuffer.wrap(data).getLong(off);
    }

    public void writeLong(int off, long v) {
        ByteBuffer.wrap(data).putLong(off, v);
        dirty = true;
    }

    /** 读无符号 16 位（0..65535），用于槽页里的各种长度/偏移字段 */
    public int readShort(int off) {
        return ByteBuffer.wrap(data).getShort(off) & 0xFFFF;
    }

    /** 写低 16 位 */
    public void writeShort(int off, int v) {
        ByteBuffer.wrap(data).putShort(off, (short) v);
        dirty = true;
    }

    public byte readByte(int off) {
        return data[off];
    }

    public void writeByte(int off, int v) {
        data[off] = (byte) v;
        dirty = true;
    }

    public void readBytes(int off, byte[] dst, int len) {
        System.arraycopy(data, off, dst, 0, len);
    }

    public void writeBytes(int off, byte[] src, int len) {
        System.arraycopy(src, 0, data, off, len);
        dirty = true;
    }
}
