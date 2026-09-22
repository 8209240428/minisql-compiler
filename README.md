# MiniSQL Database（Java）

3 人协作的 MiniSQL 数据库（Java），从零实现 **词法 → 语法 → 语义 → 逻辑执行计划 → 优化 → 执行 → 存储 → 磁盘** 的端到端流水线，
既有 Swing 交互界面，也有命令行终端（CLI）。产品不仅产出并可视化**逻辑执行计划**，还实现了**真实的行存储与执行**：
页式存储管理、LRU 缓冲池、记录/目录序列化、系统目录持久化、Volcano 执行引擎，数据落盘到 `.db` 文件，重开即恢复。

```
SQL 文本 → Lexer(A) → Token 流 → Parser(B) → AST → Semantic(C) → 检查后 IR → Plan(C) → 优化后逻辑计划
        → Executor(C) → 执行结果 → TableHeap(C) → BufferPool(C) LRU → DiskManager(C) → *.db
```

| 角色 | 原有职责 | 新增（存储 + 执行） |
| --- | --- | --- |
| A | 词法分析 Lexer（Token/行列号） | **系统集成**：`Database` 门面（打开/恢复/关闭）+ `Cli`（REPL/脚本） |
| B | 语法分析 Parser + AST | **记录与目录序列化**：`Row`/`RowCodec`、`SchemaCodec`、`CatalogStore` |
| C | 语义 + Catalog + Plan + 5 条优化规则 | **页式存储 + LRU 缓存 + 执行引擎**：`Page`/`DiskManager`/`BufferPool`/`SlottedPage` + `Rid`/`TableHeap`/`ExprEvaluator`/`Executor` |

文法与各模块接口细节见 [`grammar.md`](grammar.md)；演示脚本见 [`docs/demo.sql`](docs/demo.sql)；
答辩高频问答见 [`docs/答辩Q&A.md`](docs/答辩Q&A.md)。

---

## 快速开始

- **JDK 17+**（JDK 8 跑不起来；类文件按 Java 17 编译）
- 构建用 **Maven Wrapper**：仓库自带 `mvnw`（Mac/Linux/Git Bash）与 `mvnw.cmd`（Windows cmd），
  首次运行会自动下载 Maven 3.9.9 到 `~/.m2/wrapper`，机器上**无需预装 Maven**。

### 打开交互界面（Swing）

> **最简单方式：Windows 下直接双击根目录的 `start-gui.bat`**
> —— 自动定位 JDK 17+ → 用 Wrapper 编译 → 弹出窗口，全程无需命令行。
> 已装 Maven 或想手动跑：`./mvnw compile && java -cp target/classes minisql.ui.SwingApp`。

```bash
cd minisql-compiler
./mvnw test                       # 全量单测（lexer/parser/catalog/semantic/plan/optimizer/storage/exec/ui，114 项）
./mvnw compile && java -cp target/classes minisql.parser.ParserDemo   # B：SQL→Token→AST
./mvnw compile && java -cp target/classes minisql.Demo                # C：全流程 / 优化对比 / 错误
./mvnw compile && java -cp target/classes minisql.ui.SwingApp         # 交互界面（Swing）
./mvnw compile && java -cp target/classes minisql.exec.Cli            # 命令行终端（数据落在 data.db）
./mvnw compile && java -cp target/classes minisql.exec.Cli data.db demo.sql   # 脚本模式（UTF-8）
./mvnw -Pcoverage verify          # 覆盖率报告（需联网）→ target/site/jacoco/index.html
```

- Windows cmd 把 `./mvnw` 换成 `.\mvnw.cmd`；装了 Maven 也可直接用 `mvn`。
- IDE 里直接 Run 对应类即可：`minisql.parser.ParserDemo` / `minisql.Demo` / `minisql.ui.SwingApp` / `minisql.exec.Cli`。
- 在 Windows 命令行跑 A/B 演示若中文乱码：先 `chcp 65001`，或加 `-Dfile.encoding=UTF-8`（Swing 窗口与 CLI 不受影响）。

**命令行终端（CLI）**：`minisql.exec.Cli` 是「SQL 输入 → 编译 → 执行 → 存储 → 结果返回」的完整链路入口。
REPL 里 `exit`/`quit` 退出；退出时回写缓存并落盘，下次打开同一 `.db` 文件即恢复数据：

```
minisql> CREATE TABLE student(id INT, name VARCHAR, age INT);
CREATE TABLE 成功
minisql> INSERT INTO student(id,name,age) VALUES (1,'Alice',20);
1 行受影响
minisql> SELECT id,name FROM student WHERE age >= 18;
id | name
---------
1 | Alice
(1 行)
minisql> exit
```

---

## 支持的语句与文法

- 语句：`CREATE TABLE` / `INSERT INTO … VALUES` / `SELECT … FROM … [WHERE]` / `DELETE FROM … [WHERE]`
- 类型：仅 `INT`、`VARCHAR`（字符串用单引号，如 `'Alice'`）
- 表达式优先级（由文法层级保证，从低到高）：
  `OR` < `AND` < `NOT` < 比较 `< > <= >= = <>` < `+` `-` < `*` `/` < 一元负号 / 括号

```
a = 1 OR b = 2 AND c = 3     →  a = 1 OR (b = 2 AND c = 3)
age > 10 + 8                 →  age > (10 + 8)     （便于常量折叠）
```

---

## 语义与类型规则

- 列与常量只有 `INT` / `VARCHAR`；布尔由比较、`AND`/`OR`/`NOT` 产生。
- 字符串列只允许 `=` / `<>`；序比较（`< > <= >=`）与算术只允许 `INT`。
- `SELECT`/`DELETE` 的 WHERE 整体必须是布尔；INSERT 值必须是常量、类型与列一致、不允许引用列。
- INSERT 必须覆盖目标表**全部列**，值按列下标对齐。
- 错误统一带行列号，格式对齐三段：

| 阶段 | 异常 | 示例 |
| --- | --- | --- |
| 词法 | `[词法错误] line:L, col:C …` | 字符串未闭合、非法字符 `@` |
| 语法 | `[语法错误] line:L, col:C unexpected token: …` | 缺分号、`SELEC` 拼错 |
| 语义 | `[语义错误] line:L, col:C …` | 未定义的表/列、类型不匹配、WHERE 非布尔 |

## 5 条优化规则（`minisql.optimizer`）

| # | 规则 | 作用 | 例 |
| --- | --- | --- | --- |
| R1 | 常量折叠 | 纯常量子表达式编译期求值 | `1=1→TRUE`、`age>10+8→age>18` |
| R2 | 布尔化简 | TRUE/FALSE 恒等吸收、双 NOT、`x AND NOT x` | `TRUE AND x→x`；恒真→删 Filter |
| R3 | 冗余谓词消除 | AND/OR 去重复谓词 | `p AND p→p` |
| R4 | 谓词下推 | Filter 并入 Scan 内部过滤 | 消除独立 Filter 算子 |
| R5 | 投影裁剪 | Scan 只读 `Project输出 ∪ 过滤引用列` | 见下 |

```
-- 优化前                               -- 优化后（R1→R2→R4）
Project [id]                           Project [id]
  Filter ((1 = 1) AND (age > (10 + 8)))  Scan student cols=[id, age] filter: (age > 18)
    Scan student cols=[id, name, age]
```

`minisql.Demo` 会把每次规则改写的中间计划逐步打印出来。

---

## 存储引擎与执行引擎（`minisql.storage` / `minisql.exec`）

在优化后的逻辑计划之上，新增两层让数据库**真正能读写、可持久化**：

**存储层 `minisql.storage`（C）——页式存储 + LRU 缓存，零依赖其它 `minisql.*` 包：**

- `Page` / `PageId` / `PageType`：4096 字节定长页、大端读写访问器。
- `SlottedPage`：槽目录 + 变长记录的槽页；删除只打墓碑不压缩；`nextPageId` 串成页链。
- `DiskManager`：单文件页级 I/O；第 0 页文件头（magic `MSDB` + pageSize + catalogRoot + freeListHead）；空闲页链表。
- `BufferPool`：`LinkedHashMap(accessOrder)` 实现 LRU；`getPage/unpin/newPage/deletePage/flushAll`；带命中/淘汰/回写计数。
- **统一接口**：上层只经 `getPage()` / `newPage()` / `unpin()` 访问页，不直接碰磁盘。

**执行层 `minisql.exec`（A + B + C）：**

- **B 序列化**：`RowCodec`（行↔字节：INT=8B long、VARCHAR=2B 长度+UTF-8；`decodeSubset` 按需解码）、
  `SchemaCodec`（表结构 + 数据根页 ↔ 目录记录）、`CatalogStore`（目录页持久化 / 重载）。
- **C 执行**：`TableHeap`（单表堆 insert/scan/delete）、`ExprEvaluator`（IR 按行求值：整数除法、除零抛错、短路）、
  `Executor`（Volcano 风格：Scan/Filter/Project/Insert/Delete 真实执行）。
- **A 集成**：`Database`（打开 → 载入目录 → 恢复；`run(Statement)` 走「编译 → 执行 → 存储 → 结果」）、
  `Cli`（`java -cp target/classes minisql.exec.Cli data.db [script.sql]`）。

```
INSERT → 按表定义序落行 → RowCodec 编码 → TableHeap → SlottedPage → BufferPool(LRU) → DiskManager → *.db
SELECT → TableHeap 扫描(带 Rid) → Filter/Project 算子 → ExecutionResult → 打印结果集
重启  → DiskManager.open 校验 → CatalogStore.loadAll 重建 Catalog → 数据恢复
```

---

## 模块接口约定（给继续开发 / 接手的人）

**统一入口**（不要自己 new `Parser`）：

```java
Statement ast = MiniSqlFrontend.parse(sql);           // 一条语句，结束后必须 EOF
List<Statement> all = MiniSqlFrontend.parseAll(sql);  // 多条语句
List<Token> tokens = MiniSqlFrontend.tokenize(sql);   // 只要 Token
```

**语义 / 计划都走访问者**：`AstNode.accept(AstVisitor)`，实现
`visitCreateTableStmt/visitInsertStmt/visitSelectStmt/visitDeleteStmt` 与
`visitBinaryExpr/visitUnaryExpr/visitIdentifierExpr/visitLiteralExpr`。

**AST 关键字段**（都是只读 getter，别改含义）：

- `CreateTableStmt.tableName()` / `.columns()` → `List<ColumnDef>`（`name()`、`type()`=INT|VARCHAR）
- `InsertStmt.tableName()` / `.columns()` / `.values()`
- `SelectStmt.star()`（true 时 `.columns()` 为空）/ `.tableName()` / `.where()`（无 WHERE 为 **null**）
- `DeleteStmt.tableName()` / `.where()`（无 WHERE 为 **null**）
- 表达式：`BinaryExpr(left, op, right)`、`UnaryExpr(op=NOT|NEGATE, operand)`、
  `IdentifierExpr(name)`、`LiteralExpr(raw, kind=NUMBER|STRING)`——**数字与字符串用 kind 区分，不都叫 CONST**
- 所有 AST 节点都有 `line()` / `col()`，报错用它定位。

**Lexer Token 契约**（`TokenType`）：关键字 `CREATE TABLE INSERT INTO VALUES DELETE INT VARCHAR`
`SELECT FROM WHERE AND OR NOT`；数字→`CONST`、字符串→`STRING`；比较 `=` `<>`/`!=` `>` `>=` `<` `<=`；
算术 `+` `-` `*`(STAR) `/`(SLASH)；分隔 `, ( ) ;`；末尾必有 `EOF`。当前不支持 `--`/`/* */` 注释（可自行扩展，别删现有 Token 种类）。

---

## 交互界面（`minisql/ui` · Swing）

把整条流水线可视化，供答辩 / 日常试用。属**初步版**：编译逻辑与界面分层，方便后续换皮。

- 输入区写 SQL（多条用 `;` 分隔、末句分号可省），Ctrl+Enter 或点「运行」。
- 结果分 4 个 Tab：**Token 流 / AST / 执行计划与优化（逐步改写）/ 运行摘要**。
- 顶部按钮：重置会话（清空 Catalog）、清空输入、示例·全流程 / 优化对比 / 常见错误（点击先重置再运行，演示自洽）。
- 容错：词法错误整段报出；某一句语法错误只跳过该句，后续语句继续编译且能看到之前 CREATE 的表。

**后续迭代的分层约定**

| 层 | 类 | 职责 | 接手者改这里 |
| --- | --- | --- | --- |
| 会话模型 | `Workbench` | 非 GUI；`run(sql)`→结构化 `Report`；容错与会话状态 | 编译逻辑、加新能力 |
| 文本渲染 | `ReportText` | `Report`→纯文本 | 换展示文案 / 网页输出 |
| 窗口 | `MiniSqlWindow`/`SwingApp` | SQL 放进去、文本塞进 Tab | 布局 / 配色 / 图标 / 快捷键，或整窗重写 |

不要碰 `lexer / parser / semantic / plan / optimizer` —— 那是编译器本体（A/B/C 的成果）。

---

## 代码仓库与覆盖率

- 分支：`main`（稳定）；按分工建 `dev-a-lexer` / `dev-b-parser` / `dev-c-plan`（基线）；交互界面在 `dev-ui`
  上开发并合回 `main`。合并前必须 `./mvnw test` 全绿。
- 覆盖率：默认不开以保持离线可构建；需要时联网 `./mvnw -Pcoverage verify`，或 IntelliJ 自带 Coverage 运行器。

---

## 目录

```
minisql-compiler/
├── start-gui.bat                    # Windows 双击启动交互界面
├── mvnw / mvnw.cmd                  # Maven Wrapper（无需预装 Maven）
├── .mvn/wrapper/                    # wrapper 配置（distributionUrl=3.9.9）
├── grammar.md                       # 文法 + 各模块接口说明（验收要交）
├── README.md                        # 本文档
├── docs/                            # demo.sql（演示脚本）、答辩Q&A.md
├── .gitattributes / .gitignore
├── pom.xml                          # JDK17；exec 默认 mainClass=ParserDemo；可选 coverage profile
└── src/
    ├── main/java/minisql/
    │   ├── MiniSqlFrontend.java     # B→C 统一入口（SQL→AST）
    │   ├── MiniSqlCompiler.java     # C 门面（带状态 Catalog）
    │   ├── CompileResult.java       # C 单条语句编译结果
    │   ├── Demo.java                # C 端到端演示
    │   ├── lexer/ ast/ parser/      # A / B
    │   ├── catalog/ expr/ semantic/ plan/ optimizer/   # C
    │   ├── storage/                 # C：页式存储 + LRU（Page/DiskManager/BufferPool/SlottedPage）
    │   ├── exec/                    # A+B+C：Database/Cli + RowCodec/CatalogStore + TableHeap/ExprEvaluator/Executor
    │   └── ui/                      # Workbench + ReportText + MiniSqlWindow + SwingApp
    └── test/java/minisql/           # 每个模块对应用例 + storage/exec + ui（114 项全绿）
```

## 协作注意（不要做的事）

- 不要改 `AstNode` / 各 Stmt 字段含义（语义与界面都依赖这些 getter）。
- 不要在 Parser 里写 Catalog、类型检查、Plan（那是 C）。
- 合并前先 `./mvnw test`，不通过不要往主分支合。
