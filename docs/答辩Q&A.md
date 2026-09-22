# MiniSQL 实训答辩 Q&A（C 部分为主，兼答全链路）

> 面向对象：3 人团队通用。标 **[C]** 的由 C 主讲，标 **[A]/[B]/[共同]** 的按分工作答。
> 配合 `minisql.Demo` 的演示一/二/三段、`minisql.exec.Cli` 的演示四（真实执行/持久化）与 `docs/demo.sql` 一起看。

---

## 一、整体 / 架构

### Q1. 一条 SQL 从文本到结果是怎么走的？【共同】

```
SQL 文本 → Lexer(A) 词法 → Token 流 → Parser(B) 语法(递归下降) → AST
        → Semantic(C) 语义(名字绑定/类型检查) → 已检查 IR
        → Plan(C) 逻辑计划生成 → Optimizer(C) 5 条规则 → 优化后逻辑计划
        → Executor(C) 执行 → 执行结果 → TableHeap(C) → BufferPool(C) LRU → DiskManager(C) → *.db
```

演示口径：输入 `SELECT id,name FROM student WHERE 1=1 AND age>10+8;`，
先打印 Token 流与 AST（A/B 产物），再由 C 给出「优化前计划 → 每步改写 → 优化后计划」；
同样一条语句交给 `minisql.exec.Cli` 时，Executor 真正扫描/过滤/投影返回结果集并落盘。

### Q2. 三个模块怎么保证接口不打架、能顺利合代码？【共同】

1. **Day1 就冻结公共接口**：`Token`/`TokenType`（A）、AST record + `AstVisitor`（B）、
   Catalog + 计划/IR（C）都是明确约定的；C 只消费 B 的 AST，不反向改它。
2. **入口唯一**：都走 `MiniSqlFrontend.parse/parseAll`，不各自 new `Parser`。
3. **小步合入 + 全量测试**：每人都跑 `mvn test`，不绿不合；冲突按模块负责人裁决。
4. **解耦设计**：C 的表达式 IR 独立于 B 的 AST（见 Q7），谁改自己包不影响别人。

### Q3. 代码合并冲突时怎么处理？【共同】

以 Day1 约定接口为准；内部实现以最新合入代码为准；拿不准找对应负责人当面定，不拖延。
合并前 `git pull origin main` → 本地解决 → `mvn test` 全绿才允许推 `main`。

---

## 二、语法（B 为主，答辩常问）

### Q4. 表达式优先级是怎么保证的？【B】

递归下降分层：`or_expr → and_expr → not_expr → comparison → additive → multiplicative → unary → primary`，
优先级从低到高 `OR < AND < NOT < 比较 < + - < * /`，由文法层级天然保证，不在枚举里比较。
例：`a=1 OR b=2 AND c=3` 解析为 `a=1 OR (b=2 AND c=3)`。

### Q5. 括号怎么影响优先级？【B】

`primary` 遇到 `(` 就递归回到 `expression`，因此括号能把低优先级包起来改变结合：
`(a=1 OR b=2) AND c=3` 的根节点是 `AND`。

---

## 三、语义分析（C 主讲）

### Q6. 语义分析都做了哪些事？【C】

对四类语句做**名字绑定 + 类型检查**，产出“已检查 IR”：
- CREATE：表名冲突、列重名检查，并把表登记进 Catalog（会话状态）。
- INSERT：表存在、列存在且不重复、必须覆盖全部列、值的个数与类型都匹配、值不允许引用列。
- SELECT/DELETE：表存在；`SELECT` 的 `*` 展开为全列；WHERE 整体必须为布尔。

表达式递归检查时给每棵子树算出类型 INT / VARCHAR / BOOLEAN，
不满足就抛带行列号的 `[语义错误]`（与词法/语法错误格式一致）。

### Q7. 为什么 C 要自建一套表达式 IR，而不是直接用 B 的 AST？【C】

两处硬性原因：
1. **B 的 AST 没有“布尔常量 TRUE/FALSE”节点**，而优化必须能表达 `1=1 → TRUE`，否则 R1/R2 没法做。
2. 语义还要在树上附加“已绑定的列元信息 / 类型”，AST 是 B 的 record，我们不该也不想去改它的字段含义。

所以语义分析把 AST“翻译 + 绑定 + 校验”成 C 的 `minisql.expr` IR，计划与优化只在这份 IR 和计划树上工作，
C 与 B 只隔着一层最小契约（AST 字段 + 访问者），各自改自己包互不影响。

### Q8. 类型检查有哪些关键规则？【C】

- 类型只有 INT / VARCHAR，布尔由比较、`AND`、`OR`、`NOT` 产生。
- 字符串列只允许 `=` / `<>`；序比较与算术只允许 INT；`age>=‘18’` 属类型不匹配。
- INSERT 值必须是常量且类型与目标列一致，不允许引用列。
- WHERE 根部必须是布尔：`WHERE age`、`WHERE 1` 都会报错。

### Q9. 报错信息为什么能带行列号？【C】

B 的所有 AST 节点 record 都带 `line()/col()`（来自 A 的 Token），
语义在绑不到列、类型对不上等位置直接用节点位置抛 `SemanticException`，
于是错误格式统一为 `[xx错误] line:L, col:C …`，答辩演示“非法 SQL 友好报错”很直观。

---

## 四、执行计划与优化（C 主讲）

### Q10. 每类语句生成什么样的逻辑计划？【C】

| 语句 | 计划 |
| --- | --- |
| CREATE | 无算子计划（语义阶段登记 Catalog） |
| INSERT | `InsertNode(table, cols, values)` |
| SELECT | `Project ← [Filter ←] Scan`（有 WHERE 才有 Filter） |
| DELETE | `DeleteNode ← [Filter ←] Scan` |

### Q11. 5 条优化规则分别是什么原理？【C】

基于计划树/IR 的遍历与模式匹配，按固定顺序执行直到一轮无变化：
1. **R1 常量折叠**：纯常量子表达式编译期求值 → `1=1→TRUE`、`age>10+8→age>18`。
2. **R2 布尔化简**：TRUE/FALSE 恒等吸收、双 NOT、`x AND NOT x→FALSE`；整个条件恒真就去掉 Filter。
3. **R3 冗余谓词消除**：AND/OR 列表里去掉重复相同谓词（按规范化文本判重）。
4. **R4 谓词下推**：`Filter(pred, Scan)` → `Scan.filter = pred`，过滤越早做中间数据越少。
5. **R5 投影裁剪**：算 Project 输出列 ∪ 过滤引用列，Scan 只读这些列。

### Q12. 请现场演示一条“优化前后对比”。【C】

`SELECT id FROM student WHERE 1=1 AND age>10+8;`

```
-- 优化前
Project [id]
  Filter ((1 = 1) AND (age > (10 + 8)))
    Scan student cols=[id, name, age]

-- 优化后（R1→R2→R4）
Project [id]
  Scan student cols=[id, age] filter: (age > 18)
```

`minisql.Demo` 会把“每步由哪条规则改写成什么”逐条打印出来。

### Q13. 优化为什么不会死循环？【C】

规则都是“单调化简”的（谓词变短、算子变少、列变少），Optimizer 在
“一轮内没有任何变化”时停止，另设轮数上限兜底；演示/测试也验证过收敛。

### Q14. 投影裁剪里为什么 Scan 有时还带过滤用的列？【C】

因为列有两个用途：一是进最终结果（Project 输出），二是只参与 WHERE 过滤。
比如只 `SELECT id` 但 `WHERE age>18`，Scan 仍需读出 `age` 供过滤，所以 `cols=[id, age]`，
但不会再读整表多余列——这就是“Scan 只读被用到的列”。

---

## 五、测试 / 验收

### Q15. 怎么证明没把 A/B 代码改坏、C 是可用的？【C】

`mvn test` 全量绿（114 项）：A 的 LexerTest、B 的 ParserTest 原样通过，
C 新增 catalog / semantic / plan / optimizer / MiniSqlCompilerTest，
再叠加 storage（SlottedPage / DiskManager / BufferPool / FreeList）与 exec（RowCodec / ExprEvaluator / Executor / Persistence / Database）。
语义测试既有正例也有大量“必须抛 `[语义错误]`”的反例；优化测试断言“优化前 ≠ 优化后”和具体谓词文本；
持久化测试断言“写入 → 关闭 → 重开 → 数据仍在”。

### Q16. 如果时间不够，怎么取舍？【共同】

先保证 Lexer/Parser/Semantic 全链路跑通（能编译能报错）；
优化规则优先做 R1 常量折叠 + R2 布尔化简（最简单也最能演示），
R4/R5 关系算子级规则次之；若仍不够，规则留接口空实现并给 1 个硬编码示例演示。

### Q17. 演示环境出问题怎么办？【共同】

准备 ≥3 套 SQL（合法 / 优化 / 错误，见 `docs/demo.sql`）；
本地 `mvn test` 全绿作为兜底；提前录一份 `minisql.Demo` 视频。

---

## 六、存储引擎与执行引擎（新增，对应评分「存储系统」「数据库系统」）

### Q18. 页是怎么组织的？槽页（Slotted Page）格式是什么？【C】

页大小固定 4096 字节，大端字节序。数据页/目录页都是「槽页」：
页头 12 字节 = `pageType(1) + reserved(1) + slotCount(2) + freeSpaceOffset(2) + freeSpaceEnd(2) + nextPageId(4)`；
槽目录从 12 向上增长（每条 6 字节 = 记录偏移 + 记录长度），记录从页尾向下增长。
删除只把槽的 recordOffset 写成 -1（墓碑），不压缩——压缩会破坏扫描中已取得的其它 Rid；
放不下的行分配新页并用 nextPageId 串成页链。文件第 0 页是文件头（magic "MSDB" + pageSize + catalogRoot + freeListHead）。

### Q19. LRU 缓冲池是怎么实现命中/淘汰/回写的？【C】

用 `LinkedHashMap(accessOrder=true)` 维护「最近访问在尾、最久未访问在头」。
`getPage(id)` 命中则 pinCount++ 并自动移到 MRU；未命中且满则从头找第一个 pinCount==0 的帧淘汰，
脏帧先写回磁盘。`unpin(id, dirty)` 减 pin；被 pin 的页不参与淘汰；`flushAll/close` 回写全部脏页。
测试用固定访问序列 `0,1,2,0,3`（容量 3）断言「淘汰的是 1」来证明 LRU 正确。

### Q20. 一条记录（行）怎么序列化？为什么能只解码需要的列？【B】

schema 已知，所以编码不带类型标签、无 NULL 位图：INT=8 字节 long，VARCHAR=2 字节长度 + UTF-8。
`RowCodec.decodeSubset(fullSchema, requested, bytes)` 按表定义顺序读完整条记录，但只把 requested 中出现的列留下——
这正是 R5 投影裁剪在存储层的落地：堆里仍存整行，扫描时只解需要的列。

### Q21. 系统目录怎么持久化、重开怎么恢复？【B】

建表时（`Database.compile`）分配该表第一张数据页，把「表名 + 列定义 + 数据根页号」经 `SchemaCodec` 编成目录记录，
由 `CatalogStore` 写进目录页链（满则链新目录页）。打开数据库时 `CatalogStore.loadAll()` 重载全部目录记录，
逐个 `register` 进编译器的内存 Catalog——之后 SELECT/INSERT/DELETE 才能编译通过，这就是恢复路径。

### Q22. 执行器是怎么跑一个计划的？除零怎么办？【C】

`Executor` 按计划树逐算子执行（Volcano 风格）：Scan=堆扫描+过滤、Filter=过滤、Project=按列重排、
Insert=按表定义序落行、Delete=先扫描取 Rid 再打墓碑。表达式交给 `ExprEvaluator` 按行求值：
INT 用 Java long（整数除法、溢出回绕），VARCHAR 只允许 = / <>，AND/OR 短路；除零抛 `ExecutionException`（最易测试）。
