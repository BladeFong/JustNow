### 2026-07-26 — 多用户 Robolectric 测试修复 + convertToChore 执行记录修复

- AppDatabase 新增 setTestInstance/clearTestInstance 测试注入入口
- 两测试类适配多用户数据库（setTestInstance 替代反射注入 sInstances）
- performShortCompletionSync：convertToChore 前保存 executingStartMs，转换后恢复以写执行记录
- 测试断言更新：短完成应写执行记录

### 2026-07-26 — 死代码清理 + 过滤逻辑精简

- 删除 ChoreHiddenTodayStore 整个类及测试
- MainViewModel 移除 mChoreHiddenStore 字段
- TaskFilterHelper 移除 ChoreHiddenTodayStore 隐藏 + hideCompletedChoresForToday 冗余步骤
- TimelineBuilder 删除 hideCompletedChoresForToday 方法
- TaskPhotoDao/Repository 删除 getCompletedTasksWithoutPhotos（旧补拍专用）
- TaskExecutionDao 删除 getCompletedExecutionsBetween（无调用方）
- 两侧过滤统一：todayCompletedIds 一次构建，日/周月年/琐碎共用

### 2026-07-26 — 任务完成统一流程 + isChildTask 判断封装

- 新增 completeTaskUnified 统一入口，短完成写 status=3 执行记录
- isChildTask = isTablet && iconName != null，封装在 JustNowApplication
- 拍照按钮/列表加 isChildTask 过滤，拍照查询去 status=0 限制
- 移除 ChoreHiddenTodayStore.hideForToday

### 2026-07-21 — 成果墙全屏浏览限定时间范围 + 无效照片自愈

- 全屏浏览改用 getPhotosForTaskInRange 限定当前周期，修复跨天旧照片显示为空白幽灵页
- ContentResolver 校验 URI + 自动删除无效 DB 记录（自愈）
- 拍照按钮改为当天有已完成或执行中任务才显示

### 2026-07-21 — 任务多照片支持与完成前拍照实现完成

- 每任务最多 5 张照片，右下角补拍按钮改为通用拍照按钮
- 花瓣只计每任务每周首张（getFirstPhotoPerTaskInRange + Set 去重）
- 成果墙按任务去重 + ViewPager2 左右划动浏览所有照片
- RetroactivePhotoDialog → TaskPhotoListDialog（通用任务拍照列表）
- CongratulationDialog 满5张 Toast 拒绝
- 拍照成功后返回任务列表供继续拍照

### 2026-07-21 — 任务多照片支持与完成前拍照设计规范

- 写入设计文档 `docs/superpowers/specs/2026-07-21-task-photo-multi-and-precompletion-design.md`

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
