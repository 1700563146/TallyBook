# TallyBook 架构设计

> 一款 Kotlin + Jetpack Compose 的 Android 记账本。**单币种、离线优先、金额零误差**。
>
> 本文档描述分层架构、模块划分与关键决策。仓库内已有**真实可编译**的骨架：
> `./gradlew :app:assembleDebug` 通过，**163 个测试全绿**（141 JVM + 22 真机仪器化）。

**配套文档**

| 文档 | 内容 | 什么时候看 |
| --- | --- | --- |
| 本文 | 分层、模块边界、关键技术决策与取舍 | 想理解"为什么这么设计" |
| [ROADMAP.md](ROADMAP.md) | 分阶段交付计划与验收标准 | 想知道"接下来做什么" |
| [FEATURES.md](FEATURES.md) | 完整功能盘点（含未入路线的功能）与数据模型缺口 | 想知道"还缺什么功能" |
| [RISKS.md](RISKS.md) | 隐患清单：已修复缺陷、未修复风险、平台与工程风险 | 想知道"哪里会出事" |

---

## 1. 设计目标与约束

记账 App 有它特殊的工程约束，架构必须围绕它们设计，而不是套用通用模板：

| 约束 | 后果 |
| --- | --- |
| **金额不能有误差** | 禁止 `Double` 参与金额运算；内部一律整数最小单位 |
| **数据不能丢** | 软删除、显式迁移、`exportSchema`；绝不 `fallbackToDestructiveMigration` |
| **查询是只读为主** | 读路径用 Room `Flow` 直出 UI；写路径才需要事务 |
| **首屏要快** | 单 Activity + 模块化按需加载；DB 懒初始化 |
| **图表要能按周/月/年切换** | 分桶在 SQL 里做，不在 Kotlin 里全量加载后分组 |
| **预算要准时** | 周/月周期边界用用户时区计算，且首日即视为"已过 1 天" |
| **离线优先** | 无网络是常态；网络只用于可选同步 |
| **长期维护** | 模块边界用 Gradle 强制，而不是靠自觉 |

### 关于单币种（重要的范围决策）

本项目**不做多币种转换**。这不只是"少写点代码"，而是消除了三类长期 bug 的来源：

1. 每次读取都要查汇率 → 现在不需要
2. 汇率缺失导致总数部分失真 → 这个状态在类型上不存在了
3. SQL 跨币种 `SUM()` 静默算错 → 聚合键不再需要携带币种维度

`CurrencyCode` 仍然保留，因为它承载的是**显示正确性**（JPY/KRW 零小数位、符号、千分位），
不是换算。代码里仍有一个 `requireSameCurrency` 断言，用来在配置出错时尽早失败。

---

## 2. 分层架构

采用 Google 官方推荐的 **UI → Domain → Data** 三层，但**刻意不做成 Clean Architecture 的三层包**。

```
┌────────────────────────────────────────────────────────┐
│  :app          单 Activity、NavHost、主题、DI 入口       │
├────────────────────────────────────────────────────────┤
│  :feature:*    UI Layer                                 │
│    transactions / insights / budget / settings          │
│    Compose Screen + ViewModel(StateFlow<UiState>)       │
├────────────────────────────────────────────────────────┤
│  :core:data    Domain + Data Layer                      │
│    Repository 接口 + 实现 + Mapper + Budget 计算         │
├────────────────────────────────────────────────────────┤
│  :core:database  :core:common  :core:ui  :core:model    │
└────────────────────────────────────────────────────────┘
```

### 2.1 为什么没有独立的 `:domain` 模块

Clean Architecture 通常强制 `domain`（纯 Kotlin，含 UseCase）与 `data` 分离。**本项目不这么做**：

1. **UseCase 层在这个规模下是噪音**。记账 App 90% 的逻辑是"查询 + 聚合 + 校验"，
   包一层 `class GetTransactionsUseCase` 只是把 `repository.get()` 改名。
2. **实体拆分成本 > 收益**。`Transaction` 在 DB / domain / UI 各有一份会让每次加字段变成三处修改。
   本项目只在 **DB entity（`TransactionEntity`）** 与 **domain model（`Transaction`）** 之间映射。
3. **`:core:model` 已经是纯 Kotlin JVM 模块**（无 Android 依赖，测试秒级），
   它承担了 domain 层的"不变式"职责。真正的抽象价值在于**模型不自欺**，而不在于目录层数。

> 什么时候该补 Service：一旦出现"跨多个 Repository 的编排 + 复杂事务 + 幂等重试"
> （例如导入 CSV 时去重 + 建账户 + 建分类 + 写流水），就抽到 `:core:data` 里的 Service，
> 而不是为了对称而建模块。本项目已有先例：`BudgetRepositoryImpl` 内部的评估逻辑。

### 2.2 依赖方向（编译期强制）

```
:app  ──► :feature:*  ──► :core:data ──► :core:database
  │            │              │                │
  └────────────┴──────────────┴──► :core:model  (:core:ui 仅供 feature)
```

规则：

- `:core:model` 依赖**零个** Android / Room / Hilt 库，只有 `kotlinx-coroutines` / `serialization`
- `:feature:*` **不允许**依赖 `:core:database`，只能看到 Repository 接口
- `:feature:*` 之间**互不依赖**，跨 feature 跳转通过 `:app` 的 `NavHost` 与类型安全路由完成
- `:core:ui` 只放主题与通用组件，**不持有业务状态**

---

## 3. 模块清单

```
app/                     单 Activity 壳：MainActivity、TallyBookApp、Theme
core/
  model/                 纯 JVM —— Money / CurrencyCode / Transaction / Budget
                         + stats: TrendPoint / TrendSeries / CategorySlice / BudgetProgress
  common/                Clock、DateProvider（可注入时间、时区正确的周/月/年边界）
  database/              Room：Entity、DAO、Converters、Migration、schema 快照
  data/                  Repository 接口 + 实现、Mapper、StatsRepository、BudgetRepository
  ui/                    Material3 主题、共用组件、Preview 夹具
feature/
  transactions/          明细列表、记一笔、编辑
  insights/              趋势图（周/月/年 × 支出/收入/两者）+ 分类占比
  budget/                每周 / 每月预算与进度
  settings/              账户/分类/币种/备份/隐私
```

**为什么按 feature 切而不是按 layer 切**：按 feature 切，新增"预算"功能只新增一个目录；
按 layer 切，新增功能要在三个模块同时改动，且三个模块会成为编译瓶颈。

---

## 4. 关键技术决策

### 4.1 金额表示：`Long` 最小单位

```kotlin
data class Money(val minorUnits: Long, val currency: CurrencyCode)
```

- **`Double` 直接出局**：`0.1 + 0.2 != 0.3`，一个月的手续费累加就能出现肉眼可见误差。
- **`BigDecimal` 不适合做模型类型**：Room 无 DECIMAL 类型（只能存 TEXT 再解析），
  且它是堆对象、无值语义、在列表/图表循环里分配成本高。
- **`Long` + 币种小数位数**是 Stripe / Square / Shopify 的通行做法。

> **这里踩过一个真实的严重 bug，值得记录。**
> 早期实现为了让调用点简洁，写成 `requireSameCurrency(other)` 返回 `other`，于是
> `plus` 里 `Math.addExact(minorUnits, other.requireSameCurrency(this).minorUnits)`
> 实际拿到的是**接收者自己**：`10 + 20` 得 `20`，`0 + 0` 恒为 `0`。
> 累加 1000 次 1 分得到 0，所有月度汇总会全线错误，而且**不会有任何异常**。
> 修复方式是把守卫函数改成返回 `Unit`——强制每个运算点显式写出两个操作数。
> 教训：**辅助函数不要"顺手返回值"来省字符，那会把调用点变成一维易错形式。**

### 4.2 数据库：Room + `exportSchema` + 显式迁移

- `exportSchema = true`，schema JSON 纳入版本管理（`core/database/schemas/`）
- **迁移必须手写**，`addMigrations()` 预留位置；**不启用** `fallbackToDestructiveMigration`
- 主键用 **UUID 字符串**而非自增 `Long`：为未来同步/合并/导入铺路

### 4.3 聚合键必须包含所有被"消耗"的维度

早期版本的分类聚合是 `GROUP BY category_id` + `SUM(amount_minor)`。
在多币种场景下这会把 100 JPY 和 100 CNY 加成 200。
单币种之后这个具体 bug 消失了，但**规则仍然成立**：任何 `GROUP BY` 都要问一句
"我丢掉的维度里，有没有不该相加的？"

### 4.4 图表分桶：SQL 只聚合到"天"，其余全在 Kotlin

图表的**第一版设计是错的**，值得完整记录，因为它的失败方式极其隐蔽。

原设计让 SQLite 直接产出周/月/年桶键：

```sql
-- 旧实现（已删除）
CASE :granularity
  WHEN 'MONTH' THEN strftime('%Y-%m', ...)
  WHEN 'WEEK'  THEN strftime('%Y', ...) || '-W' || strftime('%W', ...)
  ELSE strftime('%Y', ...)
END AS bucketKey
```

Kotlin 侧则用 `WeekFields.of(Locale.UK)` 去算"同一个键"来查表。**但两者语义不同**：

| 侧 | 定义 | 结果 |
| --- | --- | --- |
| SQL `%W` | 年内第几个周一；首个周一之前算 `00` | `2025-01-06` → `2025-W01` |
| Kotlin ISO | week-based year，首周至少 4 天 | `2025-01-06` → `2025-W02` |

实测 2024-01-01 ~ 2026-01-10 共 753 天，**377 天（50.1%）键值不一致**。
SQL 只返回有数据的桶，Kotlin 用另一套键去查，查不到就补 0 —— 于是**周视图图表永远是零**，
不崩溃、不告警。详见 [RISKS](RISKS.md) 的 §A1。

**现在的设计**：把"分桶"这件事收敛到唯一实现。

| 职责 | 位置 | 原因 |
| --- | --- | --- |
| 扫描 + 按天求和 | **SQL**（`strftime('%Y-%m-%d')`） | `occurred_at` 走索引，3 年范围最多 ~1100 行已聚合结果；ISO 日期无歧义 |
| 周/月/年分组 | **Kotlin**（`DateProvider.bucketStartOf`） | 与预算共用同一个 `weekStartsOn`，**一致性由构造保证**，而不是靠两边同步修改 |
| 空桶补零 | **Kotlin**（`TrendBucketer`） | 缺口有歧义（"没数据" vs "没花钱"），零线才是真相 |
| 生成标签 | **Kotlin** | 依赖粒度与 locale（"11月" vs "Nov"），散落各处必然漂移 |

代价对比：旧方案省下的是"把 1100 行搬进内存"，换来的是"整个周视图失效"。**这笔交换不划算。**

`TrendBucketer` 因此被抽成独立、无依赖的纯组件，测试直接喂日期断言归属，
不需要 Room、协程或 Android。

> **为什么原来的测试没抓到**：`BucketKeyConsistencyTest` 只断言 Kotlin 侧能生成
> "它自己认为对的键"，从未与 SQL 对拍。**只测接缝一端的一致性测试等于没测**，
> 还会提供虚假的安全感。这条已写进 [RISKS §A1](RISKS.md)。

**为什么周/月/年是 `TimeGranularity` 枚举而不是三个查询**：粒度是同一查询的参数，
拆成三个方法会导致"新增一种粒度要改四处"，而且测试要重写三遍。

### 4.5 预算：周/月，且首日即算"已过 1 天"

```kotlin
data class BudgetProgress(
    val limit: Money, val spent: Money,
    val daysElapsed: Int, val daysTotal: Int,
    val projectedOverrun: Money?,
)
```

三个设计要点：

1. **周期边界由 `DateProvider` 统一计算**，`WEEK` 从周一开始。
   图表和预算必须用同一个 `weekStartsOn`，否则会出现"图表说这周花了 500，预算说花了 800"。
2. **首日计为 1 天**。在月初返回 0 会让所有进度比率除零，也会让用户看到"还剩 0 天"。
   这是 `daysElapsed` 唯一一次边界修正，专门有测试锁定。
3. **配速提示比百分比有用**。`spentPace` 对比 `timePace`：
   30 天里第 10 天花掉 60% 是"花得比时间快"，第 28 天花掉 60% 反而正常。
   单一百分比表达不了这个，所以 `BudgetProgress` 同时暴露两个进度值。

`projectedOverrun`（按当前节奏推算期末超支）在周期前 3 天返回 `null`：
拿 2 天的数据外推一整个月只会产生吓人且无用的数字。

### 4.6 状态管理：`StateFlow<UiState>` + 单向数据流

- 每个 Screen 一个 `HiltViewModel`，暴露 `StateFlow<XxxUiState>`
- `UiState` 不可变 `data class`，`copy` 更新，配合 Compose 的 `equals` 跳过重组
- Screen 拆成**有状态外壳**（只做 `hiltViewModel()` + `collectAsStateWithLifecycle()`）
  和**无状态内容**（纯参数，可 `@Preview`、可截图测试、不需要 DI 图）
- **图表的选择状态（粒度/系列）放在 ViewModel 而非 `remember`**：
  它必须跨旋转与进程死亡存活，且要和"查询出这份数据的那个快照"绑定。
  放在 Composable 本地状态里，旋转后就会出现"图表切到周视图但坐标轴还是月"的经典 bug

#### 切粒度必须用 `flatMapLatest`

```kotlin
granularity.flatMapLatest { g -> combine(trend(g), breakdown(g), totals(g)) { ... } }
```

用普通 `map` 的话，先发出的慢周查询可能**晚于**后面的月查询落地，把图表覆盖成旧数据。
这是这类页面最常见的图表 bug，在这里用 `flatMapLatest` 从根上解决（会取消上一轮查询）。

### 4.7 依赖注入：Hilt（编译期）

- Hilt 编译期生成依赖图，**改构造函数签名立即编译失败**；Koin 是运行期解析，
  错误要等页面打开才暴露，记账 App 页面路径深，晚失败代价高
- 已知代价：KSP 让 clean build 变慢（本项目全量约 2 分钟），用配置缓存 + 构建缓存缓解

### 4.8 时间：`Clock` / `DateProvider` 注入

```kotlin
interface Clock { fun nowMillis(): Long }
```

看起来过度设计，但解决真实问题：**"本周"的边界依赖时区与周起始日**。
直接调 `System.currentTimeMillis()` 的代码无法测试月末、跨年、夏令时；
注入之后测试可以固定时间跑，CI 不会在每月 1 号莫名变红。

`minSdk 26` 让 `java.time` 可用，**不需要** `desugar_jdk_libs`。

### 4.9 软删除 + 撤销

`transactions.deleted_at` 而非物理删除。所有查询以 `deleted_at IS NULL` 开头（DAO 内统一）。

> 财务数据必须可审计。用户误删一笔 5000 元记录，物理删除就是不可恢复的数据丢失。

---

## 5. 数据模型

```
accounts ──┐
           ├──< transactions >── categories (自引用 parent_id)
categories ┘         │
                     └──< transaction_tags
budgets          (category_id?, period ∈ {WEEKLY, MONTHLY}, limit, include_subcategories)
```

设计要点：

- **`amount_minor` 恒为正**，方向由 `type ∈ {EXPENSE, INCOME}` 表达。
  带符号金额会让 `SUM()` 语义含糊，且"支出填 -50 还是 50"变成人祸。
- **转账是「一行」记录**：`kind = TRANSFER`，`account_id` 是出方，`counter_account_id` 是入方。
  余额查询用一个 `CASE` 同时扣一边、加一边。
  曾经存在的 `insertTransferPair`（写两行镜像）与这个模型冲突 —— 两行相加后互相抵消，
  **转账后两个账户余额都不变**。已删除，DAO 只保留单行的 `upsertTransfer`。详见 [RISKS §A3](RISKS.md)。
- **所有收支聚合都带 `kind != 'TRANSFER'`**，否则账户间挪钱会同时虚增收入和支出。
- **分类自引用 + `ON DELETE RESTRICT`**：删除父分类必须先迁移子分类，不能级联静默删除。
- **索引按真实查询建**：`occurred_at`、`(occurred_at, account_id)`、`(type, occurred_at)`、
  `(category_id, occurred_at)` —— 覆盖"本月明细""本月支出""某分类趋势"三个热查询。
- **预算表存 `period`**，`YEARLY`/`CUSTOM` 在枚举里预留给 UI 演进，不需要 schema 迁移。

### 5.1 模型目前**缺**的字段（建议尽早补）

这些不是"加个功能"，而是"定形状"。现在加是一列，上线后加是迁移 + 回填 + 双写兼容。
完整论证见 [FEATURES §0](FEATURES.md)。

| 缺口 | 不补的后果 |
| --- | --- |
| `ledgerId`（多账本） | 生活/生意账混在一起，后期拆分需要用户手工迁移，基本没人做 |
| `local_date`（写入时固化本地日期） | 用户换时区，历史报表数字会变（见 [RISKS B1](RISKS.md)） |
| `refundOfId`（退款关联原单） | 退款只能记成负数支出或反向收入，两种都会污染统计口径 |
| `status`（报销/待确认） | 垫付算不算支出只能靠备注约定 |
| `attachment` 表 | 凭证图片无处可放 |

---

## 6. 测试策略

| 层 | 工具 | 关注点 | 现状 |
| --- | --- | --- | --- |
| `:core:model` | 纯 JUnit | 金额运算、金额输入引擎、币种格式化、预算比例、告警策略、环比 | ✅ 66 例 |
| `:core:common` | 纯 JUnit | 周/月/年边界、闰年、跨年、DST、`daysElapsed` | ✅ 13 例 |
| `:core:data` | 纯 JUnit | 分桶归属、CSV 转义与注入防护、导入计划 | ✅ 40 例 |
| `:core:database` | **真机仪器化** + 内存 Room | 转账余额、退款口径、软删除、回收站、分类过滤、created_at | ✅ 18 例 |
| `:feature:*` | Turbine + 假 Repository | ViewModel 状态流转、切粒度取消旧查询 | ❌ 待补 |
| UI | Compose UI Test + 截图 | 空态、超长中文、深色模式、字体 200% | ❌ 待补 |

> **为什么数据库测试跑在真机而不是 Robolectric**：Robolectric 会在首次执行时下载
> `android-all` 运行时，在受限网络下会长时间挂起（本项目实测卡住超过 10 分钟）。
> 仪器化测试用的是 App 实际发布的那个 SQLite 实现，反而更可信。
>
> 调试通知类功能时注意：`adb shell am force-stop` **会清除该应用的所有通知**。
> 所以"重启后通知消失了"不能说明去重生效——用"重启后是否**新发**通知"或直接检查去重状态文件判断。
>
> 代价：仪器化测试的方法名**不能用反引号加空格**（`fun \`transfer moves money\`()`）
> —— 编译器会生成含空格的 lambda 类名，D8 在 DEX 040 以下直接拒绝。所以
> `:core:database` 用 snake_case，而纯 JVM 模块仍保留可读的反引号命名。

**没有覆盖金额与迁移的测试，就不要发版。**

> 测试的经验教训：本项目已发生的三个严重缺陷（金额加错操作数、周分桶语义不一致、
> 转账写两行）**全部由测试或实证检查发现，没有一个是靠 code review 看出来的**。
> 但它们也说明测试本身可能失效 —— 周分桶那个 bug 旁边就有一个"通过的"测试。
> 判断标准不是"有没有测试"，而是**测试有没有触到真实边界**。

---

## 7. 当前仓库状态（已验证）

```bash
./gradlew :app:assembleDebug       # ✅ BUILD SUCCESSFUL，app-debug.apk 25 MB
./gradlew :core:model:test \
          :core:common:testDebugUnitTest \
          :core:data:testDebugUnitTest   # ✅ 141 JVM tests, 0 failures
# 真机仪器化（需要已连接的设备/模拟器）
./gradlew :core:database:assembleDebugAndroidTest
adb shell am instrument -w com.tallybook.core.database.test/androidx.test.runner.AndroidJUnitRunner
                                         # ✅ 22 instrumented tests, OK
```

已实现：完整模块骨架、金额/币种模型、Room schema + 4 个 DAO + 索引 + 外键、
Repository 接口与实现、**按天聚合 + 统一分桶的统计仓库**、
**预算仓库（周/月周期 + 配速 + 期末推算）**、Hilt DI 图、
Compose 四屏导航（明细 / 统计 / 预算 / 设置）、**Vico 折线图与柱状图**、Material3 主题、自适应图标。

尚未实现（见 [ROADMAP](ROADMAP.md) 与 [FEATURES](FEATURES.md)）：记一笔编辑页、范围选择、分类下钻、
CSV/JSON 导入导出、生物识别锁、桌面小组件。

---

## 8. 已知取舍与需要警惕的地方

完整的隐患清单（含严重度、触发条件、检测方式、修复状态）在 **[RISKS.md](RISKS.md)**。
这里只列架构层面的取舍：

1. **图表最多 12 个桶**（`TRAILING_BUCKETS = 12`）。这是手机屏幕能容纳可读标签的上限；
   要"看全年 365 天"应该用日历热力图，不是硬塞进折线图。
   `TrendBucketer.MAX_BUCKETS = 400` 兜底，防止误传超长区间冻结渲染。
2. **没有分页**：当前 `LIMIT/OFFSET` 直接查。数据超过约 5 万条应切 Room `PagingSource`。
3. **图表数据单位换算**：Vico 只吃 `Double`，所以传入前做了 `/100.0`。
   这是**显示层**的边界，模型里仍是 `Long` 最小单位。若将来支持 JPY（零小数位），
   这个硬编码需要改成按 `fractionDigits` 换算 —— 已在代码处标注。
4. **预算的 `YEARLY`/`CUSTOM` 会在 UI 被过滤掉**，只在模型与数据库层预留。
5. **分桶依赖设备时区**（SQL 用 `'localtime'`）：换时区会重排历史。
   这是产品选择（"23:30 买的算今天"），但代价要知道 —— 见 [RISKS B1](RISKS.md)。

---

## 9. 参考资料

### 官方文档

- [Android 应用架构指南](https://developer.android.google.cn/topic/architecture) — 分层与单向数据流
- [数据层指南](https://developer.android.google.cn/topic/architecture/data-layer) — Repository 与离线优先
- [架构建议清单](https://developer.android.google.cn/topic/architecture/recommendations) — 与本文的取舍逐条对应
- [Kotlin Multiplatform 推荐项目结构](https://kotlinlang.org/docs/multiplatform/multiplatform-project-recommended-structure.html) — 模块切分原则

### 同领域开源项目（选型参考，非依赖）

- [Ivy Wallet](https://github.com/Ivy-Apps/ivy-wallet) — Compose + Room 的成熟记账 App，
  数据建模与"记一笔"交互值得参考。**注意：上游已宣布不再维护**，适合读代码，不适合作为长期分叉基线。
- [MyExpenses](https://github.com/mtotschnig/MyExpenses) — 老牌开源记账应用，多账户/导入导出的边角案例很全，
  它的 issue 列表基本是一本记账 App 的踩坑百科。
- [buckwheat](https://github.com/danilkinkin/buckwheat) — Compose 记账 App，界面与预算实现可参考。

### 图表

- [Vico](https://github.com/patrykandpatrick/vico) — Compose 原生图表库，本项目选用（折线 + 柱状）。
  不选 MPAndroidChart：它基于 View 体系，需 `AndroidView` 包装，主题适配与手势在 Compose 下体验割裂。
