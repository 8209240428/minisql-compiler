package minisql.exec;

import minisql.ast.DataType;
import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统目录记录（表结构 + 数据根页）↔ 字节 的编解码。
 *
 * <p>一条目录记录的载荷（长度由槽条目携带，不再单独存长度）：
 * <pre>
 * 2B 表名长度 + UTF-8 表名
 * 1B 列数
 * 每列：2B 列名长度 + UTF-8 列名 + 1B 类型（0=INT, 1=VARCHAR）
 * 4B dataRootPage
 * </pre>
 */
public final class SchemaCodec {

    private SchemaCodec() {
    }

    public static byte[] encode(TableMeta table, int dataRootPage) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);

            writeUtf(out, table.name());
            List<ColumnMeta> cols = table.columns();
            out.writeByte(cols.size());
            for (ColumnMeta c : cols) {
                writeUtf(out, c.name());
                out.writeByte(c.type() == DataType.INT ? 0 : 1);
            }
            out.writeInt(dataRootPage);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new ExecutionException("目录记录编码失败", e);
        }
    }

    public static SchemaEntry decode(byte[] bytes) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            String name = readUtf(in);
            int colCount = in.readByte() & 0xFF;
            List<ColumnMeta> cols = new ArrayList<>(colCount);
            for (int i = 0; i < colCount; i++) {
                String cn = readUtf(in);
                int typeCode = in.readByte() & 0xFF;
                DataType type = (typeCode == 0) ? DataType.INT : DataType.VARCHAR;
                cols.add(new ColumnMeta(cn, type));
            }
            int root = in.readInt();
            return new SchemaEntry(new TableMeta(name, cols), root);
        } catch (IOException e) {
            throw new ExecutionException("目录记录解码失败", e);
        }
    }

    private static void writeUtf(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        out.writeShort(b.length);
        out.write(b);
    }

    private static String readUtf(DataInputStream in) throws IOException {
        int len = in.readShort() & 0xFFFF;
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
