package minisql.plan;

import minisql.expr.Expr;

/**
 * 过滤算子：对子计划（单表场景下即 Scan）逐行套用布尔谓词。
 *
 * <p>这是未优化计划里位于 Project 与 Scan 之间的节点，
 * 优化规则 R4（谓词下推）会把它合并进 ScanNode，届时该节点消失。
 */
public record FilterNode(PlanNode child, Expr predicate) implements PlanNode {
    /** 简化/改写谓词后重建节点 */
    public FilterNode withPredicate(Expr newPredicate) {
        return new FilterNode(child, newPredicate);
    }
}
