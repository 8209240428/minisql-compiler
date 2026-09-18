package minisql.exec;

import minisql.ast.DataType;
import minisql.catalog.ColumnMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 行 ↔ 字节 的序列化/反序列化（大端）。
 *
 * <p>因 schema 已知（来自 {@link TableMeta}），编码不带类型标签、无 NULL 位图：
 * INT 列 = 8 字节 long；VARCHAR 列 = 2 字节无符号长度 + UTF-8 字节。
 * {@link #decodeSubset} 只解出「请求列」——这正是投影裁剪（R5）在存储层的落地：
 * 堆里仍存整行，但扫描时只解码需要的列。
 */
public final class RowCodec {

    private RowCodec() {
    }

    /** 把与 schema 对齐的单元格列表编码成字节 */
    public static byte[] encode(List<ColumnMeta> schema, List<Object> cells) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            for (int i = 0; i < schema.size(); i++) {
                ColumnMeta col = schema.get(i);
                Object cell = cells.get(i);
                if (col.type() == DataType.INT) {
                    out.writeLong((Long) cell);
                } else {
                    byte[] b = ((String) cell).getBytes(StandardCharsets.UTF_8);
                    if (b.length > 0xFFFF) {
                        throw new ExecutionException("VARCHAR 值过长（超过 65535 字节）");
                    }
                    out.writeShort(b.length);
                    out.write(b);
                }
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ExecutionException("行编码失败", e);
        }
    }

    /** 按 schema 完整解码整行 */
    public static List<Object> decode(List<ColumnMeta> schema, byte[] bytes) {
        return decodeSubset(schema, schema, bytes);
    }

    /**
     * 按 fullSchema（表定义序）顺序解码，但只保留 requested 中含有的列。
     * 返回顺序与 fullSchema 中这些列的出现顺序一致。
     */
    public static List<Object> decodeSubset(List<ColumnMeta> fullSchema, List<ColumnMeta> requested, byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            List<Object> out = new ArrayList<>(requested.size());
            for (ColumnMeta col : fullSchema) {
                Object v;
                if (col.type() == DataType.INT) {
                    v = in.readLong();
                } else {
                    int len = in.readShort() & 0xFFFF;
                    byte[] b = new byte[len];
                    in.readFully(b);
                    v = new String(b, StandardCharsets.UTF_8);
                }
                if (requested.contains(col)) {
                    out.add(v);
                }
            }
            return out;
        } catch (IOException e) {
            throw new ExecutionException("行解码失败", e);
        }
    }
}
