package minisql.exec;

import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;
import minisql.storage.BufferPool;
import minisql.storage.Page;
import minisql.storage.PageId;
import minisql.storage.PageType;
import minisql.storage.SlottedPage;

import java.util.ArrayList;
import java.util.List;

/**
 * 单张表的堆存储：在若干数据页（页链）上插入整行、扫描若干列、按 Rid 删除。
 *
 * <p>插入走页链 first-fit；放不下则分配新页并链接。扫描把整条链的结果物化成一个
 * {@link List}，这样 DELETE 可以在扫描结束后再逐个删 Rid，不会边扫边改偏移。
 */
public final class TableHeap {

    private final BufferPool pool;
    private final TableMeta table;
    private final int dataRootPage;

    public TableHeap(BufferPool pool, TableMeta table, int dataRootPage) {
        this.pool = pool;
        this.table = table;
        this.dataRootPage = dataRootPage;
    }

    public void insert(Row row) {
        byte[] record = RowCodec.encode(table.columns(), row.cells());
        Page p = pool.getPage(new PageId(dataRootPage));
        while (true) {
            if (SlottedPage.hasFree(p, record.length)) {
                SlottedPage.insert(p, record);
                pool.unpin(p, true);
                return;
            }
            int next = SlottedPage.nextPage(p);
            if (next == 0) {
                Page np = pool.newPage();
                SlottedPage.init(np, PageType.HEAP);
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

    /** 扫描整表，按给定列集合解码（表定义序），每行携带 Rid。 */
    public List<Row> scan(List<ColumnMeta> columns) {
        List<Row> out = new ArrayList<>();
        PageId cur = new PageId(dataRootPage);
        while (cur.id() != 0) {
            Page p = pool.getPage(cur);
            int n = SlottedPage.slotCount(p);
            for (int s = 0; s < n; s++) {
                byte[] rec = SlottedPage.read(p, s);
                if (rec == null) {
                    continue;
                }
                List<Object> cells = RowCodec.decodeSubset(table.columns(), columns, rec);
                out.add(new Row(columns, cells, new Rid(cur.id(), s)));
            }
            int next = SlottedPage.nextPage(p);
            pool.unpin(p, false);
            cur = new PageId(next);
        }
        return out;
    }

    public void delete(Rid rid) {
        Page p = pool.getPage(new PageId(rid.pageId()));
        SlottedPage.delete(p, rid.slot());
        pool.unpin(p, true);
    }
}
