package minisql.storage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LRU 页缓冲池：在内存缓存若干 {@link Page}，统一对上层暴露
 * {@link #getPage(PageId)} / {@link #unpin(PageId, boolean)} / {@link #newPage()} /
 * {@link #deletePage(PageId)}，并负责命中/淘汰/替换/回写。
 *
 * <p>淘汰策略：用 {@link LinkedHashMap} 的 accessOrder=true 维护「最近访问在尾」，
 * 头结点即最久未访问（LRU）候选。被 pin 的页不参与淘汰；脏页淘汰前写回磁盘。
 * 同时维护文件头里的「空闲页链表」与「目录根页」两个指针。
 */
public final class BufferPool implements AutoCloseable {

    public static final int DEFAULT_CAPACITY = 8;

    private final DiskManager disk;
    private final int capacity;
    private final LinkedHashMap<PageId, Frame> frames;

    private int hitCount = 0;
    private int missCount = 0;
    private int evictCount = 0;
    private int writeBackCount = 0;

    private static final class Frame {
        final Page page;
        boolean dirty;
        int pinCount;

        Frame(Page page, boolean dirty, int pinCount) {
            this.page = page;
            this.dirty = dirty;
            this.pinCount = pinCount;
        }
    }

    public BufferPool(DiskManager disk, int capacity) {
        this.disk = disk;
        this.capacity = capacity;
        this.frames = new LinkedHashMap<>(capacity, 0.75f, true);
    }

    /** 取一页（pin + 命中计数 / 未命中则读盘，满则先淘汰）。调用方用完必须 {@link #unpin}。 */
    public Page getPage(PageId id) {
        Frame f = frames.get(id);
        if (f != null) {
            f.pinCount++;
            hitCount++;
            return f.page;
        }
        missCount++;
        if (frames.size() >= capacity) {
            evictOne();
        }
        Page p = new Page(id, disk.readPage(id.id()));
        frames.put(id, new Frame(p, false, 1));
        return p;
    }

    /** 释放一页；dirty=true 表示该页被修改过，淘汰/回写时需落盘。 */
    public void unpin(PageId id, boolean dirty) {
        Frame f = frames.get(id);
        if (f == null) {
            throw new IllegalStateException("unpin 了未缓存的页: " + id);
        }
        if (f.pinCount <= 0) {
            throw new IllegalStateException("页 " + id + " 的 unpin 次数超过 pin 次数");
        }
        if (dirty) {
            f.dirty = true;
        }
        f.pinCount--;
    }

    public void unpin(Page page, boolean dirty) {
        unpin(page.id(), dirty);
    }

    /**
     * 新分配一页：优先从空闲页链表弹出；空则扩展文件。返回清零、已 pin、标记 dirty 的页。
     */
    public Page newPage() {
        Page header = getPage(PageId.ZERO);
        int head = header.readInt(DiskManager.HEADER_FREE_LIST_OFF);
        if (head != 0) {
            int next = readFreePageNext(head);
            header.writeInt(DiskManager.HEADER_FREE_LIST_OFF, next);
            unpin(header.id(), true);
            Page p = Page.newEmpty(new PageId(head));
            frames.put(p.id(), new Frame(p, true, 1));
            return p;
        }
        unpin(header.id(), false);
        PageId id = disk.allocatePage();
        Page p = Page.newEmpty(id);
        frames.put(id, new Frame(p, true, 1));
        return p;
    }

    /** 删除一页：把该页头插进空闲页链表并立即落盘，同时移出缓存。 */
    public void deletePage(PageId id) {
        Page header = getPage(PageId.ZERO);
        int oldHead = header.readInt(DiskManager.HEADER_FREE_LIST_OFF);

        byte[] data = new byte[Page.PAGE_SIZE];
        Frame f = frames.get(id);
        if (f != null) {
            System.arraycopy(f.page.data(), 0, data, 0, Page.PAGE_SIZE);
        } else {
            data = disk.readPage(id.id());
        }
        java.nio.ByteBuffer.wrap(data).putInt(0, oldHead);
        disk.writePage(id.id(), data);

        header.writeInt(DiskManager.HEADER_FREE_LIST_OFF, id.id());
        unpin(header.id(), true);
        frames.remove(id);
    }

    /** 回写单页（若脏） */
    public void flushPage(PageId id) {
        Frame f = frames.get(id);
        if (f != null && f.dirty) {
            disk.writePage(id.id(), f.page.data());
            f.dirty = false;
            writeBackCount++;
        }
    }

    /** 回写所有脏页（不改变缓存内容与 LRU 顺序） */
    public void flushAll() {
        for (Map.Entry<PageId, Frame> e : frames.entrySet()) {
            Frame f = e.getValue();
            if (f.dirty) {
                disk.writePage(e.getKey().id(), f.page.data());
                f.dirty = false;
                writeBackCount++;
            }
        }
    }

    /** 目录根页页号（0 = 尚未建目录） */
    public PageId catalogRoot() {
        Page header = getPage(PageId.ZERO);
        int root = header.readInt(DiskManager.HEADER_CATALOG_ROOT_OFF);
        unpin(header.id(), false);
        return new PageId(root);
    }

    public void setCatalogRoot(PageId root) {
        Page header = getPage(PageId.ZERO);
        header.writeInt(DiskManager.HEADER_CATALOG_ROOT_OFF, root.id());
        unpin(header.id(), true);
    }

    @Override
    public void close() {
        flushAll();
        disk.close();
    }

    private void evictOne() {
        Iterator<Map.Entry<PageId, Frame>> it = frames.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<PageId, Frame> e = it.next();
            Frame frame = e.getValue();
            if (frame.pinCount == 0) {
                evictCount++;
                if (frame.dirty) {
                    disk.writePage(e.getKey().id(), frame.page.data());
                    writeBackCount++;
                    frame.dirty = false;
                }
                it.remove();
                return;
            }
        }
        throw new IllegalStateException("缓冲池无可用页帧（全部被 pin）");
    }

    private int readFreePageNext(int freePageId) {
        byte[] data = disk.readPage(freePageId);
        return java.nio.ByteBuffer.wrap(data).getInt(0);
    }

    // ---- 测试/演示钩子 ----

    public int hitCount() {
        return hitCount;
    }

    public int missCount() {
        return missCount;
    }

    public int evictCount() {
        return evictCount;
    }

    public int writeBackCount() {
        return writeBackCount;
    }

    /** 当前驻留在缓存中的页号（LRU 顺序：从最久到最近访问） */
    public List<PageId> residentPages() {
        return new ArrayList<>(frames.keySet());
    }
}
