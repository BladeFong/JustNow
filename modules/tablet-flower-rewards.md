# 阶段规划、决策记录 (时光胶囊与七朵花花瓣奖励机制)

## 1. 概述与定位
为平板端（及非平板端）引入“时光胶囊照片回顾”与“七朵花点亮”的游戏化奖励机制。通过统一的主题色系统消除界面突兀色（如默认的 Material 3 紫色），规范两大祝贺弹窗（单任务完成及周大奖通关）的视觉与发声行为。

## 2. 规划步骤
- **第一阶段：主题色 SharedPreferences 存储与设置弹窗实现**
  - 在 SharedPreferences 中定义全局主题色存储。
  - 新增“设置主题色”菜单，点击弹出单选题对话框，支持“活力蓝”与“童趣粉”切换。
- **第二阶段：主界面及花朵已点亮花瓣颜色动态绑定**
  - 提取当前全局激活的主题色，动态对主界面的“添加任务按钮”和“补拍按钮”进行染色覆盖。
  - 修改 `FlowerCapsuleView`，使其已点亮花瓣的颜色（`mActiveColor`）与此主题色完全统一。
- **第三阶段：两大祝贺对话框去紫色与像素级原型对齐**
  - 重构 `CongratulationDialog`，添加顶部高 48dp 贴边 Header 栏，并在 Java 中动态修改按钮和字体的颜色为任务象限色，消除紫色。
  - 重构 `CongratulationsDialog`，使其我知道啦按钮的背景与字体色随全局主题色变化，消除紫色。
  - TTS/音效使用 `getApplicationContext()` 以及强行映射音频通道为 `STREAM_MUSIC` 进行发声，消除静音免打扰导致的无声问题。

---

# 研究发现、技术决策、需求分析

## 1. 缺陷分析与根因
* **无声问题根因**：
  1. `TextToSpeech` 实例化时使用了 Dialog 的 `getContext()`。在 Service 绑定时 Dialog Context 易导致失效，需一律使用全局的 `getApplicationContext()`。
  2. 部分 Android 测试设备（尤其是模拟器）关闭了 Notification 音量通道，或者未安装中文离线 TTS 包。
* **紫色字体与突兀色根因**：默认使用 Material 3 组件（如 `MaterialButton` 的 TextButton 样式）时，字体颜色会默认套用 App 主题中定义的主色（Primary Color，即紫色）。必须在 Java 中通过 `setTextColor()` 或 `setBackgroundTintList()` 显式覆盖。

## 2. 技术选型决策
* **音效强行映射**：铃声音效在播放时强行通过 `setLegacyStreamType(AudioManager.STREAM_MUSIC)` 路由至多媒体音乐通道，绕过通知音静音屏蔽，保障 100% 能够发出声音。
* **主题色自适应机制**：
  - 非平板模式默认：`🔵 活力蓝 (#1A73E8)`。
  - 平板模式默认：`🌸 童趣粉 (#FF4081)`。
  - 允许用户手动覆盖，并在确认修改时立即通知主界面及自定义 View 进行 `invalidate()` 重绘刷新。
