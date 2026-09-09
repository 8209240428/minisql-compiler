package minisql.expr;

/**
 * 把 C 侧 IR 表达式渲染成中缀文本，用于：
 * 计划树打印、优化前后对比、以及“去重谓词”的规范化 key。
 *
 * <p>统一加括号以保证不同结构的文本一定不同（可安全作为结构签名比较）。
 */
public final class ExprPrinter {

    private ExprPrinter() {
    }

    /** 渲染表达式为可读字符串（如 {@code (age > 18)}、{@code TRUE}、{@code 'Alice'}） */
    public static String render(Expr expr) {
        if (expr == null) {
            return "<null>";
        }
        if (expr instanceof Expr.Value v) {
            return v.kind() == ExprType.VARCHAR ? "'" + v.str() + "'" : Long.toString(v.num());
        }
        if (expr instanceof Expr.Col c) {
            return c.name();
        }
        if (expr instanceof Expr.Bool b) {
            return b.value() ? "TRUE" : "FALSE";
        }
        if (expr instanceof Expr.Arith a) {
            return "(" + render(a.left()) + " " + a.op().symbol() + " " + render(a.right()) + ")";
        }
        if (expr instanceof Expr.Neg n) {
            return "(-" + render(n.operand()) + ")";
        }
        if (expr instanceof Expr.Cmp c) {
            return "(" + render(c.left()) + " " + c.op().symbol() + " " + render(c.right()) + ")";
        }
        if (expr instanceof Expr.And a) {
            return "(" + render(a.left()) + " AND " + render(a.right()) + ")";
        }
        if (expr instanceof Expr.Or o) {
            return "(" + render(o.left()) + " OR " + render(o.right()) + ")";
        }
        if (expr instanceof Expr.Not n) {
            return "(NOT " + render(n.operand()) + ")";
        }
        // sealed 分支已穷尽，这里仅作兜底
        throw new IllegalStateException("未知的表达式节点: " + expr.getClass());
    }

    /** 便捷入口：null 谓词渲染为 "-"（表示无条件） */
    public static String renderOrDash(Expr expr) {
        return expr == null ? "-" : render(expr);
    }
}
