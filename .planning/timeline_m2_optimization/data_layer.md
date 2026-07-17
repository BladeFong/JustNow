# Data Layer Module: Timeline Item & Builder

## Overview
该模块负责将底层的数据库实体 (`TaskEntity`, `TaskExecutionEntity`, `TaskScheduleEntity`) 转化为面向时间轴渲染的高级数据模型 `TimelineItem`。

## Key Components

### 1. `TimelineItem.java`
承载时间轴任务卡片和对应时间节点渲染所需的元数据。
- **新增字段**：
  - `public final int quadrant;` (四象限值，范围 0-3)
- **作用**：决定对应的轴线时刻圆点填充色、连接线段色、以及正在执行卡片的背景颜色。

### 2. `TimelineBuilder.java`
负责将活跃任务（或今日已执行完的任务）转换并构建为按开始时间排序的 `TimelineItem` 列表。
- **构建逻辑**：
  - 执行中专注任务：从 `activeTasks` 中筛选，并传入 `task.quadrant`。
  - 当天已完成任务：从 `executions` 中关联 `TaskEntity` 查出，并传入 `task.quadrant`。
  - 构建完成后按 `startMs` 升序排列。
