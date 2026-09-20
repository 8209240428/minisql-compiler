package minisql.exec;

import minisql.CompileResult;
import minisql.MiniSqlCompiler;
import minisql.ast.Statement;
import minisql.catalog.TableMeta;
import minisql.semantic.AnalyzedCreate;
import minisql.storage.BufferPool;
import minisql.storage.DiskManager;
import minisql.storage.Page;
import minisql.storage.PageType;
import minisql.storage.SlottedPage;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 数据库门面：把「编译（复用 {@link MiniSqlCompiler}）+ 存储 + 执行」串成一体。
 *
 * <p>打开时从目录页重载全部表结构并重登记进编译器的内存 {@code Catalog}——这就是
 * 数据恢复路径；之后 SELECT/INSERT/DELETE 才能对重开的表编译通过。CREATE 时分配
 * 该表第一张数据页并持久化 schema（建表即分配，目录记录里的 dataRootPage 无需原地改）。
 */
public final class Database implements AutoCloseable {

    private final DiskManager disk;
    private final BufferPool pool;
    private final CatalogStore catalogStore;
    private final MiniSqlCompiler compiler = new MiniSqlCompiler();
    private final Map<String, SchemaEntry> entries = new LinkedHashMap<>();

    private Database(DiskManager disk, BufferPool pool, CatalogStore catalogStore) {
        this.disk = disk;
        this.pool = pool;
        this.catalogStore = catalogStore;
    }

    public static Database open(Path dbPath) {
        DiskManager disk = DiskManager.open(dbPath, Page.PAGE_SIZE);
        BufferPool pool = new BufferPool(disk, BufferPool.DEFAULT_CAPACITY);
        CatalogStore cs = new CatalogStore(pool);
        Database db = new Database(disk, pool, cs);
        for (SchemaEntry e : cs.loadAll()) {
            db.compiler.catalog().register(e.table());
            db.entries.put(e.table().name().toLowerCase(Locale.ROOT), e);
        }
        return db;
    }

    /** 编译一条语句；CREATE 成功后分配数据页并持久化 schema。 */
    public CompileResult compile(Statement stmt) {
        CompileResult cr = compiler.compile(stmt);
        if (cr.ok() && cr.analyzed() instanceof AnalyzedCreate c) {
            TableMeta table = compiler.catalog().table(c.tableName());
            if (table != null) {
                Page p = pool.newPage();
                SlottedPage.init(p, PageType.HEAP);
                int root = p.id().id();
                pool.unpin(p, true);
                catalogStore.persist(table, root);
                entries.put(table.name().toLowerCase(Locale.ROOT), new SchemaEntry(table, root));
            }
        }
        return cr;
    }

    /** 编译并执行一条语句，返回执行结果。 */
    public ExecutionResult run(Statement stmt) {
        CompileResult cr = compile(stmt);
        if (!cr.ok()) {
            return ExecutionResult.error(cr);
        }
        if (!cr.hasPlan()) {
            return ExecutionResult.message("CREATE TABLE 成功");
        }
        return Executor.execute(cr.after().root(), this);
    }

    public MiniSqlCompiler compiler() {
        return compiler;
    }

    public BufferPool bufferPool() {
        return pool;
    }

    /** 取某表的堆存储（执行器用） */
    public TableHeap heapFor(TableMeta table) {
        SchemaEntry e = entries.get(table.name().toLowerCase(Locale.ROOT));
        if (e == null) {
            throw new ExecutionException("表未登记进存储: " + table.name());
        }
        return new TableHeap(pool, table, e.dataRootPage());
    }

    @Override
    public void close() {
        pool.close();
    }
}
