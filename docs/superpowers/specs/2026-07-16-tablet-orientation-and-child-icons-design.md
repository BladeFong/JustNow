# JustNow 平板端横竖屏放开与儿童兴趣活动图标适配设计规范

## 1. 概述 (Overview)
本项目旨在优化 JustNow 应用在平板端（大屏设备）的使用体验，特别是针对儿童居家寒暑假的学习与活动引导场景。本期设计主要解决两个核心问题：
1. **解除平板的强制横屏锁定**：允许平板在横竖屏间自由切换，同时手机保持强制竖屏。
2. **适配横竖屏任务列表布局**：平板横屏时右侧任务网格展示 3 列，平板竖屏时展示 2 列，以保证文字和图标的良好展示，防止排版拥挤或文字折行。
3. **引入儿童兴趣活动图标**：内置 10 个适合儿童居家暑期/寒假生活场景的矢量图标，方便家长或儿童在新建任务时点选展示。

---

## 2. 屏幕方向与布局适配设计 (Orientation & Layout Design)

### 2.1 运行时屏幕旋转限制逻辑调整
当前项目在 `JustNowApplication` 中注册了全局生命周期回调，通过硬编码将平板锁定为 `SCREEN_ORIENTATION_LANDSCAPE`（横屏）。
为放开限制，我们将使用 Android 资源限定符（Resource Qualifiers）定义的布尔值来代替硬编码检测，使判断逻辑更标准、且允许平板自由转屏。

1. **定义设备属性 bool 值**：
   * 默认资源目录 `res/values/bools.xml`（适用于普通手机）：
     ```xml
     <resources>
         <bool name="is_tablet">false</bool>
     </resources>
     ```
   * 平板资源目录 `res/values-sw600dp/bools.xml`（适用于最小宽度 ≥ 600dp 的平板设备）：
     ```xml
     <resources>
         <bool name="is_tablet">true</bool>
     </resources>
     ```
2. **全局生命周期回调逻辑修改 (`JustNowApplication.java`)**：
   在 `onActivityCreated` 时：
   * 读取 `resources.getBoolean(R.bool.is_tablet)`。
   * 如果为 `false`（即手机设备）：强制调用 `activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)`。
   * 如果为 `true`（即平板设备）：**不主动干预屏幕方向**（由系统默认行为控制，即可横屏也可竖屏）。

### 2.2 任务列表网格列数自适应配置
主界面右侧的任务池（`RecyclerView`）将采用 `GridLayoutManager`，并利用资源限定符控制 `spanCount` 实现完全的零代码侵入列数自适应：

1. **手机端默认** (`res/values/integers.xml`)：
   ```xml
   <integer name="task_grid_span_count">1</integer>
   ```
2. **平板竖屏** (`res/values-sw600dp/integers.xml`)：
   ```xml
   <integer name="task_grid_span_count">2</integer>
   ```
3. **平板横屏** (`res/values-sw600dp-land/integers.xml`)：
   ```xml
   <integer name="task_grid_span_count">3</integer>
   ```
4. **绑定机制**：
   在 `MainActivity` 中获取 `resources.getInteger(R.integer.task_grid_span_count)`，并在初始化 `RecyclerView` 时设置给 `GridLayoutManager`。当屏幕旋转时，由于配置更改重建或资源重载，系统会自动加载并应用正确的列数。

---

## 3. 任务数据结构与儿童兴趣图标设计 (Data & Icons Design)

### 3.1 10 个内置儿童兴趣活动图标 (矢量)
剔除了作息、吃饭等常规项目，保留了 10 个纯粹的儿童居家兴趣活动图标，全部采用矢量图（`.xml`）内置于 `res/drawable/`：

| 图标 ID | 辅助名称 | 视觉元素推荐 | 覆盖场景 |
|---|---|---|---|
| `ic_activity_blocks` | 玩具 | 积木块 / 小汽车 | 自由玩耍、乐高搭建 |
| `ic_activity_book` | 阅读 | 打开的书本 / 绘本 | 听故事、看绘本、课外阅读 |
| `ic_activity_palette` | 美术 | 调色盘与画笔 | 简笔画、涂色、艺术手工 |
| `ic_activity_music` | 音乐 | 五线谱八音符 / 键盘钢琴 | 唱歌、跳舞、听儿歌、弹琴 |
| `ic_activity_ball` | 运动 | 皮球 / 足球 | 户外拍球、跳绳、体育锻炼 |
| `ic_activity_game_puzzle` | 益智 | 拼图拼块 / 棋子 | 拼图、智力桌游、益智游戏、走迷宫 |
| `ic_activity_craft` | 手工 | 剪刀与折纸 / 橡皮泥 | 创意折纸、手工捏泥、简易DIY |
| `ic_activity_animation` | 屏幕 | 电视机 / 投影镜头 | 看动画片、英语绘本视频、屏幕时间 |
| `ic_activity_study` | 学习 | 铅笔与课本 | 寒暑假书面作业、练字、口算练习 |
| `ic_activity_chores` | 家务 | 小扫帚与簸箕 / 浇水壶 | 整理玩具箱、扫地擦桌扫地、给花草浇水 |

### 3.2 数据库 Schema 变更与迁移

#### 3.2.1 任务表 `tasks` 扩展字段
在 `TaskEntity.java` 中新增如下 Room 字段：
```java
/**
 * 内置儿童兴趣活动图标标识。
 * 可为空，为 null 时表示该任务不显示图标（即纯文本卡片模式）。
 * 对应值为 3.1 中的图标资源名称后缀（如 "blocks", "book", "palette", "music", "ball", "game_puzzle", "craft", "animation", "study", "chores"）。
 */
@ColumnInfo(name = "icon_name", defaultValue = "NULL")
public String iconName;
```

#### 3.2.2 Room 数据库升级
* **数据库版本号更新**：在 `AppDatabase.java` 中将 `@Database` 属性中的 `version` 从 `7` 升级为 `8`。
* **定义 Migration_7_8 升级脚本**：
  ```java
  private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
      @Override
      public void migrate(@NonNull SupportSQLiteDatabase database) {
          database.execSQL("ALTER TABLE tasks ADD COLUMN icon_name TEXT DEFAULT NULL");
      }
  };
  ```
* **注册迁移**：在 `AppDatabase.getInstance` 的 `Room.databaseBuilder` 链中，使用 `.addMigrations(..., MIGRATION_7_8)` 注册此迁移，保证用户设备数据平滑保留。

---

## 4. UI/UX 详细交互设计 (UI/UX Details)

### 4.1 待办列表任务卡片 UI 调整
卡片设计在手机端和平板端完全复用相同的 XML 结构，细节改动如下：
* **四象限彩色边条**：依然保持为卡片最左侧的实色彩色边条，以区分紧急/重要程度。
* **内置图标位置**：在彩色条右侧，新增一个 `ImageView` 作为图标容器：
  * **有图标**：`iconName` 不为空，动态反射获取 `R.drawable.ic_activity_[iconName]` 并加载；`ImageView` 为显示状态 (`View.VISIBLE`)。
  * **无图标**（默认）：`iconName` 为空，`ImageView` 强制置为 `View.GONE`。卡片右侧的文字链自动拉伸占满彩色条后的所有空间。
* **双行内容区**（图标右侧）：
  * **第一行**：`时长` + `#[标签]`（如：`2小时 #工作` 或 `30分钟 #学习`）。**绝对不显示开始时间/钟点字段。**
  * **第二行**：任务标题/内容（例如：`个人项目` 或 `背单词`）。

### 4.2 添加/编辑任务页面 (`TaskInputActivity`) 图标选择组件
在创建或修改任务时，新增对内置图标的选择和取消逻辑。

1. **设备白名单与隐藏**：
   * **手机端屏蔽**：由于儿童兴趣活动图标主要面向大屏/平板儿童场景，手机端**不显示图标设置入口**。在运行时通过 `resources.getBoolean(R.bool.is_tablet)` 进行判定，若为 `false`，则将图标选择卡片 `card_icon_selector` 设为 `View.GONE` 隐藏；只有平板上才显示。
2. **界面排布**：
   在现有的“标签选择”和“时长选择”卡片下方，增加一个“选择小图标”的区域：
   * **平板横屏**：由于横向空间极其充裕，10 个图标呈 **1 行 10 列** 水平排列，无需水平滚动，完全平铺。
   * **平板竖屏**：呈 **2 行 5 列** 的网格排列，保证不挤压且对称美观。
3. **选择与取消交互逻辑**：
    * **没有单独的“无图标”占位按钮**，默认状态下 10 个按钮均未被激活。
    * **内置标签自动绑定**：每个内置图标都与一个中文标签（辅助名称本身）相对应。当选中某个图标时，系统会自动在“标签输入框”中填入对应标签（例如，点击“画画”图标，自动填入“美术”）。当反选取消该图标时，若当前标签输入框中的内容与该图标对应的标签名一致，则自动将标签输入框清空。若选中其他图标，则将标签自动更新为新图标对应的标签。
    * 点击任何一个图标按钮，该按钮呈现激活背景（带马卡龙强调色圆形背景与高亮边框），并把对应的 `iconName` 值存入临时状态中。
    * 点击已选中的图标：**将其反选（取消选择）**，按钮状态恢复为常规未激活态，临时状态中 `iconName` 清空（存为 `null`）。
    * 点击其他图标：将前一次选中的按钮恢复为未激活状态，高亮选中当前点击的图标，实现单选。
4. **内置标签排除展示机制**：
    * 这 10 个内置标签（“玩具”、“阅读”、“美术”、“音乐”、“运动”、“益智”、“手工”、“动画”、“学习”、“家务”）仅作为系统与儿童图标自动关联的元标签，**不得**出现在编辑页面下方的“已有常用标签 ChipGroup”或主界面的最近常用标签栏中。
    * 在 Room 数据库的 `TagDao.java` 里的 `getTopTags` SQL 语句中，添加对这 10 个名称的 `NOT IN` 排除过滤，在数据源层面对其物理隔离。
5. **数据存盘**：
    点击“保存”按钮时，将当前的 `iconName` 值写入任务数据结构并调用 `insert` / `update` 保存至 Room 数据库。

### 4.3 右上角临时内置图标预览菜单（调试与预览用）
为了便于非平板（如手机）用户和开发者在测试时查看这 10 个内置矢量图标的效果，我们将在项目主界面的右上角菜单中临时添加一个“查看内置图标”的项：
1. **菜单项定义**：
   在 `R.menu.menu_main` 菜单中新增：
   ```xml
   <item
       android:id="@+id/action_preview_icons"
       android:title="查看内置图标"
       app:showAsAction="never" />
   ```
2. **点击响应与 UI**：
   在 `MainFragment.java` 的 `onOptionsItemSelected` 中捕获此点击事件，弹出一个自定义的 `AlertDialog`：
   * 对话框内包含一个 Grid 视图（或 RecyclerView 列表），对称平铺展示全部 10 个内置矢量图标及其名称（玩具、阅读、美术、音乐等），点击“关闭”按钮退出。
   * 手机和平板均可通过此菜单项实时预览 10 个矢量图在大图模式下的细节与色彩效果。

---

## 5. 边界与测试用例 (Edge Cases & Testing)

1. **平板横竖屏动态切换时的列数重载**：
   * *场景*：平板从横屏转为竖屏时。
   * *表现*：`MainActivity` 重新加载资源 `task_grid_span_count`，从 3 列变为 2 列，`RecyclerView` 布局管理器自动进行网格重绘，无卡死和错位。
2. **升级应用时的数据库完整性**：
   * *场景*：已有 version 7 数据库的用户安装包含此功能的新版本。
   * *表现*：`MIGRATION_7_8` 自动执行，原所有任务的 `icon_name` 自动初始化为 `NULL`，不发生闪退，不丢失用户历史数据。
3. **无图标任务的展示**：
   * *场景*：导入无图标的任务（或者以往的历史任务）。
   * *表现*：卡片中图标 `ImageView` 被隐藏（`View.GONE`），文字展示完整，布局正常无空档。
