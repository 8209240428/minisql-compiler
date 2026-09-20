package minisql.exec;

import minisql.expr.ArithOp;
import minisql.expr.CmpOp;
import minisql.expr.Expr;
import minisql.expr.ExprType;

/**
 * 返回 （INT）/ （VARCHAR）/ （BOOLEAN）。
 * 语义与优化器的常量折叠约定一致：算术为 Java long（整数除法、溢出回绕），
 * VARCHAR 仅支持 = 和 <>，AND/OR 短路，除零抛异常。
 */
public final class ExprEvaluator {

    private ExprEvaluator() {
    }

    public static Object eval(Expr e, Row row) {
        if (e instanceof Expr.Value v) {
            return v.kind() == ExprType.INT ? v.num() : v.str();
        }
        if (e instanceof Expr.Col c) {
            return row.cell(c.meta());
        }
        if (e instanceof Expr.Bool b) {
            return b.value();
        }
        if (e instanceof Expr.Neg n) {
            return -((Long) eval(n.operand(), row));
        }
        if (e instanceof Expr.Arith a) {
            long l = (Long) eval(a.left(), row);
            long r = (Long) eval(a.right(), row);
            return arith(a.op(), l, r);
        }
        if (e instanceof Expr.Cmp c) {
            Object l = eval(c.left(), row);
            Object r = eval(c.right(), row);
            return compare(c.op(), l, r);
        }
        if (e instanceof Expr.And a) {
            return (Boolean) eval(a.left(), row) && (Boolean) eval(a.right(), row);
        }
        if (e instanceof Expr.Or o) {
            return (Boolean) eval(o.left(), row) || (Boolean) eval(o.right(), row);
        }
        if (e instanceof Expr.Not n) {
            return !(Boolean) eval(n.operand(), row);
        }
        throw new ExecutionException("无法求值的表达式: " + e);
    }

    private static long arith(ArithOp op, long l, long r) {
        return switch (op) {
            case PLUS -> l + r;
            case MINUS -> l - r;
            case MUL -> l * r;
            case DIV -> {
                if (r == 0) {
                    throw new ExecutionException("除以零");
                }
                yield l / r;
            }
        };
    }

    private static boolean compare(CmpOp op, Object l, Object r) {
        if (l instanceof Long a && r instanceof Long b) {
            return switch (op) {
                case EQ -> a.longValue() == b.longValue();
                case NEQ -> a.longValue() != b.longValue();
                case GT -> a > b;
                case LT -> a < b;
                case GTE -> a >= b;
                case LTE -> a <= b;
            };
        }
        if (l instanceof String a && r instanceof String b) {
            return switch (op) {
                case EQ -> a.equals(b);
                case NEQ -> !a.equals(b);
                default -> throw new ExecutionException("VARCHAR 不支持大小比较 " + op.symbol());
            };
        }
        throw new ExecutionException("比较两侧类型不一致: " + l + " vs " + r);
    }
}
