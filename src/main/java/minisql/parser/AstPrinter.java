package minisql.parser;

import minisql.ast.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 把 AST 打成缩进树，答辩演示用。
 */
public class AstPrinter implements AstVisitor<String> {
    private int indent;

    public String print(AstNode node) {
        indent = 0;
        return node.accept(this);
    }

    public String printAll(List<Statement> stmts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stmts.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(print(stmts.get(i)));
        }
        return sb.toString();
    }

    /** 带括号的中缀形式，用来核对优先级 */
    public String toInfix(Expression expr) {
        if (expr instanceof BinaryExpr b) {
            return "(" + toInfix(b.left()) + " " + b.op().symbol() + " " + toInfix(b.right()) + ")";
        }
        if (expr instanceof UnaryExpr u) {
            String op = u.op() == UnaryOp.NOT ? "NOT " : "-";
            return "(" + op + toInfix(u.operand()) + ")";
        }
        if (expr instanceof IdentifierExpr id) {
            return id.name();
        }
        if (expr instanceof LiteralExpr lit) {
            return lit.kind() == LiteralExpr.Kind.STRING ? "'" + lit.raw() + "'" : lit.raw();
        }
        return String.valueOf(expr);
    }

    private String pad() {
        return "  ".repeat(indent);
    }

    private String nested(AstNode node) {
        indent++;
        String s = node.accept(this);
        indent--;
        return s;
    }

    @Override
    public String visitCreateTableStmt(CreateTableStmt stmt) {
        String cols = stmt.columns().stream()
                .map(c -> c.name() + " " + c.type())
                .collect(Collectors.joining(", "));
        return pad() + "CreateTableStmt table=" + stmt.tableName() + " columns=[" + cols + "]";
    }

    @Override
    public String visitInsertStmt(InsertStmt stmt) {
        StringBuilder sb = new StringBuilder();
        sb.append(pad()).append("InsertStmt table=").append(stmt.tableName())
                .append(" columns=").append(stmt.columns());
        indent++;
        for (Expression v : stmt.values()) {
            sb.append('\n').append(v.accept(this));
        }
        indent--;
        return sb.toString();
    }

    @Override
    public String visitSelectStmt(SelectStmt stmt) {
        String list = stmt.star() ? "*" : String.join(", ", stmt.columns());
        StringBuilder sb = new StringBuilder();
        sb.append(pad()).append("SelectStmt list=").append(list)
                .append(" from=").append(stmt.tableName());
        if (stmt.where() != null) {
            sb.append('\n');
            indent++;
            sb.append(pad()).append("where:\n").append(stmt.where().accept(this));
            indent--;
        }
        return sb.toString();
    }

    @Override
    public String visitDeleteStmt(DeleteStmt stmt) {
        StringBuilder sb = new StringBuilder();
        sb.append(pad()).append("DeleteStmt from=").append(stmt.tableName());
        if (stmt.where() != null) {
            sb.append('\n');
            indent++;
            sb.append(pad()).append("where:\n").append(stmt.where().accept(this));
            indent--;
        }
        return sb.toString();
    }

    @Override
    public String visitBinaryExpr(BinaryExpr expr) {
        StringBuilder sb = new StringBuilder();
        sb.append(pad()).append("BinaryExpr ").append(expr.op().symbol()).append('\n');
        sb.append(nested(expr.left())).append('\n');
        sb.append(nested(expr.right()));
        return sb.toString();
    }

    @Override
    public String visitUnaryExpr(UnaryExpr expr) {
        StringBuilder sb = new StringBuilder();
        sb.append(pad()).append("UnaryExpr ").append(expr.op()).append('\n');
        sb.append(nested(expr.operand()));
        return sb.toString();
    }

    @Override
    public String visitIdentifierExpr(IdentifierExpr expr) {
        return pad() + "Identifier " + expr.name();
    }

    @Override
    public String visitLiteralExpr(LiteralExpr expr) {
        String shown = expr.kind() == LiteralExpr.Kind.STRING ? "'" + expr.raw() + "'" : expr.raw();
        return pad() + "Literal " + expr.kind() + " " + shown;
    }
}
