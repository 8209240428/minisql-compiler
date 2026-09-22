package minisql.exec;

import minisql.catalog.ColumnMeta;
import minisql.expr.Expr;
import minisql.plan.DeleteNode;
import minisql.plan.FilterNode;
import minisql.plan.InsertNode;
import minisql.plan.PlanNode;
import minisql.plan.ProjectNode;
import minisql.plan.ScanNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Volcano 风格执行器：逐算子执行优化后的逻辑计划。
 */
public final class Executor {

    private Executor() {
    }

    public static ExecutionResult execute(PlanNode plan, Database db) {
        if (plan instanceof ScanNode s) {
            TableHeap heap = db.heapFor(s.table());
            List<Row> rows = heap.scan(s.columns());
            List<Row> out = new ArrayList<>();
            for (Row r : rows) {
                if (s.filter() == null || Boolean.TRUE.equals(ExprEvaluator.eval(s.filter(), r))) {
                    out.add(r);
                }
            }
            return ExecutionResult.rows(s.columns(), out);
        }
        if (plan instanceof FilterNode f) {
            ExecutionResult child = execute(f.child(), db);
            List<Row> out = new ArrayList<>();
            for (Row r : child.rows()) {
                if (Boolean.TRUE.equals(ExprEvaluator.eval(f.predicate(), r))) {
                    out.add(r);
                }
            }
            return ExecutionResult.rows(child.columns(), out);
        }
        if (plan instanceof ProjectNode p) {
            ExecutionResult child = execute(p.child(), db);
            List<Row> out = new ArrayList<>();
            for (Row r : child.rows()) {
                out.add(r.project(p.outputs()));
            }
            return ExecutionResult.rows(p.outputs(), out);
        }
        if (plan instanceof InsertNode i) {
            TableHeap heap = db.heapFor(i.table());
            List<ColumnMeta> schema = i.table().columns();
            Object[] cells = new Object[schema.size()];
            for (int k = 0; k < i.columns().size(); k++) {
                ColumnMeta col = i.columns().get(k);
                Expr val = i.values().get(k);
                cells[schema.indexOf(col)] = ExprEvaluator.eval(val, null);
            }
            List<Object> cellList = new ArrayList<>(schema.size());
            for (Object c : cells) {
                cellList.add(c);
            }
            heap.insert(new Row(schema, cellList, null));
            return ExecutionResult.affected(1);
        }
        if (plan instanceof DeleteNode d) {
            ExecutionResult child = execute(d.child(), db);
            TableHeap heap = db.heapFor(d.table());
            int n = 0;
            for (Row r : child.rows()) {
                if (r.rid() != null) {
                    heap.delete(r.rid());
                    n++;
                }
            }
            return ExecutionResult.affected(n);
        }
        throw new ExecutionException("无法执行的计划节点: " + plan);
    }
}
