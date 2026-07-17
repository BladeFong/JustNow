# 平板端时光胶囊与七朵花花瓣奖励机制实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在平板模式下，增加拍照保存成果并拼满今日花朵（游戏化七朵花）的延迟补拍照片和自适应祝贺弹窗（带自动语音朗读）机制。

**Architecture:** Room 数据库新增 `task_photos` 关联表并作 8->9 版本升降级迁移。Repository 使用 MediaStore 异步读写成果照片，并使用 ContentResolver 进行防裂图自愈清理。自定义 `FlowerCapsuleView` 通过 Canvas 极坐标实现完美的五等分点几何外切胖胖花，不闭合以消除切线。在 `MainFragment` 的右侧栏中实现横竖屏 7 朵花重载布局，并在底栏新增 Outlined 样式补拍按钮；补拍弹窗通过 RecyclerView 呈递带 40dp 纯透明卡通图标的待拍照任务项。

**Tech Stack:** Java, Room ORM, Android MediaStore, Canvas Graphics, TextToSpeech API, RecyclerView.

## Global Constraints
* **数据库版本**：Room 数据库版本从 `8` 升级为 `9`。
* **物理文件路径**：照片保存至共享存储 `/sdcard/Pictures/JustNow/` 下。
* **文件命名规范**：`IMG_JustNow_[task_english_name]_[date]_[time].jpg`。
* **音画一致**：童声语音与祝贺弹窗上的文字完全一致，不增加任何额外文字，且出现后立即自动播放。
* **花朵几何定义**：花芯半径 15.5 始终实线亮黄色填充，5个正圆外切花瓣半径 17，圆心距 29，底角共享五等分点 $A(-9.1, -12.5)$ 和 $B(9.1, -12.5)$，底部不闭合。

---

### Task 1: 数据库表新增与 Room 迁移 (Migration_8_9)

**Files:**
* Create: `app/src/main/java/com/nearby/justnow/data/entity/TaskPhotoEntity.java`
* Create: `app/src/main/java/com/nearby/justnow/data/dao/TaskPhotoDao.java`
* Modify: `app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java`
* Test: `app/src/androidTest/java/com/nearby/justnow/data/db/AppDatabaseMigrationTest.java`

**Interfaces:**
* Produces: `TaskPhotoEntity` 数据类
* Produces: `TaskPhotoDao` Room 接口

- [ ] **Step 1: 创建 TaskPhotoEntity.java**
  创建 Room 实体类，建立对任务表（tasks）的一对一/多对一关联，主键自增：
  ```java
  package com.nearby.justnow.data.entity;

  import androidx.annotation.NonNull;
  import androidx.room.ColumnInfo;
  import androidx.room.Entity;
  import androidx.room.ForeignKey;
  import androidx.room.PrimaryKey;

  @Entity(
      tableName = "task_photos",
      foreignKeys = @ForeignKey(
          entity = TaskEntity.class,
          parentColumns = "id",
          childColumns = "task_id",
          onDelete = ForeignKey.CASCADE
      )
  )
  public class TaskPhotoEntity {
      @PrimaryKey(autoGenerate = true)
      public long id;

      @ColumnInfo(name = "task_id", index = true)
      public long taskId;

      @NonNull
      @ColumnInfo(name = "photo_uri")
      public String photoUri;

      @ColumnInfo(name = "created_at")
      public long createdAt;
  }
  ```

- [ ] **Step 2: 创建 TaskPhotoDao.java**
  定义插入、查询和删除的方法：
  ```java
  package com.nearby.justnow.data.dao;

  import androidx.room.Dao;
  import androidx.room.Delete;
  import androidx.room.Insert;
  import androidx.room.Query;
  import com.nearby.justnow.data.entity.TaskPhotoEntity;
  import java.util.List;

  @Dao
  public interface TaskPhotoDao {
      @Insert
      long insert(TaskPhotoEntity entity);

      @Delete
      void delete(TaskPhotoEntity entity);

      @Query("SELECT * FROM task_photos WHERE task_id = :taskId LIMIT 1")
      TaskPhotoEntity getPhotoForTask(long taskId);

      @Query("SELECT * FROM task_photos WHERE created_at >= :startTimeMS AND created_at <= :endTimeMS")
      List<TaskPhotoEntity> getPhotosInRange(long startTimeMS, long endTimeMS);
      
      @Query("SELECT * FROM task_photos")
      List<TaskPhotoEntity> getAllPhotos();
  }
  ```

- [ ] **Step 3: 升级 AppDatabase.java 并定义 MIGRATION_8_9**
  将 `TaskPhotoEntity.class` 加入 `entities`，升级版本至 9，并添加迁移逻辑：
  ```java
  // 1. 在 Database 注解的 entities 中追加 TaskPhotoEntity.class
  // 2. 将 version 变更为 9
  // 3. 在 AppDatabase 类中声明迁移脚本：
  public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
      @Override
      public void migrate(@NonNull SupportSQLiteDatabase database) {
          database.execSQL("CREATE TABLE IF NOT EXISTS `task_photos` (" +
                  "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                  "`task_id` INTEGER NOT NULL, " +
                  "`photo_uri` TEXT NOT NULL, " +
                  "`created_at` INTEGER NOT NULL, " +
                  "FOREIGN KEY(`task_id`) REFERENCES `tasks`(`id`) ON DELETE CASCADE)");
          database.execSQL("CREATE INDEX IF NOT EXISTS `index_task_photos_task_id` ON `task_photos` (`task_id`)");
      }
  };
  // 4. 在 AppDatabase.getInstance() 的 addMigrations() 中追加 MIGRATION_8_9
  // 5. 声明抽象获取方法：
  public abstract TaskPhotoDao taskPhotoDao();
  ```

- [ ] **Step 4: 编写并运行 AppDatabaseMigrationTest 验证 Room 迁移**
  测试从 version 8 到 9 的数据迁移是否无损，新表是否正确创建：
  ```java
  // 在 app/src/androidTest/java/... 中创建测试类验证 Migration_8_9
  ```
  运行：`./gradlew connectedAndroidTest --tests com.nearby.justnow.data.db.AppDatabaseMigrationTest`
  期望：PASS

- [ ] **Step 5: 提交数据库表迁移代码**
  ```bash
  git add app/src/main/java/com/nearby/justnow/data/entity/TaskPhotoEntity.java app/src/main/java/com/nearby/justnow/data/dao/TaskPhotoDao.java app/src/main/java/com/nearby/justnow/data/db/AppDatabase.java
  git commit -m "feat: 新增 Room 数据库 task_photos 表并支持 MIGRATION_8_9 升级"
  ```

---

### Task 2: TaskPhotoRepository 与物理照片防裂图自愈机制

**Files:**
* Create: `app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java`
* Test: `app/src/test/java/com/nearby/justnow/data/repository/TaskPhotoRepositoryTest.java`

**Interfaces:**
* Produces: `TaskPhotoRepository` 类及其物理校验 API

- [ ] **Step 1: 创建 TaskPhotoRepository.java**
  实现照片的保存绑定、周完成花瓣数统计，以及异步防裂图自愈逻辑：
  ```java
  package com.nearby.justnow.data.repository;

  import android.content.ContentResolver;
  import android.content.Context;
  import android.net.Uri;
  import com.nearby.justnow.data.db.AppDatabase;
  import com.nearby.justnow.data.entity.TaskPhotoEntity;
  import java.io.InputStream;
  import java.util.List;

  public class TaskPhotoRepository extends BaseRepository {
      public TaskPhotoRepository(AppDatabase db) {
          super(db);
      }

      public long bindPhotoToTask(long taskId, String photoUri) {
          TaskPhotoEntity entity = new TaskPhotoEntity();
          entity.taskId = taskId;
          entity.photoUri = photoUri;
          entity.createdAt = System.currentTimeMillis();
          return mDb.taskPhotoDao().insert(entity);
      }

      // 获取某周点亮的花朵数与每日花瓣分布 (用于点亮7朵花)
      public List<TaskPhotoEntity> getPhotosInWeek(long mondayStartMs) {
          long sundayEndMs = mondayStartMs + (7 * 24 * 60 * 60 * 1000L) - 1;
          return mDb.taskPhotoDao().getPhotosInRange(mondayStartMs, sundayEndMs);
      }

      // 异步防裂图自愈逻辑：遍历所有关联记录，检查外部Uri是否已被物理删除
      public void verifyAndCleanupPhotos(Context context) {
          AppDatabase.execute(() -> {
              List<TaskPhotoEntity> all = mDb.taskPhotoDao().getAllPhotos();
              ContentResolver resolver = context.getContentResolver();
              for (TaskPhotoEntity entity : all) {
                  try {
                      Uri uri = Uri.parse(entity.photoUri);
                      // 尝试以只读模式打开输入流以检验物理存在性
                      InputStream is = resolver.openInputStream(uri);
                      if (is != null) {
                          is.close();
                      } else {
                          mDb.taskPhotoDao().delete(entity);
                      }
                  } catch (Exception e) {
                      // 物理文件不存在/无权限/抛出异常，执行自愈删除
                      mDb.taskPhotoDao().delete(entity);
                  }
              }
          });
      }
  }
  ```

- [ ] **Step 2: 编写测试用例并运行**
  ```java
  // 编写单元/集成测试，校验物理删除后 task_photos 表中的脏数据被清除
  ```
  运行测试验证自愈机制。
  Expected: PASS

- [ ] **Step 3: 提交 Repository 物理防裂图清理模块**
  ```bash
  git add app/src/main/java/com/nearby/justnow/data/repository/TaskPhotoRepository.java
  git commit -m "feat: 新增 TaskPhotoRepository 并加入 ContentResolver 物理文件自愈清理机制"
  ```

---

### Task 3: 几何无交叉胖胖花自定义 View (FlowerCapsuleView)

**Files:**
* Create: `app/src/main/java/com/nearby/justnow/ui/custom/FlowerCapsuleView.java`
* Create: `app/src/main/res/values/attrs.xml` (若不存在则创建)

**Interfaces:**
* Produces: `FlowerCapsuleView` 控件
* Produces: xml 属性 `flowerProgress` (0~5), `flowerBaseColor`, `flowerActiveColor`

- [ ] **Step 1: 新增 attrs.xml 属性**
  定义自定义 View 属性：
  ```xml
  <resources>
      <declare-styleable name="FlowerCapsuleView">
          <attr name="flowerProgress" format="integer" />
          <attr name="flowerBaseColor" format="color" />
          <attr name="flowerActiveColor" format="color" />
      </declare-styleable>
  </resources>
  ```

- [ ] **Step 2: 创建 FlowerCapsuleView.java**
  实现 Canvas 等分相切正圆无底部切线的胖胖花绘制：
  ```java
  package com.nearby.justnow.ui.custom;

  import android.content.Context;
  import android.content.res.TypedArray;
  import android.graphics.Canvas;
  import android.graphics.DashPathEffect;
  import android.graphics.Paint;
  import android.graphics.Path;
  import android.util.AttributeSet;
  import android.view.View;
  import androidx.annotation.Nullable;
  import com.nearby.justnow.R;

  public class FlowerCapsuleView extends View {
      private int mProgress = 0; // 0 ~ 5
      private int mBaseColor = 0xFFE91E63;
      private int mActiveColor = 0xFFFF80AB;
      private Paint mFillPaint;
      private Paint mStrokePaint;
      private Paint mDashedPaint;
      private Paint mCenterPaint;
      private Path mPetalPath;

      public FlowerCapsuleView(Context context, @Nullable AttributeSet attrs) {
          super(context, attrs);
          init(context, attrs);
      }

      private void init(Context context, AttributeSet attrs) {
          if (attrs != null) {
              TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlowerCapsuleView);
              mProgress = a.getInt(R.styleable.FlowerCapsuleView.flowerProgress, 0);
              mBaseColor = a.getColor(R.styleable.FlowerCapsuleView.flowerBaseColor, 0xFFE91E63);
              mActiveColor = a.getColor(R.styleable.FlowerCapsuleView.flowerActiveColor, 0xFFFF80AB);
              a.recycle();
          }

          mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
          mFillPaint.setStyle(Paint.Style.FILL);

          mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
          mStrokePaint.setStyle(Paint.Style.STROKE);
          mStrokePaint.setStrokeWidth(4f); // 2dp

          mDashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
          mDashedPaint.setStyle(Paint.Style.STROKE);
          mDashedPaint.setStrokeWidth(4f);
          mDashedPaint.setPathEffect(new DashPathEffect(new float[]{16f, 12f}, 0f)); // Dash 8dp, Gap 6dp (以40dp屏幕缩放)

          mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
          mCenterPaint.setStyle(Paint.Style.FILL);
          mCenterPaint.setColor(0xFFFFEB3B); // 明黄色花芯

          // 精确几何水滴形花瓣路径 (以 100x100 的 viewBox 为基准，起点(-9.1, -12.5) 到 (9.1, -12.5)，半径 17，顶部尖端在 (0, -35)，不调用 close() 去除底切线)
          mPetalPath = new Path();
          // M -9.1 -12.5
          mPetalPath.moveTo(-9.1f, -12.5f);
          // A 17 17 0 0 1 0 -35
          mPetalPath.arcTo(-17f, -35f, 17f, -12.5f, 144f, 108f, false); // 绘制饱满左侧圆弧
          // A 17 17 0 0 1 9.1 -12.5
          mPetalPath.arcTo(-17f, -35f, 17f, -12.5f, 252f, 108f, false); // 绘制饱满右侧圆弧
      }

      public void setProgress(int progress) {
          mProgress = Math.max(0, Math.min(5, progress));
          invalidate();
      }

      @Override
      protected void onDraw(Canvas canvas) {
          super.onDraw(canvas);
          int width = getWidth();
          int height = getHeight();
          float scale = Math.min(width, height) / 100f;

          canvas.save();
          canvas.translate(width / 2f, height / 2f);
          canvas.scale(scale, scale);

          // 1. 绘制 5 片旋转花瓣
          for (int i = 0; i < 5; i++) {
              canvas.save();
              canvas.rotate(i * 72f);
              if (i < mProgress) {
                  // 已收集：实心粉色填充，深色描边
                  mFillPaint.setColor(mActiveColor);
                  canvas.drawPath(mPetalPath, mFillPaint);
                  mStrokePaint.setColor(mBaseColor);
                  canvas.drawPath(mPetalPath, mStrokePaint);
              } else {
                  // 未收集：无填充，大线段虚线勾边
                  mDashedPaint.setColor(mBaseColor);
                  canvas.drawPath(mPetalPath, mDashedPaint);
              }
              canvas.restore();
          }

          // 2. 绘制始终填充的黄色实心花芯 (半径 15.5)
          mFillPaint.setColor(0xFFFFEB3B); // 明黄色
          canvas.drawCircle(0, 0, 15.5f, mFillPaint);
          mStrokePaint.setColor(mBaseColor);
          canvas.drawCircle(0, 0, 15.5f, mStrokePaint);

          canvas.restore();
      }
  }
  ```

- [ ] **Step 3: 提交自定义胖胖花 View 代码**
  ```bash
  git add app/src/main/res/values/attrs.xml app/src/main/java/com/nearby/justnow/ui/custom/FlowerCapsuleView.java
  git commit -m "feat: 实现几何精确无重合开口胖胖花 FlowerCapsuleView 自定义 View"
  ```

---

### Task 4: 主界面 7 朵花横竖屏自适应排布与补拍按钮布局集成

**Files:**
* Modify: `app/src/main/res/layout/fragment_main_page0.xml`
* Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**Interfaces:**
* Consumes: `FlowerCapsuleView`
* Produces: 7 朵花主页容器的横竖屏动态切换响应

- [ ] **Step 1: 修改 fragment_main_page0.xml**
  在右侧栏 `right_panel` 中，在列表网格下方加入包裹 7 个 `FlowerCapsuleView` 的容器，并在横贯全屏的 `bottom_period_bar` 新增 `btn_retroactive_photo`（补拍）和 `btn_add_task` 按钮：
  ```xml
  <!-- 在 right_panel 布局最下方，原本 list 之下加入: -->
  <LinearLayout
      android:id="@+id/flower_capsule_container"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:padding="8dp"
      android:orientation="horizontal" />

  <!-- 在底栏 bottom_period_bar 内新增补拍按钮 (Outlined) 并在其右侧放置添加按钮 -->
  <Button
      android:id="@+id/btn_retroactive_photo"
      style="@style/Widget.MaterialComponents.Button.OutlinedButton"
      android:layout_width="wrap_content"
      android:layout_height="wrap_content"
      android:text="📸 补拍 0"
      android:visibility="gone" />
  ```

- [ ] **Step 2: 在 MainFragment.java 中实现自适应排布逻辑**
  监听屏幕旋转和布局重载，在竖屏时将 7 朵花横向排列；横屏时将 7 朵花改为纵向排列，放在右侧栏的最右侧一列：
  ```java
  // 在 MainFragment 的 onViewCreated 中：
  // 1. 初始化 7 个 FlowerCapsuleView
  // 2. 根据 resources.configuration.orientation 执行自适应排版逻辑：
  if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
      flowerContainer.setOrientation(LinearLayout.VERTICAL);
      // 调整其在 ConstraintLayout 中的 constraints 靠右侧一列
  } else {
      flowerContainer.setOrientation(LinearLayout.HORIZONTAL);
      // 调整其在 ConstraintLayout 中的 constraints 靠底部一排
  }
  // 3. 动态从 TaskPhotoRepository 查询本周拍照历史，为 7 朵花设置进度 progress 渲染点亮
  ```

- [ ] **Step 3: 提交布局适配代码**
  ```bash
  git add app/src/main/res/layout/fragment_main_page0.xml app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
  git commit -m "feat: 实现主界面右侧栏 7 朵花横竖屏自适应加载与 Outlined 补拍按钮"
  ```

---

### Task 5: 祝贺弹窗 (CongratulationDialog) 与自动 TTS 语音朗读实现

**Files:**
* Create: `app/src/main/res/layout/dialog_congratulation.xml`
* Create: `app/src/main/java/com/nearby/justnow/ui/dialog/CongratulationDialog.java`

**Interfaces:**
* Produces: `CongratulationDialog` 弹窗

- [ ] **Step 1: 创建 dialog_congratulation.xml**
  只包含精美花朵插图，无额外语音相关视觉字符：
  ```xml
  <!-- 卡通大红花 TextView (text="🌸✨" textSize="80sp") -->
  <!-- 标题 TextView (text="“您好棒！”" textColor="@color/quadrant_color") -->
  <!-- 副标题 TextView (text="快去让爸爸妈妈帮忙，\n拍照记录成果吧！") -->
  <!-- 按钮 (暂不拍照 / 去拍照) -->
  ```

- [ ] **Step 2: 创建 CongratulationDialog.java**
  实现自动 TTS 播放朗读，且只播放正文内容。根据完成任务的象限值自适应配色：
  ```java
  package com.nearby.justnow.ui.dialog;

  import android.app.Dialog;
  import android.content.Context;
  import android.graphics.drawable.ColorDrawable;
  import android.os.Bundle;
  import android.speech.tts.TextToSpeech;
  import android.view.Window;
  import android.widget.Button;
  import androidx.annotation.NonNull;
  import com.nearby.justnow.R;
  import java.util.Locale;

  public class CongratulationDialog extends Dialog implements TextToSpeech.OnInitListener {
      private final int mQuadrantColor;
      private TextToSpeech mTTS;
      private static final String PLAY_TEXT = "您好棒！快去让爸爸妈妈帮忙，拍照记录成果吧！";

      public CongratulationDialog(@NonNull Context context, int quadrantColor) {
          super(context);
          this.mQuadrantColor = quadrantColor;
      }

      @Override
      protected void onCreate(Bundle savedInstanceState) {
          super.onCreate(savedInstanceState);
          requestWindowFeature(Window.FEATURE_NO_TITLE);
          setContentView(R.styleable.dialog_congratulation);
          getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

          // 1. 根据 mQuadrantColor 设置标题文字、主高亮按钮背景、卡片高亮色自适应
          // 2. 初始化 TTS 进行自动语音播放
          mTTS = new TextToSpeech(getContext(), this);
      }

      @Override
      public void onInit(int status) {
          if (status == TextToSpeech.SUCCESS) {
              mTTS.setLanguage(Locale.CHINESE);
              // 自动朗读正文，音画一致，无延迟立即播放
              mTTS.speak(PLAY_TEXT, TextToSpeech.QUEUE_FLUSH, null, "CongratSpeech");
          }
      }

      @Override
      public void dismiss() {
          if (mTTS != null) {
              mTTS.stop();
              mTTS.shutdown();
          }
          super.dismiss();
      }
  }
  ```

- [ ] **Step 3: 提交祝贺弹窗及自动 TTS 组件**
  ```bash
  git add app/src/main/res/layout/dialog_congratulation.xml app/src/main/java/com/nearby/justnow/ui/dialog/CongratulationDialog.java
  git commit -m "feat: 新增象限色彩自适应与自动播放音画一致TTS朗读的祝贺弹窗"
  ```

---

### Task 6: 补拍管理对话框 (RetroactivePhotoDialog) 与拍照逻辑串联

**Files:**
* Create: `app/src/main/res/layout/dialog_retroactive_photo.xml`
* Create: `app/src/main/res/layout/item_retroactive_task.xml`
* Create: `app/src/main/java/com/nearby/justnow/ui/dialog/RetroactivePhotoDialog.java`
* Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**Interfaces:**
* Produces: `RetroactivePhotoDialog` 类及其 RecyclerView 适配器

- [ ] **Step 1: 创建 item_retroactive_task.xml**
  每张卡片左侧包含任务的 40dp 卡通图标本身（ImageView，纯透明底，无背景块），在垂直方向与右侧两行文本等高对齐，无任何“象限”字样，左侧带一条 5px 的彩色边条：
  ```xml
  <!-- 卡片带 5dp left border (通过 ShapeDrawable 或者是 layout 配色) -->
  <!-- 左侧 ImageView 占位 (layout_width="40dp" layout_height="40dp" android:background="@android:color/transparent") -->
  <!-- 右侧两行文本：第一行完成时间/花瓣数，第二行标题 -->
  <!-- 右端 “📸 补拍” 按钮 -->
  ```

- [ ] **Step 2: 创建 RetroactivePhotoDialog.java**
  从数据库查询当天所有已完成但未绑定照片的任务，按时间先后顺序（正序）平铺展现在 RecyclerView 中：
  ```java
  // 1. 按时间先后顺序获取待拍照任务列表
  // 2. 绑定 RecyclerView 适配器
  // 3. 点击“补拍”按钮拉起外部 Camera intent 或调用 MediaStore 模块启动拍照
  // 4. 拍照并获取返回照片的 photoUri 后，调用 TaskPhotoRepository.bindPhotoToTask() 进行绑定并增加花瓣进度，刷新列表与主页
  ```

- [ ] **Step 3: 主界面底栏补拍按钮入口连通**
  在 `MainFragment.java` 中：
  ```java
  // 1. 在 onViewCreated 或者是刷新数据方法中，查询待补拍的数量
  // 2. 若数量 > 0，展示底栏 btn_retroactive_photo 按钮并更新其角标 (补拍 X)
  // 3. 点击 btn_retroactive_photo 时，弹出 RetroactivePhotoDialog 进行处理
  ```

- [ ] **Step 4: 提交补拍核心控制与流程闭环代码**
  ```bash
  git add app/src/main/res/layout/dialog_retroactive_photo.xml app/src/main/res/layout/item_retroactive_task.xml app/src/main/java/com/nearby/justnow/ui/dialog/RetroactivePhotoDialog.java app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java
  git commit -m "feat: 完成延迟补拍交互对话框并打通与主页底栏、相机拍照、数据库绑定和花瓣进度的完整流"
  ```

---
