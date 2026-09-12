package minisql.plan;

import minisql.MiniSqlFrontend;
import minisql.TestCatalogFactory;
import minisql.catalog.Catalog;
import minisql.catalog.TableMeta;
import minisql.semantic.Analyzed;
import minisql.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlanBuilder：语义产物 → 未优化逻辑计划 的结构测试。
 */
class PlanBuilderTest {

    private LogicalPlan build(String sql) {
        Catalog c = TestCatalogFactory.student();
        Analyzed analyzed = new SemanticAnalyzer(c).analyze(MiniSqlFrontend.parse(sql));
        return PlanBuilder.build(analyzed);
    }

    @Test
    void selectWithoutWhereIsProjectOverScan() {
        LogicalPlan plan = build("SELECT id FROM student;");
        PlanNode root = plan.root();
        assertInstanceOf(ProjectNode.class, root);
        ProjectNode proj = (ProjectNode) root;
        assertEquals(1, proj.outputs().size());
        // 无 WHERE → 不应出现 Filter 节点
        assertInstanceOf(ScanNode.class, proj.child());
        assertNull(((ScanNode) proj.child()).filter());
    }

    @Test
    void selectWithWhereHasFilterBetweenProjectAndScan() {
        LogicalPlan plan = build("SELECT name FROM student WHERE age > 18;");
        ProjectNode proj = (ProjectNode) plan.root();
        assertInstanceOf(FilterNode.class, proj.child());
        FilterNode filter = (FilterNode) proj.child();
        assertInstanceOf(ScanNode.class, filter.child());
        assertNotNull(filter.predicate());
    }

    @Test
    void selectStarExpandsToAllTableColumns() {
        LogicalPlan plan = build("SELECT * FROM student;");
        ProjectNode proj = (ProjectNode) plan.root();
        assertEquals(TestCatalogFactory.studentColumns(),
                proj.outputs().stream().map(c -> c.name()).toList());
    }

    @Test
    void deleteRootIsDeleteNodeOverScanChain() {
        LogicalPlan plan = build("DELETE FROM student WHERE id = 1;");
        assertInstanceOf(DeleteNode.class, plan.root());
        DeleteNode del = (DeleteNode) plan.root();
        TableMeta table = del.table();
        assertEquals("student", table.name());
        assertInstanceOf(FilterNode.class, del.child());
        assertInstanceOf(ScanNode.class, ((FilterNode) del.child()).child());
    }

    @Test
    void insertProducesLeafInsertNode() {
        LogicalPlan plan = build("INSERT INTO student(id, name, age) VALUES (1, 'A', 2);");
        assertInstanceOf(InsertNode.class, plan.root());
        InsertNode ins = (InsertNode) plan.root();
        assertEquals(3, ins.columns().size());
        assertEquals(3, ins.values().size());
    }
}
