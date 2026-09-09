# MiniSQL 文法（成员 B）

Parser 实现必须与本文法一致。分析策略：递归下降 / LL(1) 风格，每个非终结符对应 `Parser` 中一个方法。

终结符与词法 Token 对应：`SELECT`、`FROM`、`WHERE`、`CREATE`、`TABLE`、`INSERT`、`INTO`、`VALUES`、`DELETE`、`INT`、`VARCHAR`、`IDENTIFIER`、`CONST`（整数）、`STRING`（单引号字符串）、`*`、`,`、`(`、`)`、`;`、比较符、`AND` / `OR` / `NOT`，以及扩展算术 `+ - * /`。

## 语句

```
program         -> statement { statement }

statement       -> create_stmt | insert_stmt | select_stmt | delete_stmt

select_stmt     -> SELECT select_list FROM IDENTIFIER where_opt ';'
select_list     -> '*' | IDENTIFIER { ',' IDENTIFIER }
where_opt       -> WHERE expression | ε

delete_stmt     -> DELETE FROM IDENTIFIER where_opt ';'

create_stmt     -> CREATE TABLE IDENTIFIER '(' column_def { ',' column_def } ')' ';'
column_def      -> IDENTIFIER type
type            -> INT | VARCHAR

insert_stmt     -> INSERT INTO IDENTIFIER '(' id_list ')' VALUES '(' value_list ')' ';'
id_list         -> IDENTIFIER { ',' IDENTIFIER }
value_list      -> expression { ',' expression }
```

## 表达式（优先级由文法层级保证）

优先级从低到高：`OR` < `AND` < `NOT` < 比较 < `+` `-` < `*` `/` < 一元负号 / 主键。

```
expression      -> or_expr

or_expr         -> and_expr { OR and_expr }
and_expr        -> not_expr { AND not_expr }
not_expr        -> NOT not_expr | comparison

comparison      -> additive [ comp_op additive ]
comp_op         -> '=' | '<>' | '!=' | '>' | '>=' | '<' | '<='

additive        -> multiplicative { ('+' | '-') multiplicative }
multiplicative  -> unary { ('*' | '/') unary }
unary           -> '-' unary | primary

primary         -> IDENTIFIER | CONST | STRING | '(' expression ')'
```

因此：

```
a = 1 OR b = 2 AND c = 3
```

等价于 `a = 1 OR (b = 2 AND c = 3)`，AST 根节点是 `OR`。

`WHERE age > 10 + 8` 的比较右操作数是加法节点，供后续常量折叠使用。

## AST 节点

| 语法结构 | AST |
| --- | --- |
| CREATE TABLE | `CreateTableStmt` + `ColumnDef` |
| INSERT | `InsertStmt` |
| SELECT | `SelectStmt` |
| DELETE | `DeleteStmt` |
| 二元运算 | `BinaryExpr` |
| NOT / 负号 | `UnaryExpr` |
| 列名 | `IdentifierExpr` |
| 数字 / 字符串 | `LiteralExpr` |

所有节点实现 `AstNode.accept(AstVisitor)`，C 的语义分析与 Plan 生成只需写 Visitor，不必改 Parser。

## 错误

语法错误抛出 `SyntaxException`，格式：

```
[语法错误] line:L, col:C unexpected token: ...
expected: TOKEN | TOKEN
```

词法错误仍由 A 的 `LexicalException` 负责，二者不要混用。

---

## C 阶段：AST → 语义检查 → 逻辑执行计划

本文件由 B 维护语法与 AST；AST 之后交给 C。下面给出 C 阶段的接口口径，方便 A/B/C 联调对齐。

完整流水线：

```
SQL 文本 → Lexer(A) → Token 流 → Parser(B) → AST → Semantic(C) → 检查后 IR → Plan(C) → 优化后逻辑计划
```

C 从 `MiniSqlFrontend` 拿到 AST 后，用 `minisql.MiniSqlCompiler` 一次性做
「语义检查 → 生成计划 → 优化」。四类语句分别做什么：

| 语句 | 语义行为 | 生成的逻辑计划 |
| --- | --- | --- |
| CREATE TABLE | 校验后把表登记进 Catalog（会话内共享） | 不生成计划 |
| INSERT | 表/列存在、须覆盖全部列、值类型匹配且为常量 | `InsertNode(table, cols, values)` |
| SELECT | `*` 展开为全列；WHERE 必须是布尔 | `Project ← [Filter ←] Scan` |
| DELETE | WHERE 必须是布尔（可省略=删全表） | `DeleteNode ← [Filter ←] Scan` |

### WHERE 表达式类型规则（C 侧摘要）

- 列与常量只有 `INT` / `VARCHAR`；布尔值由比较、`AND`、`OR`、`NOT` 产生。
- 字符串列只允许 `=` / `<>`；大小比较、算术运算只允许 `INT`。
- SELECT/DELETE 的 WHERE 整体必须是布尔。

### 5 条优化规则（作用于 Scan/Filter/Project 计划树，顺序执行）

| 规则 | 作用 | 例子 |
| --- | --- | --- |
| R1 常量折叠 | 纯常量子表达式求值 | `1=1→TRUE`、`age>10+8→age>18` |
| R2 布尔化简 | TRUE/FALSE 恒等与吸收、双 NOT | `TRUE AND x→x`、`NOT NOT x→x`；恒真→去 Filter |
| R3 冗余谓词消除 | AND/OR 里去重复相同谓词 | `p AND p→p` |
| R4 谓词下推 | Filter 并入 Scan 内部过滤 | 消除独立 Filter 算子 |
| R5 投影裁剪 | Scan 只读 `Project输出 ∪ 过滤引用列` | 见下例的 `cols=[id, age]` |

优化示例（计划树文本，`LogicalPlan.print()` 输出；`minisql.Demo` 会逐步打印每步）：

```
-- 优化前
Project [id]
  Filter ((1 = 1) AND (age > (10 + 8)))
    Scan student cols=[id, name, age]

-- 优化后（R1 → R2 → R4）
Project [id]
  Scan student cols=[id, age] filter: (age > 18)
```

### 语义错误格式

与词法/语法错误一致、带行列号，由 `minisql.semantic.SemanticException` 抛出：

```
[语义错误] line:1, col:1 未定义的表 'nosuch_table'
```
