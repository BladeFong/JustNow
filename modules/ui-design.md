# UI 设计规范

# 阶段规划、决策记录

## 定位和功能描述

定义主界面布局、时间线、右侧栏、Widget、对话框与编辑框的项目级 UI 规范。

## 整体规划和决策

### 主界面布局：黄金比例双栏

- 左侧栏（时间线）: 右侧栏（任务列表） = 1 : 1.618
- 底部全宽非紧凑时段栏，高度约 `72dp`，独立背景色 + 顶部 `1dp` 分隔线
- 时间线终点在底部栏上沿结束
- 左侧状态文字和右侧按钮都避开大圆角设备裁切区域
- 右侧 `+ 添加任务` 按钮替代 FAB，约 `44dp` 高

### 左侧栏：时间线

- 只显示当前时段刻度；每 15 分钟一个刻度线（15分短线/30分长线/整点加粗）
- 小时数字由独立 `HourColumnView` 显示在刻度左侧
- 任务条：执行中按 `focusMinutes` 占位，已完成按实际耗时 `endMs - startMs` 占位
- 高亮区域颜色：`#DAE134`
- 液体色块由顶层 `TimelineView` 统一绘制，横跨小时数列和刻度区
- 当前时间浮标：扁平指南针长针，左右对称

### 右侧栏：任务列表

- 按 M4 引擎排序展示
- 格式："标签 内容"
- 4:2:2:1 象限比例

### 桌面 Widget

- 顶部栏：剩余时间 + "+"按钮
- 剩余时间逻辑与主界面共用 [剩余时间模块](time-remaining.md)

### 项目级对话框与编辑框风格

**对话框**：统一基于 `AlertDialog`，通过主题层统一标题、正文、按钮的字号和颜色。三按钮挂载约定：`buttonBarPositiveButtonStyle = Widget.JustNow.Dialog.Button`（主色高亮），其他两个走 `.Secondary`。

**时间编辑框**：日期和时间入口共用项目级时间编辑框视觉——浅蓝编辑块、黑色文字、正文字号、居中显示。

### 依赖模块
- [智能展示引擎](smart-display.md) — 右侧栏任务排序
- [剩余时间模块](time-remaining.md) — 时段剩余计算
- [时间段计算](time-period.md) — 时段定义

# 研究发现、技术决策

- 主界面黄金比例双栏 + TimelineView 顶层容器 + HourColumnView
- 非紧凑底部时段栏：解决圆角屏幕遮挡 + 替代 FAB
- 液体色块关闭抗锯齿（消除边缘伪影）
- 任务项固定 64dp 两行（标签行 + 标题行），MaterialCardView 描边 + 8dp 圆角
- 字体规范：dimens.xml（text_size_title/body/caption）为字号唯一来源
- 项目级 Dialog 和时间编辑框风格整理
- 自定义 View 通过 `getResources().getDimension(R.dimen.text_size_*)` 获取字号
