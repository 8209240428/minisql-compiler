package minisql.exec;

import minisql.catalog.TableMeta;
import minisql.storage.BufferPool;
import minisql.storage.Page;
import minisql.storage.PageId;
import minisql.storage.PageType;
import minisql.storage.SlottedPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统目录的持久化：把表结构（schema）写入目录页链，并能在重开时全部重载。
 *
 * <p>目录页链首页号存于文件头（{@link BufferPool#catalogRoot()}）；每张表占一条
 * 目录记录（{@link SchemaEntry}），由 {@link SchemaCodec} 编解码后以槽页形式存放。
 */
public final class CatalogStore {

    private final BufferPool pool;

    public CatalogStore(BufferPool pool) {
        this.pool = pool;
    }

    /** 持久化一张表的结构与数据根页。 */
    public void persist(TableMeta table, int dataRootPage) {
        byte[] record = SchemaCodec.encode(table, dataRootPage);
        PageId root = pool.catalogRoot();
        if (root.id() == 0) {
            Page p = pool.newPage();
            SlottedPage.init(p, PageType.CATALOG);
            SlottedPage.insert(p, record);
            pool.setCatalogRoot(p.id());
            pool.unpin(p, true);
            return;
        }
        Page p = pool.getPage(root);
        while (true) {
            if (SlottedPage.hasFree(p, record.length)) {
                SlottedPage.insert(p, record);
                pool.unpin(p, true);
                return;
            }
            int next = SlottedPage.nextPage(p);
            if (next == 0) {
                Page np = pool.newPage();
                SlottedPage.init(np, PageType.CATALOG);
                SlottedPage.insert(np, record);
                SlottedPage.setNextPage(p, np.id().id());
                pool.unpin(p, true);
                pool.unpin(np, true);
                return;
            }
            pool.unpin(p, false);
            p = pool.getPage(new PageId(next));
        }
    }

    /** 重载全部目录记录（跳过删除墓碑），按页链 + 槽序返回。 */
    public List<SchemaEntry> loadAll() {
        PageId root = pool.catalogRoot();
        List<SchemaEntry> out = new ArrayList<>();
        if (root.id() == 0) {
            return out;
        }
        PageId cur = root;
        while (cur.id() != 0) {
            Page p = pool.getPage(cur);
            int n = SlottedPage.slotCount(p);
            for (int s = 0; s < n; s++) {
                byte[] rec = SlottedPage.read(p, s);
                if (rec == null) {
                    continue; // 墓碑
                }
                out.add(SchemaCodec.decode(rec));
            }
            int next = SlottedPage.nextPage(p);
            pool.unpin(p, false);
            cur = new PageId(next);
        }
        return out;
    }
}
