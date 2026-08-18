### 2026-08-18 — 平板端任务扫码传图系统落地与七朵花奖励联动

- **免安装扫码传图系统实现**：
  - 新增 `NetworkUtils` 与 `QrCodeUtils`（集成 `zxing-core` 3.5.3），实现局域网 IPv4 / 热点 IP 探测与二维码位图生成。
  - 新增基于标准 `ServerSocket` 的轻量嵌入式 `TaskPhotoHttpServer` 与流式 `MultipartStreamParser`，支持 10s 超时防护与多图流式落盘入库。
  - 新增手机端静态 H5 `upload.html`，实现 1s 心跳、Canvas 智能压缩（确保 < 3MB）、多图预览与 FormData 上传。
  - 新增 `QrUploadDialog` 弹窗，实现双端 5s 对等离线感知、连接常亮控制、自然休眠倒计时联动及多语言系统相机扫码引导。
  - `TaskPhotoListDialog` 新增独立「📱 扫码传图」操作按钮，上传成功后即时刷新列表已拍张数并联动重算七朵花花瓣奖励。
- **审查与复核**：基于代码审查优化了 Socket 读超时防护、手机连接期间弹窗休眠定时器暂缓与断开重置、RFC 标准 CRLF 边界处理及多语言文案本地化。

### 2026-08-18 — 平板端任务扫码传图系统设计规范与历史残留精简

- **手机免安装局域网扫码传图**：针对户外活动不便携带平板拍照场景，完成扫码传图架构设计。平板端基于 Android 原生 `com.sun.net.httpserver.HttpServer` 按需启动嵌入式 HTTP 服务并展示包含局域网 IP 与随机 Token 的二维码；手机自带相机/扫码直接打开平板托管的纯静态 H5 单页。
- **双端 5 秒对等心跳与休眠生命周期**：手机端 1 秒发送 `/api/ping` 心跳，双端统一在 5 秒无心跳时对等感知离线；平板二维码弹窗超时以 `min(systemTimeout, 3分钟)` 为准，手机连接期间临时保持常亮（`FLAG_KEEP_SCREEN_ON`）。
- **手机端 Canvas 智能压缩 (<3MB)**：大于 3MB 或超高分辨率照片在手机前端通过 HTML5 Canvas 等比缩放到 2048px 高清（质量 0.92，体积 800KB~1.8MB 极速秒传），小于 3MB 原图直传；单任务严格对齐 5 张配额限制并流式入库。
- **历史文档矫正**：在 `tablet-flower-rewards.md` 中清除历史残留的 TTS 语音与花瓣点亮动画描述，保持文档与代码事实一致。

### 2026-08-01 — 儿童奖励系统升级：每周通关天数自定义 + 非寒暑假工作日花芯默认填充

- 将右上角“查看内置图标”重命名为“儿童奖励设置”菜单，在弹窗中集成 2~5 天每周通关目标天数配置（默认 3 天），存储于 SharedPreferences。
- `RewardBarFragment` 动态读取通关目标天数 `targetDays`，满足 `activeCount >= targetDays` 即触发通关祝贺与描边。
- `RewardBarFragment.refreshWeeklyFlowers()` 利用 `PeriodGroupRuleResolver.isWorkdaySync(cal)` 与寒暑假判定，非寒暑假工作日默认 `centerFilled[i] = true`，使紧急重要任务可直接获得花瓣。
- 根据审查意见优化 `RewardBarFragment` 与 `MainFragment` 的异步回调安全性防护。
- 增加 `ChildRewardSystemTest` 单元测试并通过验证。

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
