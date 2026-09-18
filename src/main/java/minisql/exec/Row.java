package minisql.exec;

import minisql.catalog.ColumnMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 一行已物化的数据：列形状（这些单元格对应哪些列）+ 单元格值 + 可选的物理 Rid。
 *
 * <p>单元格值只有 {@code Long}（INT）与 {@code String}（VARCHAR）两种。
 * 列查找按 {@link ColumnMeta} 结构相等，<b>不按位置下标</b>——这是扫描的列集合
 * （表定义序）与投影输出列（用户书写序）能被统一映射的关键。
 */
public final class Row {

    private final List<ColumnMeta> columns;
    private final List<Object> cells;
    private final Rid rid;

    public Row(List<ColumnMeta> columns, List<Object> cells, Rid rid) {
        if (columns.size() != cells.size()) {
            throw new IllegalArgumentException("列数(" + columns.size() + ")与值数(" + cells.size() + ")不一致");
        }
        this.columns = List.copyOf(columns);
        this.cells = List.copyOf(cells);
        this.rid = rid;
    }

    public static Row of(List<ColumnMeta> columns, List<Object> cells) {
        return new Row(columns, cells, null);
    }

    public List<ColumnMeta> columns() {
        return columns;
    }

    public List<Object> cells() {
        return cells;
    }

    public Object cell(int i) {
        return cells.get(i);
    }

    /** 按列元信息取单元格（结构相等），找不到抛异常 */
    public Object cell(ColumnMeta col) {
        int i = columns.indexOf(col);
        if (i < 0) {
            throw new IllegalArgumentException("行中不存在列 " + col.name());
        }
        return cells.get(i);
    }

    public Rid rid() {
        return rid;
    }

    public Row withRid(Rid rid) {
        return new Row(columns, cells, rid);
    }

    /** 投影：按 outputs 的顺序重排/子选单元格，产出不带 Rid 的新行 */
    public Row project(List<ColumnMeta> outputs) {
        List<Object> out = new ArrayList<>(outputs.size());
        for (ColumnMeta c : outputs) {
            out.add(cell(c));
        }
        return new Row(outputs, out, null);
    }
}
