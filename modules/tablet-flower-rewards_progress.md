### 2026-07-18 — 花瓣按象限加权 3/2/2/1 + 花芯填充逻辑

- 花瓣按象限加权：Q1=3/Q2=2/Q3=2/Q4=1（JOIN 任务表获取 quadrant）
- 花芯默认虚线，每天首个紧急重要任务填花芯（不计花瓣），后续同象限才给 3 瓣
- FlowerCapsuleView 新增 mCenterFilled 状态

### 2026-07-18 — 审查修复：花朵栏拆 Fragment + 补拍按钮资源化
- 花朵收集栏拆为 RewardBarFragment（~340 行），承载七朵花+补拍按钮全部逻辑，自己通过 getGlobalThemeColor() 获取主题色
- 补拍按钮文本资源化：s_retroactive_photo / s_retroactive_photo_count / s_retroactive_remind_title / s_retroactive_go_shoot / s_retroactive_later / s_retroactive_remind_msg，四语翻译
- 恢复 updateFlowerCapsuleLayoutOrientation 逻辑（横屏竖向一列+照片图标居上+分隔线；竖屏横向一排+照片图标居左）
- FragmentContainerView→FrameLayout 占位+程序化挂载，解决平板测量异常导致边框消失和位置漂移

### 2026-07-18 — 内置活动分类更名与屏幕映射 Bug 彻底修复
- 落实方案 A，将内置活动分类“益智”更名为更具体的“桌游”（英文 Board Game），以避开与玩具重叠的泛益智界定。
- 彻底修复了“屏幕”与 `animation` 之间因早期硬编码为“动画”产生的映射 Bug。统一了 `TagLocalizer` 的解析网关、`TagDao` 的数据库拦截范围（将 '动画' 改为 '屏幕'）、以及简繁体 `strings.xml`（繁体为 '螢幕'）的翻译对齐，确保常用标签栏能够 100% 正确拦截和剔除“屏幕”内置标签。

### 2026-07-18 — 代码审查
- 代码审查记录于 `docs/code-review-20260718.md`，涉及时光胶囊/照片墙/补拍流程相关问题共 5 项。

### 2026-07-18 — 成果栏直角主题色边框与沉浸式标准 Activity 照片墙交付
- 移除了多余的属性动画和所有九宫格雕花图片资源，通关时只在 Java 层动态通过 GradientDrawable 渲染 2dp 直角主题色描边，完美符合与主界面直边拼贴的设计规范。
- 重构成果照片墙为标准沉浸式 TimeCapsuleWallActivity 页面，使系统状态栏颜色与顶部标题栏完全一致（无缝融合一体），支持左上角白色返回箭头返回，去除多余关闭按钮，提供彻底的只读功能。
- 彻底移除了任何删除照片红小叉及相关的删除数据库逻辑，保障成果墙照片只读、不可被删除。
- 修复了相册返回时由于首次加载判定缺陷导致的偶发性误弹出通关祝贺对话框 Bug。
- 重构了 Monday 开始时间戳计算算法，使用相对数学偏移避开了 Java Calendar 处于不同 Locale / WeekDay 规则下的计算漂移，保障成果墙精确仅显示当周的照片记录。

### 2026-07-17 — 时光胶囊与设置主题色全局对齐及去紫色重构
- 确立统一主题色设计，去除多余无用设置菜单，仅保留“设置主题色”菜单。
- 确立花瓣颜色与主题色动态跟随的逻辑，拟重构 FlowerCapsuleView。
- 确立两大对话框像素级贴边 Header 对齐及强制去紫色字体的动态配置方案。
