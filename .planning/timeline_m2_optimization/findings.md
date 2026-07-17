# Findings & Decisions: Timeline M2 Visual Optimization

## Requirements
- 时刻圆点半径统一为 `6dp` (直径 `12dp`)，确保物理大小完全一致（灰色、蓝色、黄色、红色）。
- 时刻圆点之间是不与圆点相连的竖直分段轴线（两端保留间距）。
- 当前时间浮标周围绝对不能绘制任何当前时间文本，以防止在整点处与刻度文本重叠。
- 所有时间刻度文本使用黑色，向左偏置以绝不与轴线/圆点重叠。
- 任务左侧色条拉长，使用 6dp 宽、22dp 高的圆角胶囊块。
- 执行中任务变为全色卡片背景（象限色），文字转白，去除 "ONGOING" 标签。
- 自由空闲时间色块最小绘制高度 12dp，内部不绘制任何文本框。

## Research Findings
- `TimelineView.java` 采用 Custom View 绘制，其 `onDraw` 处理了整个时间线的轴、线、点、任务块及浮标。
- `HourColumnView.java` 负责在左侧绘制整点文本。两者在 `fragment_main_page0.xml` 中以水平 `LinearLayout` 并排布置。
- 分界线坐标为 `areaLeft`。在 `HourColumnView` 中，文本绘制坐标是 `w - mDensity` (即紧贴分界线)。在 `TimelineView` 中，圆点圆心在 `areaLeft` 上，因此圆点会向左突出 `6dp`。
- 将 `HourColumnView` 中文本绘制位置改为 `w - 12 * mDensity` 可以保留 6dp 的安全间距。

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Shift `HourColumnView` text left | Avoids overlap with the new enlarged 12dp dots |
| Render segment lines dynamically | Colors sections of the timeline line based on time state (past/present/future) |
| Remove buoy time text | Simplifies UI and completely eliminates collision at hourly junctions |
| Capsule shape for done tasks | Uses `canvas.drawRoundRect` with radius 3dp for a chubby capsule shape |
| Quadrant-colored executing card | Full color background with white text brings focus to the ongoing task |

## Resources
- Design Specification: [2026-07-16-timeline-m2-optimization-design.md](file:///home/lanef/Android/StudioProjects/JustNow/docs/superpowers/specs/2026-07-16-timeline-m2-optimization-design.md)
- Implementation Plan: [2026-07-16-timeline-m2-optimization.md](file:///home/lanef/Android/StudioProjects/JustNow/docs/superpowers/plans/2026-07-16-timeline-m2-optimization.md)
