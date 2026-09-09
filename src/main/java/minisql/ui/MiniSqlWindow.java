package minisql.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * MiniSQL 编译器交互窗口（Swing）。<b>初步版</b>——约定见下，方便后续由他人迭代美化。
 *
 * <p><b>怎么接手迭代（给后续 UI 负责人）</b>：
 * <ol>
 *   <li>别动 {@link Workbench}/{@link ReportText}（编译逻辑与文本渲染都在这两层，可用 JUnit 覆盖）；</li>
 *   <li>本类只做“把 SQL 放进 {@link Workbench#run} → 把文本塞进 4 个 Tab”。要改布局/配色/图标，
 *       只改本类（或替换成新的窗口类），实现任意 {@code (Workbench)→视图} 即可；</li>
 *   <li>想展示更多信息，先看 {@link minisql.CompileResult} 与 {@link minisql.plan.LogicalPlan} 暴露了什么。</li>
 * </ol>
 *
 * <p>功能：输入 SQL（多条用 {@code ';'} 分隔）→「运行」→ 分 4 个 Tab 看
 * Token 流 / AST / 优化前后执行计划 / 运行摘要；支持 Ctrl+Enter、重置会话、载入示例。
 */
public final class MiniSqlWindow extends JFrame {

    // —— 会话与结果 ——
    private Workbench workbench = new Workbench();

    // —— 控件 ——
    private JTextArea input;
    private JTextArea tokenArea;
    private JTextArea astArea;
    private JTextArea planArea;
    private JTextArea summaryArea;
    private JLabel status;

    public MiniSqlWindow() {
        super("MiniSQL 编译器 · 交互演示");
        buildUi();
        // 初始展示使用说明
        summaryArea.setText(ReportText.HELP);
        setStatus("就绪 · 会话表：无");
    }

    // ------------------------------------------------------------------
    // 界面搭建
    // ------------------------------------------------------------------

    private void buildUi() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(6, 6));
        ((JPanel) getContentPane()).setBorder(new EmptyBorder(8, 8, 8, 8));

        add(buildToolbar(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // Ctrl+Enter 运行
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()),
                        "run");
        getRootPane().getActionMap().put("run", new RunAction("run"));

        setSize(1024, 760);
        centerOnScreen();
    }

    private JComponent buildToolbar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JButton run = new JButton("▶ 运行  (Ctrl+Enter)");
        run.addActionListener(e -> runNow());
        bar.add(run);

        JButton reset = new JButton("重置会话");
        reset.setToolTipText("清空已登记的表，相当于新开一个会话");
        reset.addActionListener(e -> {
            workbench.reset();
            summaryArea.setText(ReportText.HELP);
            tokenArea.setText("");
            astArea.setText("");
            planArea.setText("");
            setStatus("已重置会话 · 会话表：无");
        });
        bar.add(reset);

        JButton clear = new JButton("清空输入");
        clear.addActionListener(e -> input.setText(""));
        bar.add(clear);

        bar.addSeparator();

        bar.add(exampleButton("示例·全流程", FULL_FLOW));
        bar.add(exampleButton("示例·优化对比", OPTIMIZE));
        bar.add(exampleButton("示例·常见错误", ERRORS));

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(bar, BorderLayout.NORTH);

        // SQL 输入区
        input = new JTextArea(9, 60);
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        input.setLineWrap(false);
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setBorder(BorderFactory.createTitledBorder("输入 SQL（多条用 ; 分隔，末句分号可省）"));
        north.add(inputScroll, BorderLayout.CENTER);
        return north;
    }

    private JButton exampleButton(String label, String sql) {
        JButton b = new JButton(label);
        b.addActionListener(e -> {
            workbench.reset();          // 示例自洽：先重置再运行，避免上一段留下的表冲突
            input.setText(sql);
            runNow();
        });
        return b;
    }

    private JTabbedPane buildCenter() {
        tokenArea = newOutputArea();
        astArea = newOutputArea();
        planArea = newOutputArea();
        summaryArea = newOutputArea();

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Token 流", new JScrollPane(tokenArea));
        tabs.addTab("AST", new JScrollPane(astArea));
        tabs.addTab("执行计划与优化", new JScrollPane(planArea));
        tabs.addTab("运行摘要", new JScrollPane(summaryArea));
        return tabs;
    }

    private JTextArea newOutputArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setLineWrap(false);
        return area;
    }

    private JPanel buildStatusBar() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
        status = new JLabel();
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        p.add(status);
        return p;
    }

    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((screen.width - getWidth()) / 2, Math.max(60, (screen.height - getHeight()) / 2));
    }

    // ------------------------------------------------------------------
    // 运行
    // ------------------------------------------------------------------

    private class RunAction extends javax.swing.AbstractAction {
        RunAction(String name) {
            super(name);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            runNow();
        }
    }

    private void runNow() {
        String sql = input.getText();
        if (sql == null || sql.isBlank()) {
            tokenArea.setText("");
            astArea.setText("");
            planArea.setText("");
            summaryArea.setText(ReportText.HELP);
            setStatus("请输入 SQL");
            return;
        }
        final Workbench.Report report;
        try {
            report = workbench.run(sql);
        } catch (RuntimeException ex) {
            tokenArea.setText("");
            astArea.setText("");
            planArea.setText("");
            summaryArea.setText("编译过程抛出未预期异常（可能内部 Bug）：\n" + ex);
            setStatus("异常：" + ex.getClass().getSimpleName());
            return;
        }

        tokenArea.setText(ReportText.tokens(report));
        astArea.setText(ReportText.ast(report));
        planArea.setText(ReportText.plans(report));
        summaryArea.setText(ReportText.summary(report, workbench.tableNames()));

        List<String> tables = workbench.tableNames();
        setStatus(String.format("语句 %d · 失败 %d · 会话表：%s",
                report.entries().size(), report.failureCount(),
                tables.isEmpty() ? "无" : String.join(", ", tables)));
    }

    private void setStatus(String text) {
        status.setText(text);
    }

    // ------------------------------------------------------------------
    // 内置示例（与 docs/demo.sql 三大段一致；不含注释，可直接被词法器解析）
    // ------------------------------------------------------------------

    private static final String FULL_FLOW =
            "CREATE TABLE student(id INT, name VARCHAR, age INT, score INT);\n"
                    + "INSERT INTO student(id, name, age, score) VALUES (1, 'Alice', 20, 90);\n"
                    + "SELECT id, name FROM student WHERE age >= 20 AND score > 80;\n"
                    + "DELETE FROM student WHERE id = 1;";

    private static final String OPTIMIZE =
            "CREATE TABLE student(id INT, name VARCHAR, age INT);\n"
                    + "SELECT id, name FROM student WHERE 1 = 1 AND age > 10 + 8;\n"
                    + "SELECT name FROM student WHERE age > 20 AND age > 20 AND age > 5;";

    private static final String ERRORS =
            "CREATE TABLE student(id INT, name VARCHAR, age INT);\n"
                    + "SELECT id FROM nosuch_table;\n"
                    + "SELECT no_such_col FROM student;\n"
                    + "SELECT * FROM student WHERE name > 'a';\n"
                    + "SELECT * FROM student WHERE age >= '18';\n"
                    + "SELECT * FROM student WHERE age;\n"
                    + "INSERT INTO student(id, name, age) VALUES (1, 20, 'x');\n"
                    + "DELETE FROM student WHERE age;\n"
                    + "CREATE TABLE student(id INT, other INT);";
}
