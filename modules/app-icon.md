# APP 图标模块

# 阶段规划、决策记录 （拆分自 task_plan.md）

## 定位和功能描述

APP 启动器图标设计。纯设计文档，无独立实现阶段。任务规划见 [task_plan.md](../task_plan.md)。

# 研究发现、技术决策 （拆分自 findings.md）

> 详见：[findings.md](../findings.md) — 2026-05-21 APP 图标设计

## 设计理念

图标作为品牌门面，必须同时承担两个职能：

1. **功能识别度**：陌生用户在应用商店或启动器中一眼看出"这是任务/待办类 App"
2. **品牌气质呼应**：与"恰恰有事 / 刚好，手边有事"的从容、不催促、贴近生活气质一致

主元素采用**任务清单**这一通用可识别符号，差异化通过"中间那一条被高亮"实现——既是清单项，又是 Just Now 语义的双关："此刻，刚好这一条"。

## 核心元素

- 三条横向任务清单
- 每条左侧一个圆角小方框复选框
- 中间那条整条用品牌高亮色 `#DAE134` 铺底，复选框已勾选
- 上下两条灰调、未勾选

## 整体构图

viewport 108 x 108，中心 66 x 66 安全区。三条任务条全部布置在 x in [22, 86]、y in [23, 89] 范围内。

| 元素 | 顶 y | 高度 | 底 y |
|------|------|------|------|
| 任务条 1 | 23 | 18 | 41 |
| 间距 | — | 6 | — |
| 任务条 2（高亮） | 47 | 18 | 65 |
| 间距 | — | 6 | — |
| 任务条 3 | 71 | 18 | 89 |

## 关键设计决策

1. **任务条 1/3 描边、任务条 2 不描边**：视觉上让中间那条"实"起来
2. **横线粗细差异**（1.5px vs 2px）：高亮条的"标题"更扎实
3. **复选框统一是圆角小方块（非圆点）**：明确传达"任务"语义
4. **不放置文字、时钟指针**：避免小尺寸下糊掉
5. **不参与 Themed Icons 主题化**：移除 `<monochrome>` 引用，单色化会让三条视觉相同、语义丢失

### 配色
- 背景 `#FFFDF6` 温暖米色（与 `regular_period_note_background` 同色）
- 高亮 `#DAE134`（品牌黄绿）
- 灰任务条 `#F1F3F4` + 描边 `#9AA0A6`

## 实施落点

- `res/drawable/ic_launcher_foreground.xml` — 前景层 VectorDrawable
- `res/drawable/ic_launcher_background.xml` — 背景层 VectorDrawable
- `mipmap-anydpi/ic_launcher.xml` / `ic_launcher_round.xml` — 已移除 `<monochrome>` 引用
- 各分辨率位图 `mipmap-*dpi/ic_launcher.webp` 由 Image Asset Studio 从矢量图导出

## 依赖
- 品牌体系（modules/brand.md）— 产品名、标语、气质
- UI 设计规范（modules/ui-design.md）— 高亮色 `#DAE134`、卡片视觉语言

# 进度日志 （拆分自 progress.md）

- [x] 替换 `ic_launcher_foreground.xml` 为新清单设计
- [x] 替换 `ic_launcher_background.xml` 为 `#FFFDF6` 纯色
- [x] 由 Image Asset Studio 重新生成 `mipmap-*dpi` 下位图
- [ ] 在真机/模拟器上验证圆形、方形、squircle 三种遮罩下显示效果

**状态**：🔧 第一版已完成，待真机验证遮罩效果
