package minisql.exec;

import minisql.ast.DataType;
import minisql.catalog.ColumnMeta;
import minisql.expr.ArithOp;
import minisql.expr.CmpOp;
import minisql.expr.Expr;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式求值器测试：常量/列/布尔、算术（整数除法、除零）、比较、逻辑短路。
 */
class ExprEvaluatorTest {

    private static final ColumnMeta ID = new ColumnMeta("id", DataType.INT);
    private static final ColumnMeta NAME = new ColumnMeta("name", DataType.VARCHAR);
    private static final Row ROW = Row.of(List.of(ID, NAME), List.of(10L, "abc"));

    private static Object eval(Expr e) {
        return ExprEvaluator.eval(e, ROW);
    }

    @Test
    void valueColAndBool() {
        assertEquals(5L, ExprEvaluator.eval(Expr.Value.ofInt(5), ROW));
        assertEquals("x", ExprEvaluator.eval(Expr.Value.ofStr("x"), ROW));
        assertEquals(10L, eval(new Expr.Col(ID)));
        assertEquals("abc", eval(new Expr.Col(NAME)));
        assertTrue((Boolean) ExprEvaluator.eval(Expr.Bool.TRUE, ROW));
    }

    @Test
    void arith() {
        assertEquals(15L, eval(new Expr.Arith(ArithOp.PLUS, new Expr.Col(ID), Expr.Value.ofInt(5))));
        assertEquals(5L, eval(new Expr.Arith(ArithOp.MINUS, new Expr.Col(ID), Expr.Value.ofInt(5))));
        assertEquals(20L, eval(new Expr.Arith(ArithOp.MUL, new Expr.Col(ID), Expr.Value.ofInt(2))));
        // 整数除法：7 / 2 = 3
        assertEquals(3L, eval(new Expr.Arith(ArithOp.DIV, Expr.Value.ofInt(7), Expr.Value.ofInt(2))));
    }

    @Test
    void negate() {
        assertEquals(-10L, eval(new Expr.Neg(new Expr.Col(ID))));
    }

    @Test
    void divByZeroThrows() {
        Expr e = new Expr.Arith(ArithOp.DIV, Expr.Value.ofInt(1), Expr.Value.ofInt(0));
        assertThrows(ExecutionException.class, () -> ExprEvaluator.eval(e, null));
    }

    @Test
    void intComparisonAllOps() {
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.EQ, Expr.Value.ofInt(1), Expr.Value.ofInt(1))));
        assertFalse((Boolean) eval(new Expr.Cmp(CmpOp.NEQ, Expr.Value.ofInt(1), Expr.Value.ofInt(1))));
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.GT, Expr.Value.ofInt(2), Expr.Value.ofInt(1))));
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.LT, Expr.Value.ofInt(1), Expr.Value.ofInt(2))));
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.GTE, Expr.Value.ofInt(2), Expr.Value.ofInt(2))));
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.LTE, Expr.Value.ofInt(1), Expr.Value.ofInt(2))));
    }

    @Test
    void varcharOnlyEqNeq() {
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.EQ, Expr.Value.ofStr("a"), Expr.Value.ofStr("a"))));
        assertTrue((Boolean) eval(new Expr.Cmp(CmpOp.NEQ, Expr.Value.ofStr("a"), Expr.Value.ofStr("b"))));
    }

    @Test
    void varcharOrderThrows() {
        Expr e = new Expr.Cmp(CmpOp.GT, Expr.Value.ofStr("a"), Expr.Value.ofStr("b"));
        assertThrows(ExecutionException.class, () -> ExprEvaluator.eval(e, null));
    }

    @Test
    void andOrNot() {
        assertTrue((Boolean) eval(new Expr.And(Expr.Bool.TRUE, Expr.Bool.TRUE)));
        assertFalse((Boolean) eval(new Expr.Or(Expr.Bool.FALSE, Expr.Bool.FALSE)));
        assertFalse((Boolean) eval(new Expr.Not(Expr.Bool.TRUE)));
    }

    @Test
    void andShortCircuits() {
        Expr div0 = new Expr.Arith(ArithOp.DIV, Expr.Value.ofInt(1), Expr.Value.ofInt(0));
        // 左侧为 false，右侧除零不应被求值
        assertFalse((Boolean) ExprEvaluator.eval(new Expr.And(Expr.Bool.FALSE, div0), null));
    }

    @Test
    void orShortCircuits() {
        Expr div0 = new Expr.Arith(ArithOp.DIV, Expr.Value.ofInt(1), Expr.Value.ofInt(0));
        assertTrue((Boolean) ExprEvaluator.eval(new Expr.Or(Expr.Bool.TRUE, div0), null));
    }
}
