 # MiniSQL Compiler

3 人协作的 MiniSQL 编译器（Java）。流水线：

```
SQL 文本 → Lexer(A) → Token 流 → Parser(B) → AST → Semantic(C) → 检查后 IR → Plan(C) → 优化后逻辑计划
```

当前 **A 词法**、**B 语法+AST**、**C 语义分析 + 执行计划** 均已接入本仓库（C 模块说明见文末「C 部分：语义分析 + 执行计划」）。

## 分工

| 角色 | 模块 | 代码位置 |
| --- | --- | --- |
| A | 词法分析 Lexer | `src/main/java/minisql/lexer/` |
| B | 语法分析 Parser + AST | `src/main/java/minisql/parser/`、`src/main/java/minisql/ast/` |
| C | 语义分析 + Catalog + 逻辑执行计划 + 5 条优化规则 | `minisql/catalog`、`minisql/expr`、`minisql/semantic`、`minisql/plan`、`minisql/optimizer`；门面 `minisql.MiniSqlCompiler`、演示 `minisql.Demo` |

文法见 [`grammar.md`](grammar.md)。

## 环境与运行

- JDK 17+
- Maven 3.x

```bash
cd minisql-compiler
mvn test                          # 全量单测（Lexer + Parser）
mvn exec:java                     # B 的演示：SQL → Token → AST，含语法错误示例
```

IDE 里也可直接运行 `minisql.parser.ParserDemo`。

---

## 给 C：怎么接到 AST

入口类：`minisql.MiniSqlFrontend`

```java
import minisql.MiniSqlFrontend;
import minisql.ast.*;

Statement ast = MiniSqlFrontend.parse(sql);           // 一条语句，结束后必须是 EOF
List<Statement> all = MiniSqlFrontend.parseAll(sql);  // 多条语句
List<Token> tokens = MiniSqlFrontend.tokenize(sql);   // 只要 Token 时用
```

不要自己 new Parser 去猜 Token 列表，统一走上面三个方法。

### 用访问者遍历（语义 / Plan 都走这里）

```java
public class SemanticAnalyzer implements AstVisitor<Void> {
    @Override
    public Void visitSelectStmt(SelectStmt stmt) {
        // stmt.tableName() / stmt.star() / stmt.columns() / stmt.where()
        if (stmt.where() != null) {
            stmt.where().accept(this);
        }
        return null;
    }
    // 其余 visitXxx 同样实现
}

ast.accept(new SemanticAnalyzer());
```

> 说明：仓库中 C 已实现的 `SemanticAnalyzer(Catalog)` 需传入 Catalog（平时直接用门面 `minisql.MiniSqlCompiler` 即可），上例仅为「访问者写法」示意。

`AstVisitor` 需要实现的方法：

- 语句：`visitCreateTableStmt` / `visitInsertStmt` / `visitSelectStmt` / `visitDeleteStmt`
- 表达式：`visitBinaryExpr` / `visitUnaryExpr` / `visitIdentifierExpr` / `visitLiteralExpr`

### AST 字段约定

**CreateTableStmt**

- `tableName()`：表名
- `columns()`：`List<ColumnDef>`，每个有 `name()`、`type()`（`DataType.INT` / `DataType.VARCHAR`）

**InsertStmt**

- `tableName()`、`columns()`（列名列表）、`values()`（`List<Expression>`，目前是字面量）

**SelectStmt**

- `star() == true` 表示 `SELECT *`，此时 `columns()` 为空
- 否则 `columns()` 是选出的列名
- `tableName()`
- `where()`：无 WHERE 时为 **`null`**

**DeleteStmt**

- `tableName()`
- `where()`：无 WHERE 时为 **`null`**

**表达式**

- `BinaryExpr`：`left()` / `op()` / `right()`，`BinaryOp` 含 `AND OR EQ NEQ GT LT GTE LTE PLUS MINUS MUL DIV`
- `UnaryExpr`：`op()` 为 `NOT` 或 `NEGATE`，`operand()`
- `IdentifierExpr`：`name()` 列名
- `LiteralExpr`：`raw()` 文本；`kind()` 为 `NUMBER` 或 `STRING`（**不要把数字和字符串都当成 CONST**）

所有节点都有 `line()`、`col()`，语义报错请带上位置。

### 异常怎么分

| 阶段 | 异常 | 谁抛 |
| --- | --- | --- |
| 词法 | `minisql.lexer.LexicalException` | A |
| 语法 | `minisql.parser.SyntaxException` | B |
| 语义 | `minisql.semantic.SemanticException`（`[语义错误] line..col`） | C |

语法错误示例：

```
[语法错误] line:1, col:44 unexpected token: SEMICOLON(;)
expected: IDENTIFIER | CONST | STRING | LPAREN | NOT
```

### 表达式优先级（答辩 / 优化都要用）

从低到高：`OR` < `AND` < `NOT` < 比较 < `+` `-` < `*` `/`

```
a = 1 OR b = 2 AND c = 3     →  a = 1 OR (b = 2 AND c = 3)
age > 10 + 8                 →  age > (10 + 8)
```

C 做常量折叠时，直接看 `BinaryExpr` 的 `PLUS` 节点即可。

---

## 给 A：Lexer 相对最初版本的改动

B 解析四类 SQL 必须用到这些 Token，已写进 `TokenType` / `Lexer`：

1. 关键字：`CREATE` `TABLE` `INSERT` `INTO` `VALUES` `DELETE` `INT` `VARCHAR`
2. 字符串改为 **`STRING`**，整数仍是 **`CONST`**（方便 C 做类型检查）
3. `!=` 与 `<>` 都是 `NEQ`
4. 算术：`PLUS` `MINUS` `SLASH`（`*` 仍是原来的 `STAR`）

A 原有 SELECT 单测仍然通过。pull 之后请再跑 `mvn test`。若还要补注释 `--` / `/* */`，可以继续加，不要删现有 Token 种类。

---

## C 部分：语义分析 + 执行计划

C 从 B 的 AST 出发，走 `语义分析 → 逻辑计划 → 优化`。语义分析用访问者遍历 AST，产出**已绑定 + 类型检查**的中间 IR，再交给计划生成，因此 C 不修改 A/B 的任何节点定义。

### C 统一入口（复用 B 的 MiniSqlFrontend）

```java
MiniSqlCompiler c = new MiniSqlCompiler();              // 每次会话一个实例（持有 Catalog）
List<CompileResult> rs = c.compileAll(
    "CREATE TABLE student(id INT, name VARCHAR, age INT);" +
    "INSERT INTO student(id, name, age) VALUES (1, 'Alice', 20);" +
    "SELECT id, name FROM student WHERE age > 18;");
```

每个 `CompileResult` 提供：`ok()/error()`（语义错误已封装不抛出）、
`before()/after()`（优化前后计划）、`steps()/stepNames()`（逐步优化快照，答辩用）、
`analyzed()`。`CREATE TABLE` 会登记进 `c.catalog()`，同一实例内后续语句可见（会话状态）。

### C 各包职责

| 包 | 主要类 | 职责 |
| --- | --- | --- |
| `minisql.catalog` | `Catalog` / `TableMeta` / `ColumnMeta` | 会话符号表：建表登记、查表/查列（大小写不敏感） |
| `minisql.expr` | `Expr`(sealed) 等 | C 自有的“已绑定/类型化”表达式 IR（含布尔常量 TRUE/FALSE） |
| `minisql.semantic` | `SemanticAnalyzer` / `Analyzed*` / `SemanticException` | 名字绑定 + 类型检查，产出中间结果 |
| `minisql.plan` | `PlanNode`(Scan/Filter/Project/Insert/Delete) / `PlanBuilder` / `LogicalPlan` | 中间结果 → 未优化逻辑计划 + 树形打印 |
| `minisql.optimizer` | 5 条 `OptimizationRule` + `Optimizer` | 规则改写 + 逐步快照 |
| `minisql`（根） | `MiniSqlCompiler` / `CompileResult` / `Demo` | 门面与演示 |

### 语义 / 类型规则（摘要）

- 类型仅 `INT`、`VARCHAR`；字符串列只允许 `= / <>`，序比较（`< > <= >=`）只允许 INT。
- 算术 `+ - * /`、一元负号只作用于 INT；`AND / OR / NOT` 与比较的结果才是布尔；SELECT/DELETE 的 WHERE 必须是布尔。
- INSERT 必须覆盖目标表全部列、值与列按下标对齐且类型匹配、值不允许引用列。
- 语义错误格式与词法/语法一致：`[语义错误] line:1, col:1 未定义的表 'nosuch_table'`。

### 5 条优化规则（在 Scan/Filter/Project 计划树上按序重写）

| # | 规则 | 作用 |
| --- | --- | --- |
| R1 | 常量折叠 | `1=1 → TRUE`、`age>10+8 → age>18` |
| R2 | 布尔化简 | `TRUE AND x → x`、`FALSE AND x → FALSE`、`x AND NOT x → FALSE`、`NOT NOT x → x`；条件恒真则去掉 Filter |
| R3 | 冗余谓词消除 | 去掉重复的相同谓词（AND/OR 列表） |
| R4 | 谓词下推 | 把 Filter 并入 Scan 的内部过滤，消除独立 Filter 算子 |
| R5 | 投影裁剪 | Scan 只读 `Project 输出列 ∪ 过滤引用列` |

优化示例（`minisql.Demo` 会逐条打印每步优化）：

```
-- 优化前
Project [id, name]
  Filter ((1 = 1) AND (age > (10 + 8)))
    Scan student cols=[id, name, age]

-- 优化后（R1 → R2 → R4）
Project [id, name]
  Scan student cols=[id, name, age] filter: (age > 18)
```

### 运行 C 的演示与测试

```bash
mvn test                                          # 全量单测（含 C：catalog/semantic/plan/optimizer/端到端）
mvn compile && java -cp target/classes minisql.Demo   # C 演示：合法SQL全流程 / 优化对比 / 语义错误
```

> 说明：无参的 `mvn exec:java` 默认运行的是 **B 的 `ParserDemo`**（pom 里配置的 mainClass，`-Dexec.mainClass` 会被它覆盖）。运行 C 演示请用 IDE 直接 Run `minisql.Demo`，或先 `mvn compile` 再执行上面的 `java -cp …`。

演示输入样例见 [`docs/demo.sql`](docs/demo.sql)；答辩 Q&A 见 [`docs/答辩Q&A.md`](docs/答辩Q&A.md)。

### 关键设计说明

- 本编译器产出**逻辑执行计划**并打印，用于演示/答辩；不实现行存储与真实执行。
- C 的表达式 IR（`expr` 包）相对 B 的 AST 独立：因为 B 的 AST 没有“布尔常量 TRUE/FALSE”节点，常量折叠等优化无法直接表达，C 在 IR 侧补上 `Expr.Bool`，因此不必改动 B 的 `ast` 定义。
- 约定保持：合并前 `mvn test` 必须全绿。

## 目录

```
minisql-compiler/
├── grammar.md                          # 文法 + C 阶段说明（验收要交）
├── README.md                           # 协作分工 + C 模块说明
├── docs/                               # C：demo.sql（演示脚本）、答辩Q&A.md
├── .gitignore                          # 忽略 target/、.idea/ 等
├── pom.xml
├── src/main/java/minisql/
│   ├── MiniSqlFrontend.java            # B→C 的统一入口（SQL→AST）
│   ├── MiniSqlCompiler.java            # C：门面（带状态 Catalog，SQL→语义→计划→优化）
│   ├── CompileResult.java              # C：单条语句的编译结果
│   ├── Demo.java                       # C：端到端演示主程序
│   ├── lexer/                          # A：词法分析
│   ├── ast/                            # B：AST 节点 + AstVisitor
│   ├── parser/                         # B：Parser、语法错误、AST 打印
│   ├── catalog/                        # C：Catalog 会话符号表 + 类型元信息
│   ├── expr/                           # C：自有的“已绑定/类型化”表达式 IR
│   ├── semantic/                       # C：名字绑定、类型检查、语义错误
│   ├── plan/                           # C：逻辑计划节点 + PlanBuilder + 计划打印
│   └── optimizer/                      # C：5 条优化规则 + Optimizer
└── src/test/java/minisql/
    ├── lexer/LexerTest.java            # A
    ├── parser/ParserTest.java          # B
    ├── catalog/CatalogTest.java        # C
    ├── semantic/SemanticAnalyzerTest.java  # C
    ├── plan/PlanBuilderTest.java       # C
    ├── optimizer/OptimizerTest.java    # C
    └── MiniSqlCompilerTest.java        # C（端到端）
```

## 请不要做的事

- 不要改 `AstNode` / 各 Stmt 的字段含义（C 依赖这些 getter）
- 不要在 Parser 里写 Catalog、类型检查、Plan（那是 C）
- 合并前先 `mvn test`，不通过不要往主分支合
