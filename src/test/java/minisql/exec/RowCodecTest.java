package minisql.exec;

import minisql.ast.DataType;
import minisql.catalog.ColumnMeta;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 行编解码测试：整行往返、按需列解码、VARCHAR 上限。
 */
class RowCodecTest {

    private static final ColumnMeta ID = new ColumnMeta("id", DataType.INT);
    private static final ColumnMeta NAME = new ColumnMeta("name", DataType.VARCHAR);
    private static final ColumnMeta AGE = new ColumnMeta("age", DataType.INT);

    @Test
    void roundtripIntAndVarchar() {
        List<ColumnMeta> schema = List.of(ID, NAME);
        List<Object> cells = List.of(42L, "Alice");
        byte[] bytes = RowCodec.encode(schema, cells);
        assertEquals(cells, RowCodec.decode(schema, bytes));
    }

    @Test
    void roundtripUnicode() {
        List<ColumnMeta> schema = List.of(ID, NAME);
        List<Object> cells = List.of(7L, "数据库中文");
        byte[] bytes = RowCodec.encode(schema, cells);
        assertEquals(cells, RowCodec.decode(schema, bytes));
    }

    @Test
    void decodeSubsetOnlyRequestedColumns() {
        List<ColumnMeta> schema = List.of(ID, NAME, AGE);
        byte[] bytes = RowCodec.encode(schema, List.of(1L, "Bob", 30L));
        // 返回顺序按表定义序，只保留被请求的列
        assertEquals(List.of(1L, 30L), RowCodec.decodeSubset(schema, List.of(ID, AGE), bytes));
        assertEquals(List.of("Bob"), RowCodec.decodeSubset(schema, List.of(NAME), bytes));
    }

    @Test
    void varcharTooLongThrows() {
        String big = "a".repeat(65536);
        assertThrows(ExecutionException.class,
                () -> RowCodec.encode(List.of(NAME), List.of(big)));
    }
}
