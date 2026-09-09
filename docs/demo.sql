-- =====================================================================
-- MiniSQL 演示 / 联调用 SQL 脚本（C 成员整理）
-- 说明：
--   1) 本脚本是「输入样例 + 期望输出注释」，可直接用 minisql.Demo 的
--      三个演示段替换，或经 MiniSqlCompiler.compileAll(...) 逐条编译。
--   2) 当前编译器产出「逻辑执行计划」并打印（见每段注释），不做真实执行。
--   3) 每段建议在答辩现场顺序展示：合法 → 优化 → 错误。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 演示一：合法 SQL 全流程（Token → AST → 语义 → 计划 → 优化后计划）
-- ---------------------------------------------------------------------

-- 建表：语义阶段把它登记进 Catalog（同一会话内后续语句可见）
CREATE TABLE student(id INT, name VARCHAR, age INT, score INT);

-- 插入：必须覆盖 student 全部列，值类型与列一致（1=INT, 'Alice'=VARCHAR…）
INSERT INTO student(id, name, age, score) VALUES (1, 'Alice', 20, 90);

-- 查询：WHERE 是布尔；预计优化后 Scan 列被裁剪为投影+过滤用列
SELECT id, name FROM student WHERE age >= 20 AND score > 80;

-- 删除：WHERE 布尔
DELETE FROM student WHERE id = 1;

-- ---------------------------------------------------------------------
-- 演示二：优化规则（展示优化前 / 每步 / 优化后 的计划对比）
-- 讲解口径：
--   WHERE 1=1 AND age>10+8
--     → R1 常量折叠：1=1→TRUE、10+8→18
--     → R2 布尔化简：TRUE AND age>18 → age>18
--     → R4 谓词下推：Filter 并入 Scan
--   （R5 投影裁剪在本表只输 id,name 时可见）
-- ---------------------------------------------------------------------

CREATE TABLE student(id INT, name VARCHAR, age INT);

SELECT id, name FROM student WHERE 1 = 1 AND age > 10 + 8;

-- 演示重复谓词消除（R2/R3）与投影裁剪（R5）：
--   age>20 AND age>20 → age>20；Scan 只读 [name, age]
SELECT name FROM student WHERE age > 20 AND age > 20;

-- ---------------------------------------------------------------------
-- 演示三：非法 SQL 的语义错误提示（都应报 [语义错误] line..col）
-- ---------------------------------------------------------------------

CREATE TABLE student(id INT, name VARCHAR, age INT);

SELECT id FROM nosuch_table;              -- 未定义的表 'nosuch_table'
SELECT no_such_col FROM student;           -- 未定义的列 'no_such_col'
SELECT * FROM student WHERE name > 'a';    -- VARCHAR 不支持大小比较
SELECT * FROM student WHERE age >= '18';   -- 类型不匹配: INT 与 VARCHAR
SELECT * FROM student WHERE age;           -- WHERE 条件必须是布尔
INSERT INTO student(id, name, age) VALUES (1, 20, 'x');   -- 值类型不匹配
DELETE FROM student WHERE age;             -- DELETE 的 WHERE 非布尔
CREATE TABLE student(id INT, x INT);       -- 表已存在

-- =====================================================================
-- 运行建议：
--   mvn test                                        # 全量单测全绿
--   mvn compile && java -cp target/classes minisql.Demo   # 三段式现场演示
-- 或 IDE 直接运行 minisql.Demo。
-- 注：无参 `mvn exec:java` 默认跑 B 的 ParserDemo（pom 默认 mainClass），
--      `-Dexec.mainClass=minisql.Demo` 会被它覆盖，故 C 演示用上面方式。
-- =====================================================================
