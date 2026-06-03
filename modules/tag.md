# 标签模块

> 标签创建、筛选、优先展示、管理、清理
> 对应 task_plan.md M4 标签筛选与优先展示

# 阶段规划、决策记录 （拆分自 task_plan.md）

## 定位和功能描述

标签与任务通过 `TaskEntity.tagId` 关联。标签创建于任务录入时（按名称查找已有或新建），名称唯一。

## 整体规划和决策

### 1. 标签筛选

| 场景 | 交互 | 实现 |
|------|------|------|
| 单标签筛选 | 短按标签 -> 过滤该标签任务；再点同一标签 -> 取消 | `MainViewModel.setFilterTag()` / `clearFilterTag()` |
| 多标签筛选 | 非筛选态长按标签 -> 半透覆盖层弹出 ChipGroup，多选 -> 确定 | `MainViewModel.setMultiFilterTags()` |
| 多标签筛选态 | 单点标签无反应；右侧栏显示「清除筛选」按钮 | `TaskAdapter.setMultiFilterActive()` |
| 返回主界面 | 清除所有筛选 | `MainViewModel.applyDefaultFilter()` |

### 2. 标签优先展示

通用化条件->优先标签 ID 集合映射，引擎只接收 `priorityTagIds` 参数。

**当前开放场景**：工作日工作时段优先
- 设置优先标签 + 打开开关
- 生效条件：工作日（周一至周五）+ 早上或下午时段
- 排序：优先标签任务整体排前，内部仍按四象限排序

**预留场景**：当前时段优先

### 全项目审查修复（2026-05-30）

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md) F8

- [x] `DisplayEngine` `setValue` → `postValue`，避免非主线程直接 set

### 3. 标签管理页

| 功能 | 状态 |
|------|------|
| 全部标签列表 + 星标切换优先标签 | OK |
| 工作时段优先开关 | OK |
| 清理未使用标签入口 | OK |

### 4. 清理未使用标签

- 查询未被任何任务引用的标签（`COUNT(tasks) = 0`），按 `id DESC` 排序
- 逐个删除：点 Chip x 图标 -> 确认弹窗
- 一键清除：勾选多个 -> 确认弹窗
- 空状态提示

### 5. Widget 标签筛选（已实现）

- 点击标签切换筛选/取消，per-widget 独立持久化，交互与 Widget 模块统一

### 6. Repository 缓存线程安全（2026-06-03 审查修复）

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md) #2

- [x] `TagRepository.mCachedTags`：`ArrayList` → `volatile CopyOnWriteArrayList`
- [x] `TagRepository.mCachedTagsMap`：`HashMap` → `volatile ConcurrentHashMap`

### 标签 UI 交互决策（D011）

- **正常态**：蓝色（#1A73E8），无下划线，点击筛选该标签
- **激活态**：深蓝（#1557B0），显示下划线，表示当前正按此标签筛选
- **切换**：点击已激活标签 -> 取消筛选；点击其他标签 -> 切换筛选
- **持久化**：默认筛选标签 ID 存入 SharedPreferences（`justnow_prefs`）

### 文件结构

```
data/
├── entity/TagEntity.java          # 标签实体（id, name, color, isPriority）
├── dao/TagDao.java                # CRUD + 搜索 + 排序 + 未使用查询
└── repository/TagRepository.java  # 同步/异步封装

ui/
├── base/TagChipHelper.java        # Chip 创建/样式工具（2 处复用）
├── main/
│   ├── TaskAdapter.java           # 标签展示（优先标记 + 筛选态抑制）
│   ├── MainFragment.java          # 筛选覆盖层 + 长按触发
│   └── MainViewModel.java         # 筛选 + 优先标签逻辑
├── engine/
│   ├── DisplayEngine.java         # 优先标签排序偏移
│   ├── PriorityTagConfig.java     # 优先标签配置管理
│   ├── IWorkdayChecker.java       # 工作日判断接口
│   └── SimpleWorkdayChecker.java  # 周一至周五实现
├── tagmanage/
│   ├── TagManageFragment.java     # 标签管理页
│   ├── TagManageViewModel.java    # 优先标签 + 开关状态
│   ├── UnusedTagFragment.java     # 清理未使用标签
│   └── UnusedTagViewModel.java    # 未使用标签加载 + 删除
```

# 研究发现、技术决策 （拆分自 findings.md）

### 优先标签状态行（2026-05-25）

**需求**：主界面右侧栏顶部增加优先标签生效状态标注，用户可点击临时关闭/恢复优先排序。

**设计**：
- 位置：右侧栏顶部，`tv_engine_warning` 与 RecyclerView 之间
- 显示条件：`getEffectivePriorityTagIds()` 非空时显示
- 生效态：浅蓝底色 `#EEF5FF` + 组名 + 时段描述 + 右侧"关闭"按钮
- 暂停态：浅灰底色 `#F5F5F5` + "优先标签已暂停" + 右侧"恢复"按钮
- 临时关闭：`mSuppressPriority` 会话级标记，不持久化

**后台恢复机制**：
- `ProcessLifecycleOwner` 监听 App 前后台切换
- `JustNowApplication` 维护 `AtomicBoolean mBackgroundFlag`
- `MainViewModel.resetFiltersAndPriority()` 统一重置
- `MainFragment.onResume` 检查 `consumeBackgroundFlag()` 后调用重置

### 通用化设计
- 条件 -> 优先标签集合映射，引擎只接收 `priorityTagIds` 列表不关心条件来源
- 当前仅开放工作日工作时段场景，预留当前时段优先等扩展点

### 全项目审查修复（2026-05-30）

- `setValue` → `postValue`：LiveData setValue 要求主线程，后台线程调用有崩溃风险

### Repository 缓存并发修复（2026-06-03）

- `mCachedTags` ArrayList 在线程池中无同步保护 → 改用 `CopyOnWriteArrayList`
- `mCachedTagsMap` HashMap 无同步保护 → 改用 `ConcurrentHashMap`

# 进度日志 （拆分自 progress.md）

> 详见：[progress.md](../progress.md) — 2026-05-11/12 标签模块全线完成、2026-05-25 优先标签状态行、2026-06-03 审查修复

### 2026-06-03 审查修复

> 审查报告：[../docs/code-review-20260603.md](../docs/code-review-20260603.md)

- [x] TagRepository 缓存集合改为并发安全类（CopyOnWriteArrayList + ConcurrentHashMap）
- [x] 编译通过 + testDebugUnitTest 全通过

### 2026-05-30 审查修复

> 审查报告：[../docs/code-review-20260530.md](../docs/code-review-20260530.md)

- [x] setValue→postValue 修复
- [x] 编译通过

---

- [x] 单标签筛选（短按）+ 多标签筛选覆盖层（长按）
- [x] `TagEntity` 新增 `isPriority` 字段，DB 版本 3->4
- [x] `PriorityTagConfig` 优先标签 ID 改用 Room 读写
- [x] `DisplayEngine` 方法重载支持 priorityTagIds 偏移
- [x] `TagManageFragment`：场景开关 + ChipGroup + 标签选择对话框
- [x] `UnusedTagFragment`：单个删除 + 一键清除
- [x] 超链接风格标签交互（D011）：蓝色/深蓝/下划线切换
- [x] 主界面优先标签状态行 + `mSuppressPriority` + 后台恢复（2026-05-25）
- [x] i18n：新增字符串同步 zh-CN / zh-TW / zh-HK
- [ ] 标签删除时清理任务的 `tagId`
- [ ] Widget 多标签筛选实现（M7）

**状态**：🔧 已打磨

### 2026-06-02 — code-review-20260602 修复

- [x] TagRepository 加实例级内存缓存：`mCachedTags`（List）+ `mCachedTagsMap`（Map），写操作同步更新
- [x] 新增封装方法：`getAllTagsMapSync()` 缓存 Map、`getTagByIdSync()`、`getTagByNameSync()`、`getTagsByIdsSync()`
- [x] 写操作细化：async（insert/delete/deleteTags）全清，sync（insertSync/setTagPrioritySync）增量更新缓存
- [x] `getAllTagsSync()`/`getAllTagsMapSync()` 返回防御性拷贝，避免调用方 removeIf 污染缓存
- [x] TagRepository 收归 Application 单例，显示主界面/Widget 共享缓存
