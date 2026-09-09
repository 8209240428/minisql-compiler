package minisql.expr;

import minisql.catalog.ColumnMeta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * IR 表达式的辅助工具：收集被引用的列、判断是否纯常量等。
 * 供执行计划（投影裁剪需要知道谓词引用哪些列）与优化器复用。
 */
public final class ExprUtil {

    private ExprUtil() {
    }

    /**
     * 递归收集表达式中引用到的列，按首次出现顺序去重。
     * 返回的 ColumnMeta 与表元信息中的实例相同，可与输出列集合求并集。
     */
    public static List<ColumnMeta> referencedColumns(Expr expr) {
        Set<ColumnMeta> seen = new LinkedHashSet<>();
        if (expr != null) {
            collect(expr, seen);
        }
        return new ArrayList<>(seen);
    }

    /** 表达式是否为“纯常量”（不含任何列引用）；TRUE/FALSE 也算常量布尔 */
    public static boolean isPureConstant(Expr expr) {
        return expr != null && referencedColumns(expr).isEmpty();
    }

    /** 递归收集列引用（写入 seen 集合） */
    private static void collect(Expr expr, Set<ColumnMeta> seen) {
        if (expr instanceof Expr.Col col) {
            seen.add(col.meta());
            return;
        }
        if (expr instanceof Expr.Arith a) {
            collect(a.left(), seen);
            collect(a.right(), seen);
            return;
        }
        if (expr instanceof Expr.Neg n) {
            collect(n.operand(), seen);
            return;
        }
        if (expr instanceof Expr.Cmp c) {
            collect(c.left(), seen);
            collect(c.right(), seen);
            return;
        }
        if (expr instanceof Expr.And a) {
            collect(a.left(), seen);
            collect(a.right(), seen);
            return;
        }
        if (expr instanceof Expr.Or o) {
            collect(o.left(), seen);
            collect(o.right(), seen);
            return;
        }
        if (expr instanceof Expr.Not n) {
            collect(n.operand(), seen);
            return;
        }
        // Value / Bool / Col 已处理；其余为叶子或已包含
    }
}
