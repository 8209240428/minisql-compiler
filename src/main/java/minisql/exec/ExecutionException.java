package minisql.exec;

/**
 * 执行期异常：除零、记录损坏、页满等运行时错误。
 * 与编译期异常（词法/语法/语义）分开，语义错误不在此抛。
 */
public final class ExecutionException extends RuntimeException {

    public ExecutionException(String message) {
        super(message);
    }

    public ExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
