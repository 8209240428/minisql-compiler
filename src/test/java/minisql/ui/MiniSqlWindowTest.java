package minisql.ui;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.awt.GraphicsEnvironment;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Swing 窗口的“能建起来”冒烟测试：在无图形环境（headless CI）里自动跳过，
 * 有桌面环境时确认窗口与内部组件能正常构造、不抛异常。
 */
class MiniSqlWindowTest {

    @Test
    void windowCanBeConstructed() {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "无图形环境，跳过 Swing 冒烟");

        MiniSqlWindow w = new MiniSqlWindow();
        assertNotNull(w);
        w.dispose();
    }
}
