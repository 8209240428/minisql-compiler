package minisql.expr;

import minisql.catalog.ColumnMeta;

/**
 * C 自有的“已绑定表达式 IR”（sealed 层级）。
 *
 * <p>语义分析把 B 的 AST 表达式做名字绑定 + 类型检查后翻译成这套 IR；
 * 执行计划与优化器只在这套 IR（以及计划树）上工作，绝不触碰/重建 B 的 AST。
 *
 * <p>为什么自建而非直接复用 B 的 AST：B 的 AST 里没有“布尔常量 TRUE/FALSE”节点，
 * 而常量折叠（如 {@code 1 = 1 → TRUE}）需要表达布尔常量；在 C 侧 IR 里补上
 * {@link Expr.Bool}，B 的节点定义保持不变。
 */
public sealed interface Expr
        permits Expr.Value, Expr.Col, Expr.Bool, Expr.Arith, Expr.Neg, Expr.Cmp,
                Expr.And, Expr.Or, Expr.Not {

    /** 表达式类型（INT / VARCHAR / BOOLEAN） */
    ExprType kind();

    /**
     * 类型化常量：INT 存 {@code Long}、VARCHAR 存 {@code String}。
     * 供字面量、常量折叠结果的表示。
     */
    record Value(ExprType kind, Object data) implements Expr {
        /** INT 常量 */
        public static Value ofInt(long n) {
            return new Value(ExprType.INT, n);
        }

        /** VARCHAR 常量（不带引号的内容） */
        public static Value ofStr(String s) {
            return new Value(ExprType.VARCHAR, s);
        }

        public Value {
            if (kind == ExprType.INT && !(data instanceof Long)) {
                throw new IllegalArgumentException("INT 常量必须携带 Long");
            }
            if (kind == ExprType.VARCHAR && !(data instanceof String)) {
                throw new IllegalArgumentException("VARCHAR 常量必须携带 String");
            }
            if (kind == ExprType.BOOLEAN) {
                throw new IllegalArgumentException("布尔常量请使用 Expr.Bool");
            }
        }

        /** 读取 INT 值（仅 kind 为 INT 时合法） */
        public long num() {
            if (kind != ExprType.INT) {
                throw new IllegalStateException("当前常量不是 INT");
            }
            return (Long) data;
        }

        /** 读取 VARCHAR 值（仅 kind 为 VARCHAR 时合法） */
        public String str() {
            if (kind != ExprType.VARCHAR) {
                throw new IllegalStateException("当前常量不是 VARCHAR");
            }
            return (String) data;
        }
    }

    /** 已绑定到具体列的列引用（ColumnMeta 来自 Catalog，类型已知） */
    record Col(ColumnMeta meta) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.fromAst(meta.type());
        }

        public String name() {
            return meta.name();
        }
    }

    /**
     * 布尔常量 TRUE / FALSE（B 的 AST 没有，由 C 的 IR 补齐）。
     */
    record Bool(boolean value) implements Expr {
        public static final Bool TRUE = new Bool(true);
        public static final Bool FALSE = new Bool(false);

        @Override
        public ExprType kind() {
            return ExprType.BOOLEAN;
        }
    }

    /** 二元算术（只作用于 INT），供常量折叠前保留、折叠后消除 */
    record Arith(ArithOp op, Expr left, Expr right) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.INT;
        }
    }

    /** 一元负号（INT） */
    record Neg(Expr operand) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.INT;
        }
    }

    /** 比较表达式（结果恒为 BOOLEAN） */
    record Cmp(CmpOp op, Expr left, Expr right) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.BOOLEAN;
        }
    }

    /** 逻辑与（两侧均为 BOOLEAN） */
    record And(Expr left, Expr right) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.BOOLEAN;
        }
    }

    /** 逻辑或（两侧均为 BOOLEAN） */
    record Or(Expr left, Expr right) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.BOOLEAN;
        }
    }

    /** 逻辑非（操作数为 BOOLEAN） */
    record Not(Expr operand) implements Expr {
        @Override
        public ExprType kind() {
            return ExprType.BOOLEAN;
        }
    }
}
