package minisql.semantic;

import minisql.ast.AstVisitor;
import minisql.ast.BinaryExpr;
import minisql.ast.BinaryOp;
import minisql.ast.ColumnDef;
import minisql.ast.CreateTableStmt;
import minisql.ast.DeleteStmt;
import minisql.ast.Expression;
import minisql.ast.IdentifierExpr;
import minisql.ast.InsertStmt;
import minisql.ast.LiteralExpr;
import minisql.ast.SelectStmt;
import minisql.ast.Statement;
import minisql.ast.UnaryExpr;
import minisql.ast.UnaryOp;
import minisql.catalog.Catalog;
import minisql.catalog.ColumnMeta;
import minisql.catalog.TableMeta;
import minisql.expr.ArithOp;
import minisql.expr.CmpOp;
import minisql.expr.Expr;
import minisql.expr.ExprType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 语义分析器：遍历 B 的 AST，做 <b>名字绑定 + 类型检查</b>，产出已解析结果 {@link Analyzed}。
 *
 * <p>对四类语句的职责：
 * <ul>
 *   <li>CREATE TABLE —— 检查表名冲突、列定义重复，并登记进 {@link Catalog}（会话状态）；</li>
 *   <li>INSERT —— 检查表/列存在、列需完整覆盖且不重复、值个数一致、值类型与列匹配、值不允许引用列；</li>
 *   <li>SELECT / DELETE —— 检查表存在、SELECT 列存在（* 展开为全列）、WHERE 为布尔且类型合法。</li>
 * </ul>
 *
 * <p>表达式层面把 B 的 AST 翻译为 C 侧 IR {@link Expr}，同时施加类型规则
 * （详见每个分支的注释）。报错均带行列号。
 *
 * <p>说明：语句走 {@code AstVisitor}（保持与 B 约定一致）；表达式用私有方法递归处理，
 * 因为校验需要携带「表结构 / 是否允许引用列 / 期望类型」等上下文，
 * 不适合再走一次无上下文的 accept。
 */
public class SemanticAnalyzer implements AstVisitor<Analyzed> {

    private final Catalog catalog;

    public SemanticAnalyzer(Catalog catalog) {
        this.catalog = catalog;
    }

    /** 对外入口：分析一条语句 */
    public Analyzed analyze(Statement statement) {
        return statement.accept(this);
    }

    // ------------------------------------------------------------------
    // 四种语句
    // ------------------------------------------------------------------

    @Override
    public Analyzed visitCreateTableStmt(CreateTableStmt stmt) {
        String name = stmt.tableName();
        if (catalog.containsTable(name)) {
            throw SemanticException.at(stmt, "表 '" + name + "' 已存在");
        }
        List<ColumnMeta> metas = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ColumnDef def : stmt.columns()) {
            String lower = def.name().toLowerCase(Locale.ROOT);
            if (!seen.add(lower)) {
                // ColumnDef 不实现 AstNode，改用带行列的定位
                throw SemanticException.at(def.line(), def.col(),
                        "CREATE TABLE 中重复定义列 '" + def.name() + "'");
            }
            metas.add(new ColumnMeta(def.name(), def.type()));
        }
        boolean ok = catalog.register(new TableMeta(name, metas));
        if (!ok) {
            // 上面已检查 containsTable，这里仅兜底
            throw SemanticException.at(stmt, "表 '" + name + "' 已存在");
        }
        return new AnalyzedCreate(name);
    }

    @Override
    public Analyzed visitInsertStmt(InsertStmt stmt) {
        TableMeta table = tableOrThrow(stmt, stmt.tableName());

        // 解析目标列：列必须都存在、且不允许重复
        List<String> names = stmt.columns();
        List<ColumnMeta> targets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String n : names) {
            String lower = n.toLowerCase(Locale.ROOT);
            if (!seen.add(lower)) {
                throw SemanticException.at(stmt, "INSERT 目标列重复: '" + n + "'");
            }
            ColumnMeta col = table.findColumn(n);
            if (col == null) {
                throw SemanticException.at(stmt, "未定义的列 '" + n + "'");
            }
            targets.add(col);
        }

        // 当前表没有默认值/可空概念，因此 INSERT 必须覆盖全部列
        if (targets.size() != table.columnCount()) {
            throw SemanticException.at(stmt,
                    "列数量与表 '" + table.name() + "' 不符(表共 " + table.columnCount()
                            + " 列, 给出 " + targets.size() + " 列)");
        }

        // 值的个数必须与目标列一致
        List<Expression> rawValues = stmt.values();
        if (rawValues.size() != targets.size()) {
            throw SemanticException.at(stmt,
                    "INSERT 值的个数(" + rawValues.size() + ") 与列个数(" + targets.size() + ") 不一致");
        }

        // 逐值检查：不得引用列、不得为布尔；类型须与目标列一致
        List<Expr> values = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            Expression raw = rawValues.get(i);
            Expr v = toExpr(raw, table, false);
            if (v.kind().isBoolean()) {
                throw SemanticException.at(raw,
                        "INSERT 值必须是数据值(INT/VARCHAR), 不能是布尔表达式");
            }
            ExprType want = ExprType.fromAst(targets.get(i).type());
            if (v.kind() != want) {
                throw SemanticException.at(raw,
                        "类型不匹配: 列 '" + targets.get(i).name() + "'(" + want
                                + "), 实际值是 " + v.kind());
            }
            values.add(v);
        }
        return new AnalyzedInsert(table, targets, values);
    }

    @Override
    public Analyzed visitSelectStmt(SelectStmt stmt) {
        TableMeta table = tableOrThrow(stmt, stmt.tableName());

        // 输出列：* 直接展开为表全列
        List<ColumnMeta> outputs = new ArrayList<>();
        if (stmt.star()) {
            outputs.addAll(table.columns());
        } else {
            for (String c : stmt.columns()) {
                ColumnMeta col = table.findColumn(c);
                if (col == null) {
                    throw SemanticException.at(stmt, "未定义的列 '" + c + "'");
                }
                outputs.add(col);
            }
        }

        Expr where = null;
        if (stmt.where() != null) {
            where = requireBoolean(stmt.where(), table, "WHERE 条件");
        }
        return new AnalyzedSelect(table, outputs, where);
    }

    @Override
    public Analyzed visitDeleteStmt(DeleteStmt stmt) {
        TableMeta table = tableOrThrow(stmt, stmt.tableName());
        Expr where = null;
        if (stmt.where() != null) {
            where = requireBoolean(stmt.where(), table, "WHERE 条件");
        }
        return new AnalyzedDelete(table, where);
    }

    // ------------------------------------------------------------------
    // 表达式节点：不会由 accept 主动触发（见类注释），此处兜底
    // ------------------------------------------------------------------

    @Override
    public Analyzed visitBinaryExpr(BinaryExpr expr) {
        throw unsupportedExpr("BinaryExpr");
    }

    @Override
    public Analyzed visitUnaryExpr(UnaryExpr expr) {
        throw unsupportedExpr("UnaryExpr");
    }

    @Override
    public Analyzed visitIdentifierExpr(IdentifierExpr expr) {
        throw unsupportedExpr("IdentifierExpr");
    }

    @Override
    public Analyzed visitLiteralExpr(LiteralExpr expr) {
        throw unsupportedExpr("LiteralExpr");
    }

    private UnsupportedOperationException unsupportedExpr(String kind) {
        return new UnsupportedOperationException(
                kind + " 不应经 AstVisitor 触发，请走 SemanticAnalyzer 内部表达式检查");
    }

    // ------------------------------------------------------------------
    // 表达式翻译 + 类型检查
    // ------------------------------------------------------------------

    /**
     * 把 AST 表达式翻译成类型化 IR。布尔上下文（AND/OR/NOT 子式）与数据上下文（算术/比较操作数、
     * INSERT 值）都从这一入口进入，随后在外层方法施加“必须为布尔 / 必须为指定数据类”的约束。
     *
     * @param table       当前语句涉及的表（用于列名绑定）
     * @param colsAllowed 是否允许出现列引用（INSERT 值为 false）
     */
    private Expr toExpr(Expression e, TableMeta table, boolean colsAllowed) {
        if (e instanceof LiteralExpr lit) {
            // 字符串 → VARCHAR 常量；数字 → INT 常量
            if (lit.kind() == LiteralExpr.Kind.STRING) {
                return Expr.Value.ofStr(lit.raw());
            }
            try {
                return Expr.Value.ofInt(Long.parseLong(lit.raw()));
            } catch (NumberFormatException ex) {
                throw SemanticException.at(lit, "非法整数常量 '" + lit.raw() + "'");
            }
        }
        if (e instanceof IdentifierExpr id) {
            if (!colsAllowed) {
                throw SemanticException.at(id, "INSERT 值中不允许出现列引用 '" + id.name() + "'");
            }
            ColumnMeta col = table.findColumn(id.name());
            if (col == null) {
                throw SemanticException.at(id, "未定义的列 '" + id.name() + "'");
            }
            return new Expr.Col(col);
        }
        if (e instanceof UnaryExpr un) {
            if (un.op() == UnaryOp.NOT) {
                Expr inner = toExpr(un.operand(), table, colsAllowed);
                requireBooleanType(inner, un.operand(), "NOT 只能作用于布尔表达式");
                return new Expr.Not(inner);
            }
            // NEGATE（一元负号）：只对 INT 有意义
            Expr inner = toExpr(un.operand(), table, colsAllowed);
            if (inner.kind() != ExprType.INT) {
                throw SemanticException.at(un, "一元负号只能作用于 INT 类型, 实际是 " + inner.kind());
            }
            return new Expr.Neg(inner);
        }
        if (e instanceof BinaryExpr bin) {
            return toBinary(bin, table, colsAllowed);
        }
        throw new UnsupportedOperationException("无法识别的表达式节点: " + e.getClass().getName());
    }

    /** 二元表达式按运算符分类处理（逻辑 / 比较 / 算术） */
    private Expr toBinary(BinaryExpr bin, TableMeta table, boolean colsAllowed) {
        BinaryOp op = bin.op();
        switch (op) {
            case AND:
            case OR: {
                Expr left = toExpr(bin.left(), table, colsAllowed);
                Expr right = toExpr(bin.right(), table, colsAllowed);
                requireBooleanType(left, bin.left(), "'" + op.name() + "' 左侧必须是布尔表达式");
                requireBooleanType(right, bin.right(), "'" + op.name() + "' 右侧必须是布尔表达式");
                return op == BinaryOp.AND ? new Expr.And(left, right) : new Expr.Or(left, right);
            }
            case EQ:
            case NEQ:
            case GT:
            case LT:
            case GTE:
            case LTE: {
                Expr left = toExpr(bin.left(), table, colsAllowed);
                Expr right = toExpr(bin.right(), table, colsAllowed);
                CmpOp cmp = toCmp(op);
                // 比较操作数不允许是布尔
                if (left.kind().isBoolean() || right.kind().isBoolean()) {
                    throw SemanticException.at(bin, "布尔表达式不能参与 '" + cmp.symbol() + "' 比较");
                }
                // 两侧类型必须一致
                if (left.kind() != right.kind()) {
                    throw SemanticException.at(bin,
                            "类型不匹配: " + left.kind() + " 与 " + right.kind() + " 不能比较");
                }
                // 字符串列只允许 = / <>，不允许大小比较
                if (cmp.isOrder() && left.kind() == ExprType.VARCHAR) {
                    throw SemanticException.at(bin,
                            "VARCHAR 类型不支持 '" + cmp.symbol() + "' 大小比较");
                }
                return new Expr.Cmp(cmp, left, right);
            }
            case PLUS:
            case MINUS:
            case MUL:
            case DIV: {
                Expr left = toExpr(bin.left(), table, colsAllowed);
                Expr right = toExpr(bin.right(), table, colsAllowed);
                if (left.kind() != ExprType.INT || right.kind() != ExprType.INT) {
                    throw SemanticException.at(bin,
                            "算术运算 '" + op.symbol() + "' 只支持 INT 类型");
                }
                return new Expr.Arith(toArith(op), left, right);
            }
            default:
                // BinaryOp 枚举已在上方穷尽
                throw new IllegalArgumentException("未知二元运算符: " + op);
        }
    }

    /** 校验表达式必须为布尔类型，否则以 source 节点位置报错 */
    private Expr requireBoolean(Expression source, TableMeta table, String what) {
        Expr e = toExpr(source, table, true);
        if (e.kind() != ExprType.BOOLEAN) {
            throw SemanticException.at(source,
                    what + " 必须是布尔表达式(比较/NOT/AND/OR), 实际类型为 " + e.kind());
        }
        return e;
    }

    /** 校验“已经翻译出的 IR”必须为布尔类型 */
    private void requireBooleanType(Expr expr, Expression source, String message) {
        if (expr.kind() != ExprType.BOOLEAN) {
            throw SemanticException.at(source,
                    message + ", 实际类型为 " + expr.kind());
        }
    }

    /** 表存在性检查，缺失时报未定义的表 */
    private TableMeta tableOrThrow(Statement stmt, String tableName) {
        TableMeta table = catalog.table(tableName);
        if (table == null) {
            throw SemanticException.at(stmt, "未定义的表 '" + tableName + "'");
        }
        return table;
    }

    // B 的比较运算符 -> C 侧 CmpOp
    private static CmpOp toCmp(BinaryOp op) {
        return switch (op) {
            case EQ -> CmpOp.EQ;
            case NEQ -> CmpOp.NEQ;
            case GT -> CmpOp.GT;
            case LT -> CmpOp.LT;
            case GTE -> CmpOp.GTE;
            case LTE -> CmpOp.LTE;
            default -> throw new IllegalArgumentException("不是比较运算符: " + op);
        };
    }

    // B 的算术运算符 -> C 侧 ArithOp
    private static ArithOp toArith(BinaryOp op) {
        return switch (op) {
            case PLUS -> ArithOp.PLUS;
            case MINUS -> ArithOp.MINUS;
            case MUL -> ArithOp.MUL;
            case DIV -> ArithOp.DIV;
            default -> throw new IllegalArgumentException("不是算术运算符: " + op);
        };
    }
}
