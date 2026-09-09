package minisql.optimizer;

import minisql.expr.ArithOp;
import minisql.expr.CmpOp;
import minisql.expr.Expr;
import minisql.expr.ExprPrinter;
import minisql.expr.ExprType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 表达式级重写工具（optimizer 包私有），供 5 条规则复用：
 * <ul>
 *   <li>{@link #fold}     —— 常量折叠（R1）：纯常量算术/比较/负号求值成常量或 TRUE/FALSE</li>
 *   <li>{@link #simplify} —— 布尔化简（R2）：TRUE/FALSE 恒等吸收、双重 NOT、x AND NOT x</li>
 *   <li>{@link #dedupe}   —— 冗余谓词消除（R3）：AND/OR 里去掉重复子式与恒真/恒假项</li>
 * </ul>
 *
 * <p>重写不可变地“新建节点”，不改动原树；没有变化的子树直接复用原对象。
 */
final class ExprOps {

    private ExprOps() {
    }

    // ------------------------------------------------------------------
    // R1 常量折叠
    // ------------------------------------------------------------------

    /** 对整棵表达式做常量折叠（自底向上） */
    static Expr fold(Expr expr) {
        if (expr instanceof Expr.Neg n) {
            Expr inner = fold(n.operand());
            if (inner instanceof Expr.Value v && v.kind() == ExprType.INT) {
                return Expr.Value.ofInt(-v.num());
            }
            return new Expr.Neg(inner);
        }
        if (expr instanceof Expr.Arith a) {
            Expr left = fold(a.left());
            Expr right = fold(a.right());
            if (left instanceof Expr.Value lv && right instanceof Expr.Value rv
                    && lv.kind() == ExprType.INT && rv.kind() == ExprType.INT) {
                long x = lv.num();
                long y = rv.num();
                long res;
                switch (a.op()) {
                    case PLUS -> res = x + y;
                    case MINUS -> res = x - y;
                    case MUL -> res = x * y;
                    case DIV -> {
                        // 除零不做折叠，保留原表达式（由优化层语义保证不会求值）
                        if (y == 0) {
                            return new Expr.Arith(a.op(), left, right);
                        }
                        res = x / y;
                    }
                    default -> throw new IllegalStateException("未知算术运算 " + a.op());
                }
                return Expr.Value.ofInt(res);
            }
            return new Expr.Arith(a.op(), left, right);
        }
        if (expr instanceof Expr.Cmp c) {
            return foldCompare(c.op(), fold(c.left()), fold(c.right()));
        }
        if (expr instanceof Expr.And a) {
            return new Expr.And(fold(a.left()), fold(a.right()));
        }
        if (expr instanceof Expr.Or o) {
            return new Expr.Or(fold(o.left()), fold(o.right()));
        }
        if (expr instanceof Expr.Not n) {
            return new Expr.Not(fold(n.operand()));
        }
        // Value / Col / Bool：叶子，无折叠空间
        return expr;
    }

    /** 比较折叠：两侧都是常量时算成 TRUE/FALSE */
    private static Expr foldCompare(CmpOp op, Expr left, Expr right) {
        if (!(left instanceof Expr.Value lv) || !(right instanceof Expr.Value rv)) {
            return new Expr.Cmp(op, left, right); // 至少一侧是列 → 无法折叠
        }
        if (lv.kind() != rv.kind()) {
            return new Expr.Cmp(op, left, right); // 类型不同（语义层已拦截，这里兜底）
        }
        if (lv.kind() == ExprType.INT) {
            long x = lv.num();
            long y = rv.num();
            boolean result = switch (op) {
                case EQ -> x == y;
                case NEQ -> x != y;
                case GT -> x > y;
                case LT -> x < y;
                case GTE -> x >= y;
                case LTE -> x <= y;
            };
            return result ? Expr.Bool.TRUE : Expr.Bool.FALSE;
        }
        // VARCHAR：只允许 = / <>（语义层已保证不会出现序比较）
        boolean equal = lv.str().equals(rv.str());
        if (op == CmpOp.EQ) {
            return equal ? Expr.Bool.TRUE : Expr.Bool.FALSE;
        }
        if (op == CmpOp.NEQ) {
            return equal ? Expr.Bool.FALSE : Expr.Bool.TRUE;
        }
        return new Expr.Cmp(op, left, right);
    }

    // ------------------------------------------------------------------
    // R2 布尔化简
    // ------------------------------------------------------------------

    /** 布尔恒等化简（自底向上）：吸收 TRUE/FALSE、双 NOT、x AND NOT x、x OR NOT x */
    static Expr simplify(Expr expr) {
        if (expr instanceof Expr.Not n) {
            Expr inner = simplify(n.operand());
            if (inner instanceof Expr.Bool b) {
                return b.value() ? Expr.Bool.FALSE : Expr.Bool.TRUE;
            }
            if (inner instanceof Expr.Not nn) {
                return simplify(nn.operand()); // NOT NOT x → x
            }
            return new Expr.Not(inner);
        }
        if (expr instanceof Expr.And a) {
            Expr l = simplify(a.left());
            Expr r = simplify(a.right());
            if (l instanceof Expr.Bool lb) {
                return lb.value() ? r : Expr.Bool.FALSE; // TRUE AND r → r ; FALSE AND r → FALSE
            }
            if (r instanceof Expr.Bool rb) {
                return rb.value() ? l : Expr.Bool.FALSE;
            }
            if (l.equals(r)) {
                return l; // p AND p → p
            }
            if (isNegation(l, r) || isNegation(r, l)) {
                return Expr.Bool.FALSE; // p AND NOT p → FALSE
            }
            return new Expr.And(l, r);
        }
        if (expr instanceof Expr.Or o) {
            Expr l = simplify(o.left());
            Expr r = simplify(o.right());
            if (l instanceof Expr.Bool lb) {
                return lb.value() ? Expr.Bool.TRUE : r; // TRUE OR r → TRUE ; FALSE OR r → r
            }
            if (r instanceof Expr.Bool rb) {
                return rb.value() ? Expr.Bool.TRUE : l;
            }
            if (l.equals(r)) {
                return l; // p OR p → p
            }
            if (isNegation(l, r) || isNegation(r, l)) {
                return Expr.Bool.TRUE; // p OR NOT p → TRUE
            }
            return new Expr.Or(l, r);
        }
        // Cmp / Arith / Neg / Value / Col / Bool 叶子级：无恒等化简
        return expr;
    }

    /** a 是否为 NOT b */
    private static boolean isNegation(Expr a, Expr b) {
        return a instanceof Expr.Not not && not.operand().equals(b);
    }

    // ------------------------------------------------------------------
    // R3 冗余谓词消除
    // ------------------------------------------------------------------

    /**
     * 扁平化 AND/OR 后按“规范化文本”去重、去掉恒真恒假项。
     * 返回 TRUE 表示整个条件恒真（可整体移除过滤）；FALSE 表示恒假（结果为空）。
     */
    static Expr dedupe(Expr expr) {
        if (expr instanceof Expr.And a) {
            List<Expr> parts = conjuncts(a);
            List<Expr> keep = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Expr p : parts) {
                if (p instanceof Expr.Bool b) {
                    if (!b.value()) {
                        return Expr.Bool.FALSE; // 一旦含 FALSE，整体恒假
                    }
                    continue; // TRUE 项直接丢弃
                }
                String key = ExprPrinter.render(p);
                if (seen.add(key)) {
                    keep.add(p);
                }
            }
            return rebuild(keep, true);
        }
        if (expr instanceof Expr.Or o) {
            List<Expr> parts = disjuncts(o);
            List<Expr> keep = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Expr p : parts) {
                if (p instanceof Expr.Bool b) {
                    if (b.value()) {
                        return Expr.Bool.TRUE; // 一旦含 TRUE，整体恒真
                    }
                    continue;
                }
                String key = ExprPrinter.render(p);
                if (seen.add(key)) {
                    keep.add(p);
                }
            }
            return rebuild(keep, false);
        }
        return expr;
    }

    /** 把 AND 树拍平成列表（左深 + 嵌套都处理） */
    private static List<Expr> conjuncts(Expr and) {
        List<Expr> out = new ArrayList<>();
        collect(and, true, out);
        return out;
    }

    /** 把 OR 树拍平成列表 */
    private static List<Expr> disjuncts(Expr or) {
        List<Expr> out = new ArrayList<>();
        collect(or, false, out);
        return out;
    }

    private static void collect(Expr e, boolean isAnd, List<Expr> out) {
        if (isAnd && e instanceof Expr.And a) {
            collect(a.left(), true, out);
            collect(a.right(), true, out);
            return;
        }
        if (!isAnd && e instanceof Expr.Or o) {
            collect(o.left(), false, out);
            collect(o.right(), false, out);
            return;
        }
        out.add(e);
    }

    /** 用去掉重复后的项重建 AND（isAnd=true）或 OR（false）树；空列表返回恒等元 */
    private static Expr rebuild(List<Expr> keep, boolean isAnd) {
        if (keep.isEmpty()) {
            return isAnd ? Expr.Bool.TRUE : Expr.Bool.FALSE;
        }
        if (keep.size() == 1) {
            return keep.get(0);
        }
        Expr acc = keep.get(0);
        for (int i = 1; i < keep.size(); i++) {
            acc = isAnd ? new Expr.And(acc, keep.get(i)) : new Expr.Or(acc, keep.get(i));
        }
        return acc;
    }
}
