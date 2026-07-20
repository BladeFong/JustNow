# 成果墙：花瓣统计 + 周期切换 + 趋势图 + 辅助修复

## 概述

成果墙（TimeCapsuleWallActivity）增强：标题栏花瓣总数统计、周期切换下拉、底部趋势图、横屏 3 列、假期提醒 bug 修复。

## D — 假期提醒 bug fix

### 问题

`getNoticeMessageResId()` 比较 `lastReviewedKey` 与当前 `reviewKey`，但 `lastReviewedKey` 从未被写入，导致每次编辑保存后仍提示"假期安排确认了吗？"。

### 修复

**文件**：`PeriodConfigViewModel.java` — `updateGroupAndPeriods()`

假期组保存时计算 `reviewKey`（`buildVacationReviewKey`）写入 `group.lastReviewedKey`，由 `mRepo.updateGroup(group)` 持久化。

```java
if (PeriodGroupType.isVacation(group.groupType)) {
    group.lastReviewedKey = buildVacationReviewKey(
        group.groupType, Calendar.getInstance());
}
```

置于 `group.lastEditedAt = System.currentTimeMillis()` 同位置。

## B — 横屏 3 列

### 修复

**文件**：`TimeCapsuleWallActivity.java` — `onCreate()`

```java
int spanCount = getResources().getConfiguration().orientation
    == Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
mRecyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));
```

替换 `new GridLayoutManager(this, 2)`。

## C — 标题栏花瓣统计 + 周期切换

### 布局

**文件**：`activity_time_capsule_wall.xml`

标题栏 `RelativeLayout` 右侧新增两个 TextView：

```
[←] [成果墙]            [🌸 X 片花瓣] [▼ 本周]
```

- `tv_petal_total`：花瓣统计，`layout_toStartOf="@id/btn_period_switcher"`
- `btn_period_switcher`：下拉切换按钮，`layout_alignParentEnd="true"`

### Java 逻辑

**文件**：`TimeCapsuleWallActivity.java`

1. **`onCreate`**：加载暑假/寒假 `TimePeriodGroupEntity`，判断是否加入下拉选项
2. **下拉菜单**：`PopupMenu`，动态构建选项列表
   - 本周（始终）
   - 本月（始终）
   - 暑假（`summer_vacation` 组 `enabled && lastReviewedKey` 匹配当年 `summer-{year}`）
   - 寒假（`winter_vacation` 组 `enabled && lastReviewedKey` 匹配当年 `winter-{year}-{year+1}`）
3. **花瓣计算**：`computeTotalPetals(periodType)` 根据选中周期查照片，累加 `QUADRANT_PETALS[quadrant]`（与 `RewardBarFragment` 一致）
4. **切换重载**：切换 → `loadPhotos()` + 更新统计文字

### 数据范围

| 周期 | 时间范围 |
|------|---------|
| 本周 | 周一 00:00 ~ 周日 23:59 |
| 本月 | 1 日 00:00 ~ 月末 23:59 |
| 暑假 | 当年 `summer_vacation.startMonthDay` ~ `endMonthDay` |
| 寒假 | 当年 `winter_vacation.startMonthDay` ~ `endMonthDay` |

### 数据查询

`TaskPhotoRepository` 新增方法 `getPhotosWithTaskInRange(long startMs, long endMs)`，按时间范围查照片。

**文件**：`TaskPhotoDao.java` — 已有 `getPhotosWithTaskInRange` 方法可复用。

## A — 花瓣趋势图（仅竖屏）

### 布局

`activity_time_capsule_wall.xml` 的根 LinearLayout 改为：

```xml
<LinearLayout orientation="vertical">
    <RelativeLayout id="layout_title_bar" />
    <RecyclerView id="rv_time_capsule_wall" layout_weight="1" />
    <com.nearby.justnow.ui.trendchart.PetalTrendChartView
        android:id="@+id/petal_trend_chart"
        android:layout_width="match_parent"
        android:layout_height="wrap_content" />
</LinearLayout>
```

横屏时 `petal_trend_chart.setVisibility(View.GONE)`。

### 新建 `PetalTrendChartView`

**文件**：`ui/trendchart/PetalTrendChartView.java`（新建）

参照 `TrendChartView` 的 Canvas 绘制模式：

- **折线+圆点**：蓝色线 #2196F3，圆点填充，与 TrendChartView 一致
- **Y 轴**：自适应范围，max 向上取整到 5 的倍数，画 5 档虚线 + 数值标签
- **X 轴**：底部标日期/周范围（"7/14-7/20"、"7月"、"暑假W1" 等）
- **数据接口**：`setData(List<String> xLabels, List<Integer> values)`，X 轴从旧到新
- **空态**：数据点 < 2 时不绘制（至少两个点才有趋势意义）
- **最小高度**：80dp

### 数据计算

`computeTrendData(periodType)` 返回 X 轴标签和 Y 轴数值：

| 周期 | 分段方式 | 节点数 |
|------|---------|--------|
| 本周 | 按周分组（含过去 9 周） | 10 周 |
| 本月 | 按月分组（含过去 9 月） | 10 月 |
| 暑假 | 按周分组，仅暑假日期范围内 | 按实际（≤暑假周数） |
| 寒假 | 按周分组，仅寒假日期范围内 | 按实际（≤寒假周数） |

数据来源：`getPhotosWithTaskInRange` 查询对应时间范围的照片，按时间分段累加花瓣。

### 与 C 联动

周期切换 → `computeTotalPetals()` + `computeTrendData()` → 更新 TextView + PetalTrendChartView。

## 字符串资源

新增 key（中英文各语言文件）：

| Key | 中文 | English |
|-----|------|---------|
| `s_petal_total` | `🌸 %d 片花瓣` | `🌸 %d petals` |
| `s_period_this_week` | `本周` | `This Week` |
| `s_period_this_month` | `本月` | `This Month` |
| `s_period_summer_vacation` | `暑假` | `Summer Vacation` |
| `s_period_winter_vacation` | `寒假` | `Winter Vacation` |

## 关键文件

| 文件 | 改动类型 |
|------|---------|
| `PeriodConfigViewModel.java` | D: 假期保存时写 lastReviewedKey |
| `TimeCapsuleWallActivity.java` | B: 横屏 3 列；C: 标题栏统计+下拉切换；A: 趋势图联动 |
| `activity_time_capsule_wall.xml` | 布局重构：标题栏加统计+下拉，底部加趋势图 |
| `ui/trendchart/PetalTrendChartView.java` | **新建**，周花瓣折线图 |
| `data/repository/TaskPhotoRepository.java` | 新增按时间范围查照片方法 |
| `res/values/strings.xml` | 新增 s_petal_total / s_period_* |
| `res/values-zh-rCN/strings.xml` | 新增中文字符串 |
| `res/values-zh-rTW/strings.xml` | 新增繁中（台湾）字符串 |
| `res/values-zh-rHK/strings.xml` | 新增繁中（香港）字符串 |

## 验证

1. 编译通过
2. D: 编辑保存暑假组后，提示"假期安排确认了吗？"消失
3. B: 手机横屏成果墙显示 3 列，竖屏 2 列
4. C: 标题栏右侧显示花瓣总数，下拉可切换周/月/暑假/寒假，总数刷新
5. C: 暑假/寒假选项仅在当年已确认且启用时出现
6. A: 竖屏底部显示趋势图（折线+圆点），横屏隐藏
7. A: 趋势图数据点随周期切换变化，暑假/寒假按实际周数
