# Task Plan: Timeline M2 Visual Optimization

## Goal
优化主界面左侧时间线视觉样式，替换刻度线为加大刻度圆点，分段轴线，对齐物理行，色条加粗拉长，并实现执行中卡片背景全色化。

## Current Phase
Phase 3: Implementation

## Phases

### Phase 1: Requirements & Discovery
- [x] Understand user intent (dots 12dp, buoy has no time text, no scheduled tasks, custom capsule-like strip shape, running task gets full background quadrant color, minimum liquid fill height 12dp)
- [x] Identify constraints and requirements
- [x] Document findings in findings.md
- **Status:** complete

### Phase 2: Planning & Structure
- [x] Define technical approach (split into data layer: TimelineItem/TimelineBuilder; scale layer: HourColumnView; custom drawing layer: TimelineView)
- [x] Create project structure if needed
- [x] Document decisions with rationale
- **Status:** complete

### Phase 3: Implementation
- [x] Implement Task 1: Data layer changes (TimelineItem & TimelineBuilder)
- [x] Implement Task 2: Scale label changes (HourColumnView text color & translation)
- [x] Implement Task 3: Custom drawing changes (TimelineView dots, segmented lines, ongoing background card, capsule strips)
- **Status:** complete

### Phase 4: Testing & Verification
- [ ] Compile successfully
- [ ] Run Android app/emulator to inspect visual presentation
- [ ] Run unit tests and resolve any regressions
- **Status:** pending

### Phase 5: Handoff
- [ ] Review all modified files
- [ ] Deliver to user
- **Status:** pending

## Key Questions
1. How to ensure all dots are strictly 12dp diameter without clipping? (Done: used raw `canvas.drawCircle` with `6 * mDensity` and no stroke thickness eating into radius).
2. How to avoid text-buoy overlap? (Done: removed buoy time text, and translated HourColumnView labels to the left by 12dp).

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| Store `quadrant` in `TimelineItem` | Required to color code dots and line segments and fill running task background card according to quadrant color |
| Shift hour text left by 12dp | The 12dp dots centered on dividing line extend 6dp to the left; shifting text left by 12dp leaves a clean 6dp safety gap |
| Simple Circle for Buoy | Visually cleaner, scales nicely, matches WeChat mockup, avoids overlap with time label |
| Set minimum liquid fill height to 12dp | Prevents squeezing to 0 height when remaining free time is small |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| git add denied | 1 | Skipped git command and proceeded with code edits directly |
| gradle test failures | 1 | Investigating if database/preference test failures were existing or caused by us |
