package minisql.ast;

/**
 * AST 节点公共接口。
 * Parser 的产物，也是 Semantic / Plan 的稳定输入（访问者模式）。
 */
public interface AstNode {
    int line();

    int col();

    <R> R accept(AstVisitor<R> visitor);
}
