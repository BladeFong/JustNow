# 任务多照片支持与完成前拍照设计规范

> 相关模块：[tablet-flower-rewards](../../../modules/tablet-flower-rewards.md)

## 1. 概述

当前拍照流程限定在任务完成后（祝贺弹窗引导 / 补拍入口），且每任务只支持一张照片。本次改造将拍照能力前移到任务进行中，并支持每任务最多 5 张照片，成果墙支持左右划动浏览同一任务的所有照片。

核心改动点：
- 右下角「补拍」按钮改为通用「拍照」按钮，列表包含当天已完成任务 + 进行中任务
- `TaskPhotoEntity` 支持同一 `task_id` 多条记录（DB schema 天然支持，无需迁移）
- 花瓣奖励只计每任务每周首张照片
- 成果墙按任务去重显示首张，点击进入全屏支持左右划动

---

## 2. 数据层改动

### 2.1 DAO 新增查询

```java
// TaskPhotoDao.java

// 查某任务已拍张数
@Query("SELECT COUNT(*) FROM task_photos WHERE task_id = :taskId")
int getPhotoCountForTask(long taskId);

// 查某任务所有照片（按时间正序，全屏划动用）
@Query("SELECT * FROM task_photos WHERE task_id = :taskId ORDER BY created_at ASC")
List<TaskPhotoEntity> getPhotosForTask(long taskId);

// 指定时间范围内每任务首张照片（花瓣计花 + 成果墙用）
@Query("SELECT p.*, t.content as taskContent, t.quadrant as taskQuadrant, t.icon_name as taskIconName " +
       "FROM task_photos p INNER JOIN tasks t ON p.task_id = t.id " +
       "WHERE p.id IN (" +
       "  SELECT MIN(p2.id) FROM task_photos p2 " +
       "  WHERE p2.created_at >= :startTimeMs AND p2.created_at <= :endTimeMs " +
       "  GROUP BY p2.task_id" +
       ") ORDER BY p.created_at ASC")
List<TaskPhotoWithTask> getFirstPhotoPerTaskInRange(long startTimeMs, long endTimeMs);

// 当天可拍照任务：已完成（有 execution end_ms 在今天）+ 进行中（executingStartMs > 0）
@Query("SELECT DISTINCT t.* FROM tasks t " +
       "LEFT JOIN task_executions e ON t.id = e.task_id AND e.status = 0 " +
       "WHERE t.is_archived = 0 AND (" +
       "  (e.end_ms >= :todayStartMs AND e.end_ms <= :todayEndMs) " +
       "  OR (t.executing_start_ms > 0 AND t.executing_end_ms = 0)" +
       ") ORDER BY COALESCE(e.end_ms, t.executing_start_ms) DESC")
List<TaskEntity> getTodayTasksAvailableForPhoto(long todayStartMs, long todayEndMs);
```

### 2.2 存储层限制

`TaskPhotoRepository.insert()` 调用前，调用方检查 `getPhotoCountForTask(taskId) >= 5`，若满则拒绝并 Toast 提示。

### 2.3 字符串资源

```xml
<!-- 各语言 strings.xml -->
<string name="s_photo_limit_reached">该任务已拍满 5 张照片</string>
```

---

## 3. 拍照流程

### 3.1 右下角通用拍照按钮

- 按钮始终可见，文案从「补拍 (X)」改为「拍照」
- 点击弹出 `TaskPhotoListDialog`（改造自现有 `RetroactivePhotoDialog`）：
  - 数据源：`getTodayTasksAvailableForPhoto()` — 当天已完成（有 execution 在今天）+ 进行中任务
  - 排序：按时间倒序（最新完成 / 最晚开始在前）
  - 每项显示：象限色条、40dp 图标、任务名、已拍张数（如 "3/5"）
  - ✖ 按钮 + 点击外部区域关闭，无底部确认/取消按钮
  - 列表为空时 Toast 提示无可拍任务
- 点击某任务：
  - 张数 < 5 → 拉起系统相机
  - 张数 ≥ 5 → Toast `s_photo_limit_reached`

### 3.2 完成弹窗拍照入口

`CongratulationDialog` 的「去拍照」按钮：
- 张数 < 5 → 直接拉起相机拍一张 → 保存关联 → 弹出 `TaskPhotoListDialog`（与右下角按钮一致）
- 张数 ≥ 5 → Toast `s_photo_limit_reached`，不弹任务列表

### 3.3 拍照回环

```
[右下角按钮] 或 [完成弹窗→去拍照]
        │
        ▼
┌─────────────────────────────┐
│  TaskPhotoListDialog        │
│  ✖ 右上角关闭               │
│  按时间倒序，显示张数        │
│  点击外部区域关闭            │
└─────────────────────────────┘
        │ 点击某任务 (< 5张)
        ▼
   拉起系统相机 → 拍一张
        │
        ▼
  保存照片关联 → 回到 TaskPhotoListDialog (张数刷新)
        │
  用户可继续选任务拍，或 ✖ / 外部关闭
```

- 每次返回对话框时重新查询数据，张数实时反映最新状态

---

## 4. 花瓣计花逻辑

### 4.1 规则

每任务每周只有**首张照片**（`created_at` 最小者）参与花瓣计算。后续追加照片不计花瓣。

### 4.2 实现

`RewardBarFragment.refreshWeeklyFlowers()` 中：

```java
// 查询本周所有照片，按时序排列
List<TaskPhotoWithTask> photos = mPhotoRepository.getPhotosWithTaskInWeek(monday);

// 按 created_at 升序，Set 去重 — 每个 taskId 首次遇到即为首张
Set<Long> countedTaskIds = new HashSet<>();
for (TaskPhotoWithTask p : photos) {
    if (!countedTaskIds.add(p.photo.taskId)) continue;  // 非首张，跳过
    // ... 原有花瓣累加 + 花芯填充逻辑不变
}
```

`Set` 为局部变量，每次 `refreshWeeklyFlowers()` 调用时从 DB 重新构建，中途退出/重进不受影响。

### 4.3 成果墙趋势图

`TimeCapsuleWallActivity.countPetalsInRange()` 同样只计每任务首张，逻辑一致。

---

## 5. 成果墙（时光胶囊）

### 5.1 网格列表

- 数据源改为 `getFirstPhotoPerTaskInRange()`，每任务只显示一张卡片
- 卡片内容不变：缩略图、任务名、图标、时间、象限色条、花瓣数

### 5.2 全屏浏览

- 点击卡片进入全屏 `Dialog`：
  - 使用 `ViewPager2` 展示该任务的所有照片（`getPhotosForTask(taskId)`）
  - 支持左右划动切换
  - 保留现有双指缩放 + 双击缩放手势
  - 右上角 ✖ 关闭按钮保留
  - 从当前卡片对应的照片位置开始展示（`setCurrentItem(index)`）

---

## 6. 文件改动清单

| 文件 | 改动 |
|------|------|
| `TaskPhotoDao.java` | 新增 4 个查询方法 |
| `TaskPhotoRepository.java` | 新增对应仓储方法 + 张数检查 |
| `RewardBarFragment.java` | 按钮改造、花瓣去重、对话框替换 |
| `RetroactivePhotoDialog.java` → `TaskPhotoListDialog.java` | 重命名、数据源扩展、张数显示、排序调整 |
| `dialog_retroactive_list.xml` → `dialog_task_photo_list.xml` | 布局微调（每项加张数 TextView） |
| `CongratulationDialog.java` | 拍照后弹出任务列表、满5张Toast |
| `TimeCapsuleWallActivity.java` | 数据源切换、ViewPager2全屏 |
| `dialog_photo_detail.xml` | 替换为 ViewPager2 布局 |
| `MainFragment.java` | 完成弹窗回调调整（拍照后弹出任务列表） |
| `res/values/strings.xml` | 新增 `s_photo_limit_reached` |
| `res/values-zh-rCN/strings.xml` | 同上 |
| `res/values-zh-rTW/strings.xml` | 同上 |
| `res/values-zh-rHK/strings.xml` | 同上 |

> 注：`TaskPhotoEntity` 无需改动，`task_photos` 表无 `task_id` 唯一约束，天然支持多行。`AppDatabase` 版本号无需升级。
