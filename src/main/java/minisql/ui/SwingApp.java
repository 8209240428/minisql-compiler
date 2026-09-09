package minisql.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * 交互界面的启动入口。
 *
 * <p>运行方式（任选其一）：
 * <pre>
 * mvn -q compile
 * java -cp target/classes minisql.ui.SwingApp          # 命令行直接跑
 * mvn -q exec:java -Dexec.mainClass=minisql.ui.SwingApp
 * </pre>
 * 或 IDE 里直接 Run {@code minisql.ui.SwingApp}。
 */
public final class SwingApp {

    private SwingApp() {
    }

    public static void main(String[] args) {
        applyLookAndFeel();
        SwingUtilities.invokeLater(() -> new MiniSqlWindow().setVisible(true));
    }

    /** 优先 Nimbus，失败则退回系统外观（本类被 headless 单测实例化时不应调用）。 */
    private static void applyLookAndFeel() {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    return;
                }
            }
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 外观只是装饰，失败保留默认
        }
    }
}
