package minisql.exec;

import minisql.CompileResult;
import minisql.catalog.ColumnMeta;

import java.util.List;

/**
 * 一条语句执行完的结果：结果集 / 影响行数 / 提示消息 / 编译错误，四选一。
 */
public final class ExecutionResult {

    private final List<ColumnMeta> columns;   // 结果集（SELECT）时非 null
    private final List<Row> rows;
    private final int affected;               // INSERT/DELETE 影响行数；结果集/消息时 = -1
    private final String message;             // 提示消息（CREATE）时非 null
    private final CompileResult error;        // 编译（语义）失败时非 null

    private ExecutionResult(List<ColumnMeta> columns, List<Row> rows, int affected,
                            String message, CompileResult error) {
        this.columns = columns;
        this.rows = rows;
        this.affected = affected;
        this.message = message;
        this.error = error;
    }

    public static ExecutionResult rows(List<ColumnMeta> columns, List<Row> rows) {
        return new ExecutionResult(List.copyOf(columns), List.copyOf(rows), -1, null, null);
    }

    public static ExecutionResult affected(int n) {
        return new ExecutionResult(null, null, n, null, null);
    }

    public static ExecutionResult message(String msg) {
        return new ExecutionResult(null, null, -1, msg, null);
    }

    public static ExecutionResult error(CompileResult cr) {
        return new ExecutionResult(null, null, -1, null, cr);
    }

    public boolean isError() {
        return error != null;
    }

    public boolean isResultSet() {
        return columns != null;
    }

    public boolean isMessage() {
        return message != null;
    }

    public List<ColumnMeta> columns() {
        return columns;
    }

    public List<Row> rows() {
        return rows;
    }

    public int affected() {
        return affected;
    }

    public String message() {
        return message;
    }

    /** 编译错误消息（带行列号），非错误时为 null */
    public String errorText() {
        return error == null ? null : error.error().getMessage();
    }
}
