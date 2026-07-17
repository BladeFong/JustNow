# 平板端横竖屏放开与儿童兴趣活动图标适配实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 解除平板端的强制横屏方向死锁（允许转屏而手机锁定竖屏），实现主页待办任务网格在平板横屏（3列）和竖屏（2列）下的自适应列数，并集成 10 个内置儿童兴趣矢量图标及新建/编辑界面对称平铺单选/反选交互。

**Architecture:** 
1. 通过布尔值资源和 `registerActivityLifecycleCallbacks` 在运行时动态判定设备类型并锁定手机为竖屏，放开平板控制。
2. 采用系统自带的屏幕资源限定符，将任务列表 Grid 列数（`task_grid_span_count`）定义在默认、`sw600dp`、`sw600dp-land` 目录中，达到零代码侵入的转屏自适应。
3. 扩展 Room 数据库的 `tasks` 表，新增 `icon_name` 列并提供 `MIGRATION_7_8` 无损升级；在编辑界面引入对称网格排列的图标单选适配器，完成数据联动和反选。

**Tech Stack:** Java, Jetpack (Room, Navigation, CardView, RecyclerView), Material Design 2.

## Global Constraints
- **代码及注释规范**：新增和修改的代码、注释及提交消息必须使用简体中文。
- **布局一致性**：任务卡片绝对不显示开始时间/钟点等属性，只保留时长和标签。
- **图标对称性**：图标选择器在横屏单行展示全部 10 个，在竖屏双行每行展示 5 个，默认不选中即为无图标，允许反选（点击已选中可取消选择）。

---

### Task 1: 设备方向锁定放开

**Files:**
- Create: `app/src/main/res/values/bools.xml`
- Create: `app/src/main/res/values-sw600dp/bools.xml`
- Modify: `app/src/main/java/com/nearby/justnow/JustNowApplication.java`
- Modify: `app/src/test/java/com/nearby/justnow/JustNowApplicationTest.java`

**Interfaces:**
- Consumes: None
- Produces: 资源 `R.bool.is_tablet`

- [ ] **Step 1: 创建手机与平板判定资源布尔值**

  创建 `app/src/main/res/values/bools.xml`，默认手机为 false：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <bool name="is_tablet">false</bool>
  </resources>
  ```

  创建 `app/src/main/res/values-sw600dp/bools.xml`，平板为 true：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <bool name="is_tablet">true</bool>
  </resources>
  ```

- [ ] **Step 2: 修改 Application 方向锁定逻辑**

  修改 `app/src/main/java/com/nearby/justnow/JustNowApplication.java`，将 91-96 行的硬编码锁定逻辑替换为读取 `R.bool.is_tablet` 资源：
  ```java
          // 全局屏幕方向锁定：手机强制竖屏，平板允许旋转（不限制）
          registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
              @Override
              public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                  boolean isTablet = activity.getResources().getBoolean(R.bool.is_tablet);
                  if (!isTablet) {
                      activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                  }
              }
              @Override public void onActivityStarted(Activity activity) {}
              @Override public void onActivityResumed(Activity activity) {}
              @Override public void onActivityPaused(Activity activity) {}
              @Override public void onActivityStopped(Activity activity) {}
              @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
              @Override public void onActivityDestroyed(Activity activity) {}
          });
  ```

- [ ] **Step 3: 编写并运行单元测试验证布尔值资源和方向锁定逻辑**

  在 `app/src/test/java/com/nearby/justnow/JustNowApplicationTest.java`（如果文件不存在则新建此文件）中添加测试用例，校验默认配置和限定符配置下的 `is_tablet` 资源读取：
  ```java
  package com.nearby.justnow;

  import static org.junit.Assert.assertFalse;
  import android.content.Context;
  import androidx.test.core.app.ApplicationProvider;
  import org.junit.Test;
  import org.junit.runner.RunWith;
  import org.robolectric.RobolectricTestRunner;

  @RunWith(RobolectricTestRunner.class)
  public class JustNowApplicationTest {
      @Test
      public void testIsTabletResourceDefaultIsFalse() {
          Context context = ApplicationProvider.getApplicationContext();
          boolean isTablet = context.getResources().getBoolean(R.bool.is_tablet);
          assertFalse("默认配置下 is_tablet 应为 false", isTablet);
      }
  }
  ```

- [ ] **Step 4: 运行测试**

  运行：`./gradlew testDebugUnitTest --tests com.nearby.justnow.JustNowApplicationTest`
  预期：测试通过（PASS）

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/res/values/bools.xml app/src/main/res/values-sw600dp/bools.xml app/src/main/java/com/nearby/justnow/JustNowApplication.java app/src/test/java/com/nearby/justnow/JustNowApplicationTest.java
  git commit -m "feat: 引入is_tablet资源并放开平板屏幕方向死锁"
  ```

---

### Task 2: 主网格任务列数自适应配置

**Files:**
- Create: `app/src/main/res/values/integers.xml`
- Create: `app/src/main/res/values-sw600dp/integers.xml`
- Create: `app/src/main/res/values-sw600dp-land/integers.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**Interfaces:**
- Consumes: 资源 `R.integer.task_grid_span_count`
- Produces: 主页待办 RecyclerView 以自适应的 GridLayoutManager 渲染

- [ ] **Step 1: 创建不同屏幕与方向下的网格列数资源**

  创建默认配置 `app/src/main/res/values/integers.xml`（手机端 1 列）：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <integer name="task_grid_span_count">1</integer>
  </resources>
  ```

  创建平板竖屏配置 `app/src/main/res/values-sw600dp/integers.xml`（平板竖屏 2 列）：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <integer name="task_grid_span_count">2</integer>
  </resources>
  ```

  创建平板横屏配置 `app/src/main/res/values-sw600dp-land/integers.xml`（平板横屏 3 列）：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <integer name="task_grid_span_count">3</integer>
  </resources>
  ```

- [ ] **Step 2: 绑定 RecyclerView 布局管理器列数**

  编辑 `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`，将初始化任务列表时设置给 RecyclerView 的 LayoutManager 从原有的 spanCount 改为从资源文件动态获取：
  定位到设置 RecyclerView 处（通常在 `onViewCreated` 内），替换为：
  ```java
          int spanCount = getResources().getInteger(R.integer.task_grid_span_count);
          getBinding().rvTasks.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
  ```
  *(注：如果原本是用 LinearLayoutManager，请替换为由 spanCount 驱动的 GridLayoutManager)*。

- [ ] **Step 3: 编译与运行测试**

  运行：`./gradlew assembleDebug`
  预期：编译通过，资源装配正确。

- [ ] **Step 4: 提交**

  ```bash
  git add app/src/main/res/values/integers.xml app/src/main/res/values-sw600dp/integers.xml app/src/main/res/values-sw600dp-land/integers.xml app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
  git commit -m "feat: 采用资源限定符实现待办列表网格列数的横竖屏自适应"
  ```

---

### Task 3: 数据库 Schema 升级与数据模型扩展

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/entity/TaskEntity.java`
- Modify: `app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java`
- Modify: `app/src/test/java/com/nearby/justnow/data/repository/TaskRepositoryTest.java`

**Interfaces:**
- Consumes: None
- Produces: 属性 `TaskEntity.iconName`，接口 `TaskInputViewModel.getIconName()` & `TaskInputViewModel.setIconName(String)`

- [ ] **Step 1: 在 TaskEntity 中增加 icon_name 字段**

  打开 `app/src/main/java/com/nearby/justnow/data/entity/TaskEntity.java`，在类成员变量中追加如下定义（通常可加在 `quota` 之后）：
  ```java
      /** 
       * 内置儿童兴趣活动图标标识，为 null 时不展示图标。
       * 值为: 'blocks', 'book', 'palette', 'music', 'ball', 'game_puzzle', 'craft', 'animation', 'study', 'chores' 
       */
      @ColumnInfo(name = "icon_name", defaultValue = "NULL")
      public String iconName;
  ```

- [ ] **Step 2: 升级数据库并编写 Migration 脚本**

  打开 `app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java`：
  1. 将 `@Database` 注解中的 `version = 7` 更改为 `version = 8`。
  2. 在类主体中（比如在 `MIGRATION_6_7` 下方）新增静态常量 `MIGRATION_7_8`：
     ```java
         private static final Migration MIGRATION_7_8 = new Migration(7, 8) {
             @Override
             public void migrate(@NonNull SupportSQLiteDatabase database) {
                 database.execSQL("ALTER TABLE tasks ADD COLUMN icon_name TEXT DEFAULT NULL");
             }
         };
     ```
  3. 在 `getInstance(Context context)` 里的 `.addMigrations(...)` 调用中，追加 `MIGRATION_7_8`：
     ```diff
     - .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
     + .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
     ```

- [ ] **Step 3: 扩展 ViewModel 属性操作入口**

  编辑 `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java`，添加对外数据交互方法：
  ```java
      public String getIconName() {
          return mDraftTask != null ? mDraftTask.iconName : null;
      }

      public void setIconName(String iconName) {
          if (mDraftTask != null) {
              mDraftTask.iconName = iconName;
          }
      }
  ```

- [ ] **Step 4: 编写测试类验证新增字段存取**

  在 `app/src/test/java/com/nearby/justnow/data/repository/TaskRepositoryTest.java` 中增加单元测试用例，验证新字段的数据库存取正常：
  ```java
      @Test
      public void testTaskIconNamePersistence() {
          TaskEntity task = new TaskEntity();
          task.content = "测试内置图标任务";
          task.iconName = "palette"; // 选择美术图标
          task.createdAt = System.currentTimeMillis();

          long id = mTaskRepo.insertSync(task);
          TaskEntity retrieved = mTaskRepo.getByIdSync(id);

          org.junit.Assert.assertNotNull(retrieved);
          org.junit.Assert.assertEquals("palette", retrieved.iconName);
      }
  ```

- [ ] **Step 5: 运行数据库相关单元测试**

  运行：`./gradlew testDebugUnitTest --tests com.nearby.justnow.data.repository.TaskRepositoryTest`
  预期：测试用例全部通过（PASS）。

- [ ] **Step 6: 提交**

  ```bash
  git add app/src/main/java/com/nearby/justnow/data/entity/TaskEntity.java app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java app/src/main/java/com/nearby/justnow/ui/taskinput/TaskInputViewModel.java app/src/test/java/com/nearby/justnow/data/repository/TaskRepositoryTest.java
  git commit -m "feat: 升级数据库到版本8，新增任务内置图标icon_name字段及对应迁移逻辑"
  ```

---

### Task 4: 内置儿童兴趣活动矢量图标导入

**Files:**
- Create: 10 个矢量图资源文件：
  - `app/src/main/res/drawable/ic_activity_blocks.xml`
  - `app/src/main/res/drawable/ic_activity_book.xml`
  - `app/src/main/res/drawable/ic_activity_palette.xml`
  - `app/src/main/res/drawable/ic_activity_music.xml`
  - `app/src/main/res/drawable/ic_activity_ball.xml`
  - `app/src/main/res/drawable/ic_activity_game_puzzle.xml`
  - `app/src/main/res/drawable/ic_activity_craft.xml`
  - `app/src/main/res/drawable/ic_activity_animation.xml`
  - `app/src/main/res/drawable/ic_activity_study.xml`
  - `app/src/main/res/drawable/ic_activity_chores.xml`

**Interfaces:**
- Consumes: None
- Produces: 10 个可在应用内加载的 Drawable 资源

- [ ] **Step 1: 写入 10 个矢量 XML 图标资源**

  *(为确保矢量图可以正常编译渲染，使用通用标准的 SVG 路径声明)*

  1. `app/src/main/res/drawable/ic_activity_blocks.xml` (玩具/积木)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M3,3H10V10H3V3 M14,3H21V10H14V3 M3,14H10V21H3V14 M14,14H21V21H14V14" />
     </vector>
     ```

  2. `app/src/main/res/drawable/ic_activity_book.xml` (阅读/绘本)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FF34A853"
             android:pathData="M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z" />
     </vector>
     ```
     *(注：为演示绘本，我们使用标准的心形或书本路径作为图形承载，以上使用标准的 Google material icon 结构，后同)*

  3. `app/src/main/res/drawable/ic_activity_palette.xml` (美术/画笔)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M12,2C6.49,2 2,6.49 2,12C2,17.51 6.49,22 12,22C13.38,22 14.5,20.88 14.5,19.5C14.5,18.86 14.24,18.27 13.82,17.84C13.4,17.41 13.14,16.82 13.14,16.18C13.14,14.8 14.26,13.68 15.64,13.68H18C20.21,13.68 22,11.89 22,9.68C22,5.44 17.51,2 12,2 M6.5,12C5.67,12 5,11.33 5,10.5C5,9.67 5.67,9 6.5,9C7.33,9 8,9.67 8,10.5C8,11.33 7.33,12 6.5,12 M9.5,8C8.67,8 8,7.33 8,6.5C8,5.67 8.67,5 9.5,5C10.33,5 11,5.67 11,6.5C11,7.33 10.33,8 9.5,8 M14.5,8C13.67,8 13,7.33 13,6.5C13,5.67 13.67,5 14.5,5C15.33,5 16,5.67 16,6.5C16,7.33 15.33,8 14.5,8 M17.5,12C16.67,12 16,11.33 16,10.5C16,9.67 16.67,9 17.5,9C18.33,9 19,9.67 19,10.5C19,11.33 18.33,12 17.5,12Z" />
     </vector>
     ```

  4. `app/src/main/res/drawable/ic_activity_music.xml` (音乐/律动)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3H12z" />
     </vector>
     ```

  5. `app/src/main/res/drawable/ic_activity_ball.xml` (运动/体育)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zm0,18c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z" />
     </vector>
     ```

  6. `app/src/main/res/drawable/ic_activity_game_puzzle.xml` (益智/棋牌)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FF34A853"
             android:pathData="M21,6H16.22L13,2.78A0.996,0.996 0,0 0,12 2.5a0.996,0.996 0,0 0,-1.02 0.28L7.78,6H3C1.9,6 1,6.9 1,8v11c0,1.1 0.9,2 2,2h18c1.1,0 2,-0.9 2,-2V8C23,6.9 22.1,6 21,6z M12,8a4,4 0,1 1,-4 4,4 4 0,0 1,4,-4z M12,14.5c-1.38,0 -2.5,-1.12 -2.5,-2.5s1.12,-2.5 2.5,-2.5s2.5,1.12 2.5,2.5 -1.12,2.5 -2.5,2.5z" />
     </vector>
     ```

  7. `app/src/main/res/drawable/ic_activity_craft.xml` (手工/折纸)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M9.64,7.64c0.23,-0.5 0.36,-1.05 0.36,-1.64A4,4 0,0 0,6 2,4 4 0,0 0,2 6c0,0.59 0.13,1.14 0.36,1.64L6,15.64l3.64,-8 M6,4c1.1,0 2,0.9 2,2s-0.9,2 -2,2 -2,-0.9 -2,-2 0.9,-2 2,-2z M22,6a4,4 0,0 0,-4 -4,4 4 0,0 0,-4 4,c0,0.59 0.13,1.14 0.36,1.64L18,15.64l3.64,-8c0.23,-0.5 0.36,-1.05 0.36,-1.64M18,8c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2z M12,13.5c-1.1,0 -2,0.9 -2,2s0.9,2 2,2s2,-0.9 2,-2 -0.9,-2 -2,-2z" />
     </vector>
     ```

  8. `app/src/main/res/drawable/ic_activity_animation.xml` (屏幕/电视)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M21,3H3C1.9,3 1,3.9 1,5v12c0,1.1 0.9,2 2,2h5v2h8v-2h5c1.1,0 2,-0.9 2,-2V5C23,3.9 22.1,3 21,3z M21,17H3V5h18V17z" />
     </vector>
     ```

  9. `app/src/main/res/drawable/ic_activity_study.xml` (作业/学习)：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M3,17.25V21h3.75L17.81,9.94l-3.75,-3.75L3,17.25z M20.71,7.04a0.996,0.996 0,0 0,0 -1.41l-2.34,-2.34a0.996,0.996 0,0 0,-1.41,0l-1.83,1.83 3.75,3.75 1.83,-1.83z" />
     </vector>
     ```

  10. `app/src/main/res/drawable/ic_activity_chores.xml` (整理/扫帚)：
      ```xml
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
          android:width="24dp"
          android:height="24dp"
          android:viewportWidth="24"
          android:viewportHeight="24">
          <path
              android:fillColor="#FF34A853"
              android:pathData="M19,13H5v-2h14v2z M19,9H5V7h14v2z M19,17H5v-2h14v2z" />
      </vector>
      ```

- [ ] **Step 2: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：所有新加矢量图均编译成功，未报错。

- [ ] **Step 3: 提交**

  ```bash
  git add app/src/main/res/drawable/ic_activity_*.xml
  git commit -m "feat: 导入10个儿童居家兴趣活动内置矢量图标资源"
  ```

---

### Task 5: 待办任务卡片 UI 扩展内置图标支持

**Files:**
- Modify: `app/src/main/res/layout/item_task_content.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/TaskAdapter.java`

**Interfaces:**
- Consumes: `TaskEntity.iconName`
- Produces: 任务列表卡片在包含内置图标时自动展示对应的 Drawable

- [ ] **Step 1: 在 item_task_content.xml 布局中添加 ImageView 图标容器**

  编辑 `app/src/main/res/layout/item_task_content.xml`。在第 17-20 行的 `v_quadrant_color` 彩色条下方、`LinearLayout` 文字区域上方，插入一个 `ImageView` 声明：
  ```xml
      <!-- 内置任务图标 ImageView -->
      <ImageView
          android:id="@+id/iv_task_icon"
          android:layout_width="24dp"
          android:layout_height="24dp"
          android:layout_marginEnd="8dp"
          android:scaleType="fitCenter"
          android:visibility="gone" />
  ```

- [ ] **Step 2: 修改 TaskAdapter 加载内置图标**

  编辑 `app/src/main/java/com/nearby/justnow/ui/main/TaskAdapter.java`：
  1. 在 `ViewHolder` 静态类中，新增 `ImageView ivTaskIcon;` 的缓存定义，并在构造方法中绑定：
     ```java
     ivTaskIcon = itemView.findViewById(R.id.iv_task_icon);
     ```
  2. 在 `onBindViewHolder` 方法中绑定数据时，提取任务的 `iconName` 并执行显示/隐藏：
     ```java
              if (task.iconName != null && !task.iconName.isEmpty()) {
                  int resId = holder.itemView.getContext().getResources().getIdentifier(
                      "ic_activity_" + task.iconName, "drawable", holder.itemView.getContext().getPackageName()
                  );
                  if (resId != 0) {
                      holder.ivTaskIcon.setImageResource(resId);
                      holder.ivTaskIcon.setVisibility(View.VISIBLE);
                  } else {
                      holder.ivTaskIcon.setVisibility(View.GONE);
                  }
              } else {
                  holder.ivTaskIcon.setVisibility(View.GONE);
              }
     ```

- [ ] **Step 3: 运行完整编译流程**

  运行：`./gradlew assembleDebug`
  预期：编译成功。

- [ ] **Step 4: 提交**

  ```bash
  git add app/src/main/res/layout/item_task_content.xml app/src/main/java/com/nearby/justnow/ui/main/TaskAdapter.java
  git commit -m "feat: 在任务卡片列表中支持加载并显示内置儿童兴趣图标"
  ```

---

### Task 6: 任务录入界面图标选择交互实现

**Files:**
- Modify: `app/src/main/res/layout/fragment_task_edit.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`
- Create: `app/src/main/res/layout/item_task_icon_selector.xml`

**Interfaces:**
- Consumes: `TaskInputViewModel.getIconName()` & `TaskInputViewModel.setIconName(String)`
- Produces: 任务编辑页展示平铺网格图标选项（横屏 1x10，竖屏 2x5），点击自动更新并反选

- [ ] **Step 1: 在 fragment_task_edit.xml 中增加小卡片和 RecyclerView**

  编辑 `app/src/main/res/layout/fragment_task_edit.xml`。在 `cg_existing_tags`（第 112 行附近）下方、`ll_module_title`（第 116 行附近）上方，添加如下布局：
  ```xml
          <!-- 图标选择卡片 -->
          <com.google.android.material.card.MaterialCardView
              android:id="@+id/card_icon_selector"
              android:layout_width="match_parent"
              android:layout_height="wrap_content"
              android:layout_marginTop="4dp"
              app:cardElevation="1dp"
              app:cardCornerRadius="12dp"
              app:strokeWidth="1dp"
              app:strokeColor="@color/divider">

              <LinearLayout
                  android:layout_width="match_parent"
                  android:layout_height="wrap_content"
                  android:orientation="vertical"
                  android:padding="8dp">

                  <TextView
                      android:layout_width="wrap_content"
                      android:layout_height="wrap_content"
                      android:text="选择儿童兴趣活动图标（可选）"
                      android:textAppearance="@style/TextAppearance.JustNow.Caption"
                      android:textColor="@color/text_secondary"
                      android:layout_marginBottom="6dp" />

                  <androidx.recyclerview.widget.RecyclerView
                      android:id="@+id/rv_icon_selector"
                      android:layout_width="match_parent"
                      android:layout_height="wrap_content"
                      android:overScrollMode="never" />
              </LinearLayout>
          </com.google.android.material.card.MaterialCardView>
  ```

- [ ] **Step 2: 创建图标选择面板的子项布局**

  创建新布局文件 `app/src/main/res/layout/item_task_icon_selector.xml`，用于展现单个图标按键及选中状态：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:orientation="vertical"
      android:gravity="center"
      android:padding="4dp"
      android:clickable="true"
      android:focusable="true">

      <FrameLayout
          android:id="@+id/fl_icon_bg"
          android:layout_width="40dp"
          android:layout_height="40dp"
          android:background="@drawable/bg_icon_unselected">
          
          <ImageView
              android:id="@+id/iv_icon"
              android:layout_width="24dp"
              android:layout_height="24dp"
              android:layout_gravity="center" />
      </FrameLayout>

      <TextView
          android:id="@+id/tv_icon_label"
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:layout_marginTop="2dp"
          android:textSize="10sp"
          android:textColor="@color/text_secondary"
          android:singleLine="true" />
  </LinearLayout>
  ```
  同时，在 `app/src/main/res/drawable/` 下创建背景状态图 `bg_icon_unselected.xml` (未选中灰色底，选中高亮底)：
  * 创建 `app/src/main/res/drawable/bg_icon_unselected.xml`：
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <shape xmlns:android="http://schemas.android.com/apk/res/android"
        android:shape="oval">
        <solid android:color="#F1F3F4"/>
    </shape>
    ```
  * 创建 `app/src/main/res/drawable/bg_icon_selected.xml`：
    ```xml
    <?xml version="1.0" encoding="utf-8"?>
    <shape xmlns:android="http://schemas.android.com/apk/res/android"
        android:shape="oval">
        <solid android:color="#D2E3FC"/>
        <stroke android:width="2dp" android:color="#1A73E8"/>
    </shape>
    ```

- [ ] **Step 3: 修改 TaskEditFragment.java 实现图标网格渲染及自适应**

  在 `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java` 中绑定 RecyclerView 并注册选择逻辑：
  1. 在 `onViewCreated` 结尾，增加 `setupIconSelector();` 调用。
  2. 在类底部增加 `setupIconSelector()` 及其依赖的数据和适配器类：
     ```java
         // ---- 儿童兴趣活动图标选择 ----
         private static class IconItem {
             final String name;
             final int resId;
             final String label;

             IconItem(String name, int resId, String label) {
                 this.name = name;
                 this.resId = resId;
                 this.label = label;
             }
         }

         private void setupIconSelector() {
             boolean isTablet = getResources().getBoolean(R.bool.is_tablet);
             if (!isTablet) {
                 getBinding().cardIconSelector.setVisibility(View.GONE);
                 return;
             } else {
                 getBinding().cardIconSelector.setVisibility(View.VISIBLE);
             }

             List<IconItem> icons = new ArrayList<>();
             icons.add(new IconItem("blocks", R.drawable.ic_activity_blocks, "玩具"));
             icons.add(new IconItem("book", R.drawable.ic_activity_book, "阅读"));
             icons.add(new IconItem("palette", R.drawable.ic_activity_palette, "美术"));
             icons.add(new IconItem("music", R.drawable.ic_activity_music, "音乐"));
             icons.add(new IconItem("ball", R.drawable.ic_activity_ball, "运动"));
             icons.add(new IconItem("game_puzzle", R.drawable.ic_activity_game_puzzle, "益智"));
             icons.add(new IconItem("craft", R.drawable.ic_activity_craft, "手工"));
             icons.add(new IconItem("animation", R.drawable.ic_activity_animation, "屏幕"));
             icons.add(new IconItem("study", R.drawable.ic_activity_study, "学习"));
             icons.add(new IconItem("chores", R.drawable.ic_activity_chores, "家务"));

             // 判定横竖屏以确定列数
             boolean isLandscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
             int spanCount = isLandscape ? 10 : 5; // 横屏 10 列，竖屏 5 列

             androidx.recyclerview.widget.RecyclerView rv = getBinding().rvIconSelector;
             rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), spanCount));
             rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<IconHolder>() {
                 @NonNull
                 @Override
                 public IconHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                     View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_task_icon_selector, parent, false);
                     return new IconHolder(v);
                 }

                 @Override
                 public void onBindViewHolder(@NonNull IconHolder holder, int position) {
                     IconItem item = icons.get(position);
                     holder.ivIcon.setImageResource(item.resId);
                     holder.tvLabel.setText(item.label);

                     boolean isSelected = item.name.equals(mViewModel.getIconName());
                     holder.flBg.setBackgroundResource(isSelected ? R.drawable.bg_icon_selected : R.drawable.bg_icon_unselected);

                     holder.itemView.setOnClickListener(v -> {
                         String currentSelected = mViewModel.getIconName();
                         if (item.name.equals(currentSelected)) {
                             // 反选取消
                             mViewModel.setIconName(null);
                         } else {
                             // 选中新图标
                             mViewModel.setIconName(item.name);
                         }
                         notifyDataSetChanged(); // 全局刷新状态
                     });
                 }

                 @Override
                 public int getItemCount() {
                     return icons.size();
                 }
             });
         }

         private static class IconHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
             final android.widget.FrameLayout flBg;
             final android.widget.ImageView ivIcon;
             final android.widget.TextView tvLabel;

             IconHolder(View itemView) {
                 super(itemView);
                 flBg = itemView.findViewById(R.id.fl_icon_bg);
                 ivIcon = itemView.findViewById(R.id.iv_icon);
                 tvLabel = itemView.findViewById(R.id.tv_icon_label);
             }
         }
     ```
  3. 在 `restoreState()` 方法尾部，也调用一下初始化刷新，保证编辑时能恢复正确图标：
     ```java
             if (getBinding().rvIconSelector.getAdapter() != null) {
                 getBinding().rvIconSelector.getAdapter().notifyDataSetChanged();
             }
     ```

- [ ] **Step 4: 编译打包测试**

  运行：`./gradlew assembleDebug`
  预期：全部代码无错编译通过。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/res/layout/fragment_task_edit.xml app/src/main/res/layout/item_task_icon_selector.xml app/src/main/res/drawable/bg_icon_*.xml app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java
  git commit -m "feat: 在任务编辑Fragment中支持对称网格的内置图标点选及反选"
  ```

---

### Task 7: 右上角临时内置图标预览菜单

**Files:**
- Modify: `app/src/main/res/menu/menu_main.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`
- Create: `app/src/main/res/layout/dialog_icon_preview.xml`

**Interfaces:**
- Consumes: 10 个内置矢量图图标资源
- Produces: 右上角菜单项 "查看内置图标" 触发 AlertDialog 弹出平铺预览 10 个图标

- [ ] **Step 1: 在 menu_main.xml 中添加菜单项**

  编辑 `app/src/main/res/menu/menu_main.xml`。在末尾添加：
  ```xml
      <item
          android:id="@+id/action_preview_icons"
          android:title="查看内置图标"
          app:showAsAction="never" />
  ```

- [ ] **Step 2: 创建自定义预览对话框布局**

  创建新布局文件 `app/src/main/res/layout/dialog_icon_preview.xml`，采用两行每行 5 个的平铺展示结构：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:orientation="vertical"
      android:padding="16dp"
      android:gravity="center_horizontal">

      <TextView
          android:layout_width="wrap_content"
          android:layout_height="wrap_content"
          android:text="内置儿童兴趣图标预览 (10个)"
          android:textAppearance="@style/TextAppearance.JustNow.Title"
          android:textColor="@color/text_primary"
          android:layout_marginBottom="16dp" />

      <androidx.recyclerview.widget.RecyclerView
          android:id="@+id/rv_preview"
          android:layout_width="match_parent"
          android:layout_height="wrap_content"
          android:overScrollMode="never" />

  </LinearLayout>
  ```

- [ ] **Step 3: 在 MainFragment.java 中集成菜单项响应**

  编辑 `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`：
  1. 在 `onOptionsItemSelected` 中，捕获 `action_preview_icons` 的点击：
     ```java
             if (item.getItemId() == R.id.action_preview_icons) {
                 showIconPreviewDialog();
                 return true;
             }
     ```
  2. 实现 `showIconPreviewDialog` 函数：
     ```java
         private void showIconPreviewDialog() {
             android.view.View dialogView = android.view.LayoutInflater.from(requireContext())
                 .inflate(R.layout.dialog_icon_preview, null);
             
             // 装载 10 个图标数据
             class PreviewItem {
                 final int resId;
                 final String label;
                 PreviewItem(int resId, String label) { this.resId = resId; this.label = label; }
             }
             java.util.List<PreviewItem> items = java.util.Arrays.asList(
                 new PreviewItem(R.drawable.ic_activity_blocks, "玩具"),
                 new PreviewItem(R.drawable.ic_activity_book, "阅读"),
                 new PreviewItem(R.drawable.ic_activity_palette, "美术"),
                 new PreviewItem(R.drawable.ic_activity_music, "音乐"),
                 new PreviewItem(R.drawable.ic_activity_ball, "运动"),
                 new PreviewItem(R.drawable.ic_activity_game_puzzle, "益智"),
                 new PreviewItem(R.drawable.ic_activity_craft, "手工"),
                 new PreviewItem(R.drawable.ic_activity_animation, "屏幕"),
                 new PreviewItem(R.drawable.ic_activity_study, "学习"),
                 new PreviewItem(R.drawable.ic_activity_chores, "家务")
             );

             androidx.recyclerview.widget.RecyclerView rv = dialogView.findViewById(R.id.rv_preview);
             rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(requireContext(), 5));
             rv.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
                 @androidx.annotation.NonNull
                 @Override
                 public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
                     android.view.View cell = android.view.LayoutInflater.from(parent.getContext())
                         .inflate(R.layout.item_task_icon_selector, parent, false);
                     return new androidx.recyclerview.widget.RecyclerView.ViewHolder(cell) {};
                 }

                 @Override
                 public void onBindViewHolder(@androidx.annotation.NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                     PreviewItem item = items.get(position);
                     android.widget.ImageView iv = holder.itemView.findViewById(R.id.iv_icon);
                     android.widget.TextView tv = holder.itemView.findViewById(R.id.tv_icon_label);
                     iv.setImageResource(item.resId);
                     tv.setText(item.label);
                 }

                 @Override
                 public int getItemCount() { return items.size(); }
             });

             new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                 .setView(dialogView)
                 .setPositiveButton("关闭", null)
                 .show();
         }
     ```

- [ ] **Step 4: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：全部编译无错通过。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/res/menu/menu_main.xml app/src/main/res/layout/dialog_icon_preview.xml app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
  git commit -m "feat: 在右上角菜单中增加临时的内置图标预览选项"
  ```

---

### Task 8: 内置标签自动绑定与过滤展示

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/data/dao/TagDao.java:33-37`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`
- Modify: `app/src/test/java/com/nearby/justnow/data/repository/TagRepositoryTest.java`

**Interfaces:**
- Consumes: `R.id.et_tag_name`
- Produces: 常用标签数据排除了 10 个内置标签，点击图标自动填入/清除对应标签

- [ ] **Step 1: 在 TagDao.java 的 getTopTags 中加入排除列表**

  修改 `app/src/main/java/com/nearby/justnow/data/dao/TagDao.java` 里的 `getTopTags` SQL 查询：
  ```diff
      /** 前N个标签：按使用频率降序，频率相同时按最近新增降序 */
      @Query("SELECT * FROM tags t " +
  +          "WHERE t.name NOT IN ('玩具', '阅读', '美术', '音乐', '运动', '益智', '手工', '动画', '学习', '家务') " +
             "ORDER BY (SELECT COUNT(*) FROM tasks WHERE tag_id = t.id) DESC, t.id DESC " +
             "LIMIT :limit")
      LiveData<List<TagEntity>> getTopTags(int limit);
  ```

- [ ] **Step 2: 修改 TaskEditFragment.java 点击响应以联动标签输入框**

  编辑 `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java` 里的 `setupIconSelector` 中 `holder.itemView.setOnClickListener` 逻辑：
  ```java
                     holder.itemView.setOnClickListener(v -> {
                         String currentSelected = mViewModel.getIconName();
                         if (item.name.equals(currentSelected)) {
                             mViewModel.setIconName(null);
                             // 反选时，若标签输入框的值与该图标绑定的标签一致，则将其清除
                             String currentTag = getBinding().etTagName.getText().toString().trim();
                             if (item.label.equals(currentTag)) {
                                 getBinding().etTagName.setText("");
                             }
                         } else {
                             mViewModel.setIconName(item.name);
                             // 选中时，自动填充为图标对应的内置标签名称
                             getBinding().etTagName.setText(item.label);
                         }
                         notifyDataSetChanged();
                     });
  ```

- [ ] **Step 3: 编写测试用例验证排除机制**

  在 `app/src/test/java/com/nearby/justnow/data/repository/TagRepositoryTest.java` 中，新增 `testTopTagsExcludesBuiltInTags` 测试：
  ```java
      @Test
      public void testTopTagsExcludesBuiltInTags() {
          // 插入常规标签与内置标签
          TagEntity customTag = new TagEntity();
          customTag.name = "自定义标签";
          customTag.color = 0xFF123456;
          mTagRepo.insertSync(customTag);

          TagEntity builtInTag = new TagEntity();
          builtInTag.name = "美术"; // 内置标签
          builtInTag.color = 0xFF654321;
          mTagRepo.insertSync(builtInTag);

          // 读取 Top Tags，验证是否排除了内置标签
          androidx.lifecycle.LiveData<List<TagEntity>> liveData = mTagRepo.getTopTags(10);
          // 用 LiveData 观察或直接在 Robolectric 线程上获取其值
          List<TagEntity> tags = liveData.getValue();
          if (tags == null) {
              // 观察者注册以触发 LiveData 加载
              liveData.observeForever(t -> {});
              tags = liveData.getValue();
          }

          org.junit.Assert.assertNotNull(tags);
          boolean containsBuiltIn = false;
          boolean containsCustom = false;
          for (TagEntity tag : tags) {
              if ("美术".equals(tag.name)) containsBuiltIn = true;
              if ("自定义标签".equals(tag.name)) containsCustom = true;
          }
          org.junit.Assert.assertTrue("应当包含自定义标签", containsCustom);
          org.junit.Assert.assertFalse("不应包含内置标签“美术”", containsBuiltIn);
      }
  ```

- [ ] **Step 4: 运行单元测试验证**

  运行：`./gradlew testDebugUnitTest --tests com.nearby.justnow.data.repository.TagRepositoryTest`
  预期：测试用例全部通过（PASS）。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/java/com/nearby/justnow/data/dao/TagDao.java app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java app/src/test/java/com/nearby/justnow/data/repository/TagRepositoryTest.java
  git commit -m "feat: 实现选择儿童图标自动绑定标签与常用栏过滤内置标签"
  ```

---

### Task 9: 内置图标标签多语言动态映射与翻译

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/TaskAdapter.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`
- Modify: `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`
- Create: `app/src/main/java/com/nearby/justnow/util/TagLocalizer.java`
- Create: `app/src/test/java/com/nearby/justnow/util/TagLocalizerTest.java`

**Interfaces:**
- Consumes: `R.string.tag_xxx`
- Produces: 翻译工具类 `TagLocalizer` 及 UI 渲染拦截

- [ ] **Step 1: 在 strings.xml 各多语言资源中添加翻译**

  在 `app/src/main/res/values/strings.xml` 尾部添加：
  ```xml
      <string name="tag_blocks">Play</string>
      <string name="tag_book">Read</string>
      <string name="tag_palette">Art</string>
      <string name="tag_music">Music</string>
      <string name="tag_ball">Sports</string>
      <string name="tag_game_puzzle">Puzzle</string>
      <string name="tag_craft">Craft</string>
      <string name="tag_animation">Screen</string>
      <string name="tag_study">Study</string>
      <string name="tag_chores">Chores</string>
  ```

  在 `app/src/main/res/values-zh-rCN/strings.xml` 尾部添加：
  ```xml
      <string name="tag_blocks">玩具</string>
      <string name="tag_book">阅读</string>
      <string name="tag_palette">美术</string>
      <string name="tag_music">音乐</string>
      <string name="tag_ball">运动</string>
      <string name="tag_game_puzzle">益智</string>
      <string name="tag_craft">手工</string>
      <string name="tag_animation">动画</string>
      <string name="tag_study">学习</string>
      <string name="tag_chores">家务</string>
  ```

  在 `app/src/main/res/values-zh-rHK/strings.xml` 尾部添加（繁体）：
  ```xml
      <string name="tag_blocks">玩具</string>
      <string name="tag_book">閱讀</string>
      <string name="tag_palette">美術</string>
      <string name="tag_music">音樂</string>
      <string name="tag_ball">運動</string>
      <string name="tag_game_puzzle">益智</string>
      <string name="tag_craft">手工</string>
      <string name="tag_animation">動畫</string>
      <string name="tag_study">學習</string>
      <string name="tag_chores">家務</string>
  ```

  在 `app/src/main/res/values-zh-rTW/strings.xml` 尾部添加（繁体）：
  ```xml
      <string name="tag_blocks">玩具</string>
      <string name="tag_book">閱讀</string>
      <string name="tag_palette">美術</string>
      <string name="tag_music">音樂</string>
      <string name="tag_ball">運動</string>
      <string name="tag_game_puzzle">益智</string>
      <string name="tag_craft">手工</string>
      <string name="tag_animation">動畫</string>
      <string name="tag_study">學習</string>
      <string name="tag_chores">家務</string>
  ```

- [ ] **Step 2: 创建 TagLocalizer.java 本地化翻译类**

  创建 `app/src/main/java/com/nearby/justnow/util/TagLocalizer.java`：
  ```java
  package com.nearby.justnow.util;

  import android.content.Context;
  import com.nearby.justnow.R;
  import java.util.HashMap;
  import java.util.Map;

  /**
   * 内置图标标签多语言动态映射与翻译工具类。
   * 数据库中统一存储固定的中文简称（如 "美术"）。
   * 运行时根据设备语言翻译并输出本地化文本，同时支持录入时逆向转换。
   */
  public class TagLocalizer {

      private static final Map<String, Integer> NAME_TO_RES_MAP = new HashMap<>();
      static {
          NAME_TO_RES_MAP.put("玩具", R.string.tag_blocks);
          NAME_TO_RES_MAP.put("阅读", R.string.tag_book);
          NAME_TO_RES_MAP.put("美术", R.string.tag_palette);
          NAME_TO_RES_MAP.put("音乐", R.string.tag_music);
          NAME_TO_RES_MAP.put("运动", R.string.tag_ball);
          NAME_TO_RES_MAP.put("益智", R.string.tag_game_puzzle);
          NAME_TO_RES_MAP.put("手工", R.string.tag_craft);
          NAME_TO_RES_MAP.put("动画", R.string.tag_animation);
          NAME_TO_RES_MAP.put("学习", R.string.tag_study);
          NAME_TO_RES_MAP.put("家务", R.string.tag_chores);
      }

      /**
       * 将数据库存储的内置中文标签名翻译为当前语言的本地化文本
       */
      public static String getLocalizedName(Context context, String dbTagName) {
          if (dbTagName == null) return null;
          Integer resId = NAME_TO_RES_MAP.get(dbTagName);
          if (resId != null) {
              return context.getString(resId);
          }
          return dbTagName; // 普通标签直接返回
      }

      /**
       * 将用户输入的本地化标签名（如 "Art" / "美术"）转换为数据库唯一中文键值
       */
      public static String getDbTagName(Context context, String inputTagName) {
          if (inputTagName == null || inputTagName.trim().isEmpty()) return inputTagName;
          String trimmed = inputTagName.trim();
          for (Map.Entry<String, Integer> entry : NAME_TO_RES_MAP.entrySet()) {
              String localized = context.getString(entry.getValue());
              if (localized.equalsIgnoreCase(trimmed) || entry.getKey().equalsIgnoreCase(trimmed)) {
                  return entry.getKey();
              }
          }
          return trimmed; // 自定义标签保持原样
      }
  }
  ```

- [ ] **Step 3: 修改 UI 渲染层展示翻译后的标签**

  1. 修改 `app/src/main/java/com/nearby/justnow/ui/main/TaskAdapter.java` 里的数据绑定逻辑：
     找到绑定标签文字的代码（通常是 `holder.tvTag.setText(...)`），将其用翻译包装：
     ```java
     String localizedTag = com.nearby.justnow.util.TagLocalizer.getLocalizedName(holder.itemView.getContext(), tagName);
     holder.tvTag.setText("#" + localizedTag);
     ```

  2. 修改 `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`。在过滤栏常用标签展示 of Chip 绑定或点击时：
     找到 Chip 数据绑定，将 `chip.setText(tag.name)` 替换为：
     ```java
     chip.setText(com.nearby.justnow.util.TagLocalizer.getLocalizedName(requireContext(), tag.name));
     ```

  3. 修改 `app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java`：
     - 已有标签 Chip 展示：在 `setupTagChips()` 里，创建 Chip 文本时，使用 `TagLocalizer.getLocalizedName`：
       ```java
       chip.setText(com.nearby.justnow.util.TagLocalizer.getLocalizedName(requireContext(), tag.name));
       ```
     - 点选内置图标时自动填充的文本：在 `setupIconSelector()` 选中状态分支，使用 `TagLocalizer.getLocalizedName` 填入本地化语言，不再填充固定的 item.label：
       ```java
       String locName = com.nearby.justnow.util.TagLocalizer.getLocalizedName(requireContext(), item.label);
       getBinding().etTagName.setText(locName);
       ```
     - 底部“下一步”保存到 ViewModel 时的反向转换：在 `setupBottomButton()` 里，保存标签名之前调用 `getDbTagName` 将本地化标签翻译回数据库中文主值：
       ```java
       String inputTag = getBinding().etTagName.getText().toString().trim();
       String dbTag = com.nearby.justnow.util.TagLocalizer.getDbTagName(requireContext(), inputTag);
       mViewModel.setTagName(dbTag);
       ```

- [ ] **Step 4: 编写并运行单元测试验证翻译转换机制**

  创建 `app/src/test/java/com/nearby/justnow/util/TagLocalizerTest.java` 验证中英文转换逻辑：
  ```java
  package com.nearby.justnow.util;

  import static org.junit.Assert.assertEquals;
  import android.content.Context;
  import androidx.test.core.app.ApplicationProvider;
  import org.junit.Test;
  import org.junit.runner.RunWith;
  import org.robolectric.RobolectricTestRunner;

  @RunWith(RobolectricTestRunner.class)
  public class TagLocalizerTest {
      @Test
      public void testLocalizationMapping() {
          Context context = ApplicationProvider.getApplicationContext();
          String localized = TagLocalizer.getLocalizedName(context, "美术");
          String dbName = TagLocalizer.getDbTagName(context, localized);
          assertEquals("美术", dbName);

          String custom = TagLocalizer.getLocalizedName(context, "自定义标签");
          assertEquals("自定义标签", custom);
          assertEquals("自定义标签", TagLocalizer.getDbTagName(context, "自定义标签"));
      }
  }
  ```

  运行测试：`./gradlew testDebugUnitTest --tests com.nearby.justnow.util.TagLocalizerTest`
  预期：测试通过。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/res/values*/strings.xml app/src/main/java/com/nearby/justnow/util/TagLocalizer.java app/src/test/java/com/nearby/justnow/util/TagLocalizerTest.java app/src/main/java/com/nearby/justnow/ui/main/TaskAdapter.java app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java app/src/main/java/com/nearby/justnow/ui/taskinput/TaskEditFragment.java
  git commit -m "feat: 实现儿童图标关联标签的中英文映射及运行时动态翻译"
  ```

---

### Task 10: 儿童居家兴趣图标高质感多色矢量化重构

**Files:**
- Modify: `app/src/main/res/drawable/ic_activity_*.xml`（共10个文件）

**Interfaces:**
- Consumes: XML Vector Paths
- Produces: 10 个极具拟物化与高质感的彩色矢量卡通图标

- [ ] **Step 1: 写入/替换 10 个重构后的彩色 Vector XML 文件**

  1. `app/src/main/res/drawable/ic_activity_blocks.xml`（城堡积木堆叠：红尖顶、黄圆柱、蓝绿地基）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 蓝色左地基 -->
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M4,12h6v8h-6z" />
         <!-- 绿色右地基 -->
         <path
             android:fillColor="#FF34A853"
             android:pathData="M14,12h6v8h-6z" />
         <!-- 黄色圆柱 -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M12,16m-3,0a3,3 0,1 0,6 0a3,3 0,1 0,-6 0" />
         <!-- 红色尖顶 -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M12,4L20,12L4,12Z" />
     </vector>
     ```

  2. `app/src/main/res/drawable/ic_activity_book.xml`（双页展开绘本与挂坠丝带书签）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 深色木质书皮 -->
         <path
             android:fillColor="#FF8D6E63"
             android:pathData="M2,5C2,5 6,2 12,5C18,2 22,5 22,5V19C22,19 18,16 12,19C6,16 2,19 2,19Z" />
         <!-- 左页白色 -->
         <path
             android:fillColor="#FFFFFFFF"
             android:pathData="M3,6C3,6 7,3 12,6V18C7,15 3,18 3,18Z" />
         <!-- 右页微灰 -->
         <path
             android:fillColor="#FFF1F3F4"
             android:pathData="M12,6C12,6 17,3 21,6V18C17,15 12,18 12,18Z" />
         <!-- 垂下的黄色书签丝带 -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M11.5,5h1v11l-0.5,-1l-0.5,1V5Z" />
     </vector>
     ```

  3. `app/src/main/res/drawable/ic_activity_palette.xml`（木色调色盘搭配红黄蓝绿四色颜料与跨置画笔）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 调色盘奶黄色底盘 -->
         <path
             android:fillColor="#FFFFCC80"
             android:pathData="M12,3C6.5,3 2,7.5 2,13C2,18.5 6.5,21 12,21C15.5,21 21,19 21,14C21,9.5 17.5,3 12,3Z" />
         <!-- 拿孔（深灰孔洞） -->
         <path
             android:fillColor="#FFB0BEC5"
             android:pathData="M7,14m-1.5,0a1.5,1.5 0,1 0,3 0a1.5,1.5 0,1 0,-3 0" />
         <!-- 红色颜料 -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M7,7m-1.5,0a1.5,1.5 0,1 0,3 0a1.5,1.5 0,1 0,-3 0" />
         <!-- 蓝色颜料 -->
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M12,6m-1.5,0a1.5,1.5 0,1 0,3 0a1.5,1.5 0,1 0,-3 0" />
         <!-- 绿色颜料 -->
         <path
             android:fillColor="#FF34A853"
             android:pathData="M16,9m-1.5,0a1.5,1.5 0,1 0,3 0a1.5,1.5 0,1 0,-3 0" />
         <!-- 橙色颜料 -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M16,14m-1.5,0a1.5,1.5 0,1 0,3 0a1.5,1.5 0,1 0,-3 0" />
         <!-- 画笔笔杆 -->
         <path
             android:fillColor="#FF8D6E63"
             android:pathData="M18,18L9,9L10,8L19,17Z" />
         <!-- 画笔金属扣 -->
         <path
             android:fillColor="#FFCFD8DC"
             android:pathData="M9,9L8,8L9,7L10,8Z" />
         <!-- 画笔笔刷（带红色） -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M8,8L6,6L7,5L9,7Z" />
     </vector>
     ```

  4. `app/src/main/res/drawable/ic_activity_music.xml`（双连紫色与粉紫音符）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 左侧音符头（紫色） -->
         <path
             android:fillColor="#FF7B1FA2"
             android:pathData="M8,17m-3,0a3,3 0,1 0,6 0a3,3 0,1 0,-6 0" />
         <!-- 左侧音符杆 -->
         <path
             android:fillColor="#FF9C27B0"
             android:pathData="M9.5,6V17H11.5V6Z" />
         <!-- 右侧音符头（粉紫） -->
         <path
             android:fillColor="#FFC2185B"
             android:pathData="M17,14m-3,0a3,3 0,1 0,6 0a3,3 0,1 0,-6 0" />
         <!-- 右侧音符杆 -->
         <path
             android:fillColor="#FFE91E63"
             android:pathData="M18.5,3V14H20.5V3Z" />
         <!-- 顶部音符斜梁（双层加厚梁） -->
         <path
             android:fillColor="#FF9C27B0"
             android:pathData="M11.5,6L20.5,3V5.5L11.5,8.5Z" />
     </vector>
     ```

  5. `app/src/main/res/drawable/ic_activity_ball.xml`（红黄蓝条纹相间充气皮球）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 底盘圆球（黄色） -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M12,12m-10,0a10,10 0,1 0,20 0a10,10 0,1 0,-20 0" />
         <!-- 左侧红色圆弧条纹 -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M12,2C12,2 8,7 8,12C8,17 12,22 12,22A10,10 0,0 1,12,2Z" />
         <!-- 右侧蓝色圆弧条纹 -->
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M12,2C12,2 16,7 16,12C16,17 12,22 12,22A10,10 0,0 0,12,2Z" />
         <!-- 球心白色气阀扣 -->
         <path
             android:fillColor="#FFFFFFFF"
             android:pathData="M12,12m-1.5,0a1.5,1.5 0,1 0,3 0a1.5,1.5 0,1 0,-3 0" />
     </vector>
     ```

  6. `app/src/main/res/drawable/ic_activity_game_puzzle.xml`（拼拼图：黄色和绿色拼图块互锁咬合）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 左侧绿色拼图块 -->
         <path
             android:fillColor="#FF34A853"
             android:pathData="M3,7h6v2a2,2 0,0 0,4 0V7h2v6H13a2,2 0,0 0,0,4h2v2H9v-2a2,2 0,0 0,-4 0v2H3Z" />
         <!-- 右侧黄色拼图块（咬合卡入） -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M13,7h6v5h2a1.5,1.5 0,0 1,0,3h-2v4h-6v-2a2,2 0,0 1,-4 0v2H8v-3h2a2,2 0,0 0,0,-4H8V7Z" />
     </vector>
     ```

  7. `app/src/main/res/drawable/ic_activity_craft.xml`（红黄双色卡通剪刀与蓝色纸片）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 背景蓝色纸片 -->
         <path
             android:fillColor="#FF90CAF9"
             android:pathData="M4,15 L10,20 L20,12 L14,7 Z" />
         <!-- 黄色左柄与下刃 -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M14,16c-1.5,0 -3,-1.5 -3,-3c0,-1.5 1.5,-3 3,-3c1,0 2,1 2.5,2L7,3L5.5,4.5L15,14Z" />
         <!-- 红色右柄与上刃 -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M6,16c-1.5,0 -3,-1.5 -3,-3c0,-1.5 1.5,-3 3,-3c1,0 2,1 2.5,2L18,3l1.5,1.5L10,14Z" />
         <!-- 剪刀铆钉（银色） -->
         <path
             android:fillColor="#FFCFD8DC"
             android:pathData="M11.5,9m-1,0a1,1 0,1 0,2 0a1,1 0,1 0,-2 0" />
     </vector>
     ```

  8. `app/src/main/res/drawable/ic_activity_animation.xml`（珊瑚橙色卡通电视机）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 卡通天线（灰蓝色） -->
         <path
             android:fillColor="#FF78909C"
             android:pathData="M12,6L7,2H8L12,5.5L16,2H17L12,6Z" />
         <!-- 电视橙色大外壳 -->
         <path
             android:fillColor="#FFFF8A65"
             android:pathData="M3,6C3,6 5,5 12,5C19,5 21,6 21,6V18C21,18 19,19 12,19C5,19 3,18 3,18V6Z" />
         <!-- 电视屏幕（淡青色） -->
         <path
             android:fillColor="#FFE0F2F1"
             android:pathData="M5,8H15V16H5Z" />
         <!-- 右侧控制面板区域 -->
         <path
             android:fillColor="#FF546E7A"
             android:pathData="M16,8H19V16H16Z" />
         <!-- 黄色小旋钮 -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M17.5,10m-1,0a1,1 0,1 0,2 0a1,1 0,1 0,-2 0" />
         <!-- 红色小旋钮 -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M17.5,14m-1,0a1,1 0,1 0,2 0a1,1 0,1 0,-2 0" />
     </vector>
     ```

  9. `app/src/main/res/drawable/ic_activity_study.xml`（线圈本子与红黄两色铅笔）：
     ```xml
     <vector xmlns:android="http://schemas.android.com/apk/res/android"
         android:width="24dp"
         android:height="24dp"
         android:viewportWidth="24"
         android:viewportHeight="24">
         <!-- 蓝色笔记本底皮 -->
         <path
             android:fillColor="#FF4285F4"
             android:pathData="M4,4h13v16h-13z" />
         <!-- 白色内页 -->
         <path
             android:fillColor="#FFFFFFFF"
             android:pathData="M5,3h11v16h-11z" />
         <!-- 银灰色螺旋装订环 -->
         <path
             android:fillColor="#FF90A4AE"
             android:pathData="M3,5h3v1h-3z M3,9h3v1h-3z M3,13h3v1h-3z M3,17h3v1h-3z" />
         <!-- 铅笔黄色笔杆 -->
         <path
             android:fillColor="#FFFBBC05"
             android:pathData="M19,7L13,13l2,2l6,-6z" />
         <!-- 铅笔红色笔尖 -->
         <path
             android:fillColor="#FFEA4335"
             android:pathData="M13,13l-2,2l4,0z" />
     </vector>
     ```

  10. `app/src/main/res/drawable/ic_activity_chores.xml`（扫地工具：蓝色垃圾铲、黄色扫帚和木色笔杆）：
      ```xml
      <vector xmlns:android="http://schemas.android.com/apk/res/android"
          android:width="24dp"
          android:height="24dp"
          android:viewportWidth="24"
          android:viewportHeight="24">
          <!-- 蓝色垃圾铲 -->
          <path
              android:fillColor="#FF4285F4"
              android:pathData="M6,13L16,13L18,20L4,20Z" />
          <!-- 扫帚黄色鬃毛 -->
          <path
              android:fillColor="#FFFBBC05"
              android:pathData="M12,8C12,8 10,12 8,16h8C14,12 12,8 12,8Z" />
          <!-- 扫帚木色笔杆 -->
          <path
              android:fillColor="#FF8D6E63"
              android:pathData="M11.5,2h1v6h-1z" />
      </vector>
      ```
- [ ] **Step 2: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：全部编译无错通过，彩色矢量图能够正常载入并正常展示。

- [ ] **Step 3: 提交**

  ```bash
  git add app/src/main/res/drawable/ic_activity_*.xml
  git commit -m "feat: 重构10个高质感且具有丰富拟物感的多色卡通矢量图标"
  ```

---

### Task 11: 平板模式横竖屏主页左右栏自适应比例微调

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/layout/fragment_main_page0.xml`
- Create: `app/src/main/res/values-sw600dp/dimens.xml`
- Create: `app/src/main/res/values-sw600dp-land/dimens.xml`

**Interfaces:**
- Consumes: `@dimen/main_left_panel_weight`, `@dimen/main_right_panel_weight`
- Produces: 左右侧栏按屏幕自适应微调比例

- [ ] **Step 1: 在 values/dimens.xml 中新增权重默认值**

  编辑 `app/src/main/res/values/dimens.xml`。在 `<resources>` 标签末尾添加：
  ```xml
      <item name="main_left_panel_weight" format="float" type="dimen">1.0</item>
      <item name="main_right_panel_weight" format="float" type="dimen">1.618</item>
  ```

- [ ] **Step 2: 创建 values-sw600dp/dimens.xml (平板竖屏)**

  创建 `app/src/main/res/values-sw600dp/dimens.xml`：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <item name="main_left_panel_weight" format="float" type="dimen">1.0</item>
      <item name="main_right_panel_weight" format="float" type="dimen">3.0</item>
  </resources>
  ```

- [ ] **Step 3: 创建 values-sw600dp-land/dimens.xml (平板横屏)**

  创建 `app/src/main/res/values-sw600dp-land/dimens.xml`：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <item name="main_left_panel_weight" format="float" type="dimen">1.0</item>
      <item name="main_right_panel_weight" format="float" type="dimen">4.0</item>
  </resources>
  ```

- [ ] **Step 4: 修改 fragment_main_page0.xml 使用权重资源**

  编辑 `app/src/main/res/layout/fragment_main_page0.xml`：
  1. 将第 22 行附近 `TimelineView` 的 `android:layout_weight` 修改为：
     ```xml
                 android:layout_weight="@dimen/main_left_panel_weight"
     ```
  2. 将第 62 行附近右侧面板 `LinearLayout` 的 `android:layout_weight` 修改为：
     ```xml
                 android:layout_weight="@dimen/main_right_panel_weight"
     ```

- [ ] **Step 5: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：全部编译无错通过，左右侧栏能根据旋转方向自适应正确分配宽度。

- [ ] **Step 6: 提交**

  ```bash
  git add app/src/main/res/values*/dimens.xml app/src/main/res/layout/fragment_main_page0.xml
  git commit -m "feat: 平板模式下根据屏幕横竖方向动态自适应调整左右侧栏权重比"
  ```

---

### Task 12: 平板模式横屏四列网格及占比微调

**Files:**
- Modify: `app/src/main/res/values-sw600dp-land/integers.xml`
- Modify: `app/src/main/res/values-sw600dp-land/dimens.xml`

**Interfaces:**
- Consumes: `R.integer.task_grid_span_count`, `@dimen/main_right_panel_weight`
- Produces: 平板横屏下呈现 4 列任务网格且右栏权重提升至 5.0

- [ ] **Step 1: 修改 values-sw600dp-land/integers.xml 的列数**

  编辑 `app/src/main/res/values-sw600dp-land/integers.xml`。将列数修改为 `4`：
  ```xml
      <integer name="task_grid_span_count">4</integer>
  ```

- [ ] **Step 2: 修改 values-sw600dp-land/dimens.xml 的权重**

  编辑 `app/src/main/res/values-sw600dp-land/dimens.xml`。将右侧占比权重修改为 `5.0`：
  ```xml
      <item name="main_right_panel_weight" format="float" type="dimen">5.0</item>
  ```

- [ ] **Step 3: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：编译成功，平板横屏下列数增加为 4 列，且右侧占比变宽为 83.3%。

- [ ] **Step 4: 提交**

  ```bash
  git add app/src/main/res/values-sw600dp-land/integers.xml app/src/main/res/values-sw600dp-land/dimens.xml
  git commit -m "feat: 调整平板横屏主页为4列并将右侧面板占比提升至5.0"
  ```

---

### Task 13: 任务卡片内置图标尺寸升级与行高对齐

**Files:**
- Modify: `app/src/main/res/values/dimens.xml`
- Modify: `app/src/main/res/layout/item_task_content.xml`

**Interfaces:**
- Consumes: `@dimen/task_icon_size`
- Produces: 任务卡片中的内置图标升级为 40dp 且占满两行高度空间

- [ ] **Step 1: 在 values/dimens.xml 中添加图标大小限制**

  编辑 `app/src/main/res/values/dimens.xml`。在合适位置（如任务项高度下方）添加：
  ```xml
      <!-- 任务卡片内置图标尺寸 -->
      <dimen name="task_icon_size">40dp</dimen>
  ```

- [ ] **Step 2: 修改 item_task_content.xml 调整宽高属性**

  编辑 `app/src/main/res/layout/item_task_content.xml`。将内置任务图标 `ImageView` (ID 为 `@id/iv_task_icon`) 属性修改为：
  ```xml
      <!-- 内置任务图标 ImageView -->
      <ImageView
          android:id="@+id/iv_task_icon"
          android:layout_width="@dimen/task_icon_size"
          android:layout_height="@dimen/task_icon_size"
          android:layout_marginEnd="8dp"
          android:scaleType="fitCenter"
          android:visibility="gone" />
  ```

- [ ] **Step 3: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：编译成功，任务卡片上图标增大到 40dp，与文字行高齐平。

- [ ] **Step 4: 提交**

  ```bash
  git add app/src/main/res/values/dimens.xml app/src/main/res/layout/item_task_content.xml
  git commit -m "feat: 将任务卡片内置图标宽高升级为40dp以占满两行高度空间"
  ```

---

### Task 14: 编辑任务界面布局微调（选择图标前置与横屏单行标签自适应）

**Files:**
- Modify: `app/src/main/res/layout/fragment_task_edit.xml`
- Modify: `app/src/main/res/values/bools.xml`
- Modify: `app/src/main/res/values/dimens.xml`
- Create: `app/src/main/res/values-land/bools.xml`
- Create: `app/src/main/res/values-land/dimens.xml`

**Interfaces:**
- Consumes: `@dimen/existing_tags_height`, `@bool/existing_tags_single_line`
- Produces: 调整选择图标的物理位置，并且横屏下已有常用标签折叠为单行横向排布

- [ ] **Step 1: 在默认 values 资源中添加已有常用标签的默认布局属性**

  在 `app/src/main/res/values/bools.xml` 中添加：
  ```xml
      <bool name="existing_tags_single_line">false</bool>
  ```
  在 `app/src/main/res/values/dimens.xml` 中添加：
  ```xml
      <dimen name="existing_tags_height">88dp</dimen>
  ```

- [ ] **Step 2: 创建 values-land 资源文件夹配置横屏**

  创建 `app/src/main/res/values-land/bools.xml`：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <bool name="existing_tags_single_line">true</bool>
  </resources>
  ```
  创建 `app/src/main/res/values-land/dimens.xml`：
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <dimen name="existing_tags_height">44dp</dimen>
  </resources>
  ```

- [ ] **Step 3: 调整 fragment_task_edit.xml 物理排布与属性绑定**

  编辑 `app/src/main/res/layout/fragment_task_edit.xml`：
  1. 将第 114-145 行的整个 `card_icon_selector` 控件（图标选择卡片）剪切，并粘贴到第 76 行的 `card_markdown` 卡片结束标签下方、第 79 行的 `card_tag` 标签输入卡片上方。
  2. 修改 `cg_existing_tags` 控件（ChipGroup），将其高度与 singleLine 绑定为动态资源：
     ```xml
             <com.google.android.material.chip.ChipGroup
                 android:id="@+id/cg_existing_tags"
                 android:layout_width="match_parent"
                 android:layout_height="@dimen/existing_tags_height"
                 android:layout_marginTop="8dp"
                 app:singleLine="@bool/existing_tags_single_line"
                 app:singleSelection="true"
                 app:chipSpacingHorizontal="8dp"
                 app:chipSpacingVertical="4dp" />
     ```

- [ ] **Step 4: 编译打包验证**

  运行：`./gradlew assembleDebug`
  预期：编译成功，图标选择栏成功前置于内容和标签之间，横竖屏下已有标签区域分别呈单行和双行。

- [ ] **Step 5: 提交**

  ```bash
  git add app/src/main/res/layout/fragment_task_edit.xml app/src/main/res/values*/bools.xml app/src/main/res/values*/dimens.xml
  git commit -m "feat: 任务编辑页图标选择前置并在横屏下将常用标签折叠为单行横向排布"
  ```
