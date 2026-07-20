package com.nearby.justnow.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskPhotoEntity;
import com.nearby.justnow.data.entity.TaskPhotoWithTask;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.repository.TaskPhotoRepository;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.ui.trendchart.PetalTrendChartView;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 成果照片墙标准页面 (沉浸式 Activity 版，只读不可删除)
 */
public class TimeCapsuleWallActivity extends AppCompatActivity {

    /** 花瓣加权：与 RewardBarFragment.QUADRANT_PETALS 保持一致 */
    private static final int[] QUADRANT_PETALS = {3, 2, 2, 1};

    private static final int PERIOD_WEEK = 0;
    private static final int PERIOD_MONTH = 1;
    private static final int PERIOD_SUMMER = 2;
    private static final int PERIOD_WINTER = 3;

    private TaskPhotoRepository mPhotoRepository;
    private TimePeriodRepository mPeriodRepo;
    private long mMondayStartMs;
    private RecyclerView mRecyclerView;
    private PhotoWallAdapter mAdapter;
    private final List<TaskPhotoWithTask> mPhotoList = new ArrayList<>();

    private TextView mTvPetalTotal;
    private TextView mBtnPeriodSwitcher;
    private PetalTrendChartView mPetalTrendChart;

    private int mCurrentPeriod = PERIOD_WEEK;
    private long mRangeStartMs;
    private long mRangeEndMs;
    private String mSummerReviewKey;
    private String mWinterReviewKey;
    private String mSummerStartMd;
    private String mSummerEndMd;
    private String mWinterStartMd;
    private String mWinterEndMd;
    private boolean mSummerAvailable;
    private boolean mWinterAvailable;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(MainFragment.resolveThemeStyle(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_capsule_wall);

        mMondayStartMs = getIntent().getLongExtra("monday_start_ms", 0L);
        long currentUserId = ((com.nearby.justnow.JustNowApplication) getApplication())
            .getCurrentUserId();
        AppDatabase db = AppDatabase.getInstance(this, currentUserId);
        mPhotoRepository = new TaskPhotoRepository(db);
        mPeriodRepo = new TimePeriodRepository(db);

        // 状态栏与标题栏颜色一致
        int themeColor = MainFragment.getGlobalThemeColor(this);
        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(themeColor);
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // 标题栏背景着色
        View titleBar = findViewById(R.id.layout_title_bar);
        if (titleBar != null) {
            titleBar.setBackgroundColor(themeColor);
        }

        // 返回监听
        View btnBack = findViewById(R.id.btn_back_wall);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // 网格列表：竖屏2列，横屏3列
        mRecyclerView = findViewById(R.id.rv_time_capsule_wall);
        int orientation = getResources().getConfiguration().orientation;
        int spanCount = (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ? 3 : 2;
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, spanCount));

        mAdapter = new PhotoWallAdapter();
        mRecyclerView.setAdapter(mAdapter);

        // 花瓣统计 + 周期切换
        mTvPetalTotal = findViewById(R.id.tv_petal_total);
        mBtnPeriodSwitcher = findViewById(R.id.btn_period_switcher);

        // 趋势图：竖屏可见，横屏隐藏，颜色跟随主题
        mPetalTrendChart = findViewById(R.id.petal_trend_chart);
        if (mPetalTrendChart != null) {
            mPetalTrendChart.setColor(themeColor);
            mPetalTrendChart.setVisibility(
                orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    ? View.GONE : View.VISIBLE);
        }

        // 加载暑假/寒假组状态后再初始化
        AppDatabase.execute(() -> {
            loadVacationGroupState();
            runOnUiThread(() -> initPeriodSwitcher());
        });
    }

    /** 加载暑假/寒假组状态，判断是否在下拉菜单中显示 */
    private void loadVacationGroupState() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        mSummerReviewKey = "summer-" + year;
        mWinterReviewKey = "winter-" + year + "-" + (year + 1);

        TimePeriodGroupEntity summer = mPeriodRepo.getGroupSync(PeriodGroupType.SUMMER_VACATION);
        TimePeriodGroupEntity winter = mPeriodRepo.getGroupSync(PeriodGroupType.WINTER_VACATION);

        mSummerAvailable = summer != null && summer.enabled
            && mSummerReviewKey.equals(summer.lastReviewedKey);
        mWinterAvailable = winter != null && winter.enabled
            && mWinterReviewKey.equals(winter.lastReviewedKey);

        if (mSummerAvailable) {
            mSummerStartMd = summer.startMonthDay;
            mSummerEndMd = summer.endMonthDay;
        }
        if (mWinterAvailable) {
            mWinterStartMd = winter.startMonthDay;
            mWinterEndMd = winter.endMonthDay;
        }
    }

    /** 初始化周期切换下拉菜单 */
    private void initPeriodSwitcher() {
        updatePeriodRange();
        mBtnPeriodSwitcher.setOnClickListener(v -> showPeriodMenu());
        loadPhotos();
    }

    /** 弹出周期切换下拉菜单 */
    private void showPeriodMenu() {
        PopupMenu popup = new PopupMenu(this, mBtnPeriodSwitcher);
        popup.getMenu().add(0, PERIOD_WEEK, 0, getString(R.string.s_period_this_week));
        popup.getMenu().add(0, PERIOD_MONTH, 1, getString(R.string.s_period_this_month));
        if (mSummerAvailable) {
            popup.getMenu().add(0, PERIOD_SUMMER, 2, getString(R.string.s_period_summer_vacation));
        }
        if (mWinterAvailable) {
            popup.getMenu().add(0, PERIOD_WINTER, 3, getString(R.string.s_period_winter_vacation));
        }
        popup.setOnMenuItemClickListener(item -> {
            mCurrentPeriod = item.getItemId();
            updatePeriodRange();
            loadPhotos();
            return true;
        });
        popup.show();
    }

    /** 根据当前选中周期计算时间范围和下拉按钮文字 */
    private void updatePeriodRange() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        String label;
        switch (mCurrentPeriod) {
            case PERIOD_MONTH:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                mRangeStartMs = cal.getTimeInMillis();
                cal.add(Calendar.MONTH, 1);
                mRangeEndMs = cal.getTimeInMillis() - 1;
                label = getString(R.string.s_period_this_month);
                break;
            case PERIOD_SUMMER:
                mRangeStartMs = monthDayToMs(mSummerStartMd, cal, false);
                mRangeEndMs = monthDayToMs(mSummerEndMd, cal, true);
                label = getString(R.string.s_period_summer_vacation);
                break;
            case PERIOD_WINTER:
                mRangeStartMs = monthDayToMs(mWinterStartMd, cal, false);
                mRangeEndMs = monthDayToMs(mWinterEndMd, cal, true);
                label = getString(R.string.s_period_winter_vacation);
                break;
            case PERIOD_WEEK:
            default:
                cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                mRangeStartMs = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_MONTH, 7);
                mRangeEndMs = cal.getTimeInMillis() - 1;
                label = getString(R.string.s_period_this_week);
                break;
        }
        mBtnPeriodSwitcher.setText("▼ " + label);
    }

    /** 将 MM-dd 格式日期转为当年时间戳 */
    private long monthDayToMs(String monthDay, Calendar cal, boolean endOfDay) {
        if (monthDay == null || monthDay.length() < 5) return 0;
        try {
            String[] parts = monthDay.split("-");
            int month = Integer.parseInt(parts[0]) - 1;
            int day = Integer.parseInt(parts[1]);
            Calendar c = (Calendar) cal.clone();
            c.set(Calendar.MONTH, month);
            c.set(Calendar.DAY_OF_MONTH, day);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (endOfDay) {
                c.set(Calendar.HOUR_OF_DAY, 23);
                c.set(Calendar.MINUTE, 59);
                c.set(Calendar.SECOND, 59);
                c.set(Calendar.MILLISECOND, 999);
            }
            return c.getTimeInMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 加载照片并更新统计 */
    private void loadPhotos() {
        AppDatabase.execute(() -> {
            List<TaskPhotoWithTask> list = mPhotoRepository.getFirstPhotoPerTaskInRange(
                mRangeStartMs, mRangeEndMs);
            int totalPetals = 0;
            for (TaskPhotoWithTask p : list) {
                totalPetals += QUADRANT_PETALS[Math.min(p.taskQuadrant, 3)];
            }
            final int petalCount = totalPetals;

            // 趋势图数据
            final List<String> trendLabels = new ArrayList<>();
            final List<Integer> trendValues = new ArrayList<>();
            computeTrendData(list, trendLabels, trendValues);

            mPhotoList.clear();
            mPhotoList.addAll(list);
            mRecyclerView.post(() -> {
                mAdapter.notifyDataSetChanged();
                mTvPetalTotal.setText(getString(R.string.s_petal_total, petalCount));
                if (mPetalTrendChart != null) {
                    mPetalTrendChart.setData(trendLabels, trendValues);
                }
            });
        });
    }

    /** 计算趋势图数据：按时间段分组累加花瓣数 */
    private void computeTrendData(List<TaskPhotoWithTask> photos,
                                  List<String> outLabels, List<Integer> outValues) {
        Calendar cal = Calendar.getInstance();
        switch (mCurrentPeriod) {
            case PERIOD_MONTH:
                computeMonthlyTrend(cal, outLabels, outValues);
                break;
            case PERIOD_SUMMER:
            case PERIOD_WINTER:
                computeVacationWeekTrend(outLabels, outValues);
                break;
            case PERIOD_WEEK:
            default:
                computeWeeklyTrend(cal, outLabels, outValues);
                break;
        }
    }

    /** 按周分组的趋势（最多 10 周，从有数据的周开始） */
    private void computeWeeklyTrend(Calendar cal, List<String> outLabels,
                                    List<Integer> outValues) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int w = 9; w >= 0; w--) {
            Calendar weekStart = (Calendar) c.clone();
            weekStart.add(Calendar.DAY_OF_MONTH, -w * 7);
            long ws = weekStart.getTimeInMillis();
            Calendar weekEnd = (Calendar) weekStart.clone();
            weekEnd.add(Calendar.DAY_OF_MONTH, 7);
            long we = weekEnd.getTimeInMillis() - 1;

            int petals = countPetalsInRange(ws, we);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "M/d", java.util.Locale.US);
            labels.add(sdf.format(new Date(ws)));
            values.add(petals);
        }
        trimLeadingZeros(labels, values, outLabels, outValues);
    }

    /** 按月分组的趋势（最多 10 个月，从有数据的月开始） */
    private void computeMonthlyTrend(Calendar cal, List<String> outLabels,
                                     List<Integer> outValues) {
        Calendar c = (Calendar) cal.clone();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DAY_OF_MONTH, 1);

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int m = 9; m >= 0; m--) {
            Calendar monthStart = (Calendar) c.clone();
            monthStart.add(Calendar.MONTH, -m);
            long ms = monthStart.getTimeInMillis();
            Calendar monthEnd = (Calendar) monthStart.clone();
            monthEnd.add(Calendar.MONTH, 1);
            long me = monthEnd.getTimeInMillis() - 1;

            int petals = countPetalsInRange(ms, me);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                "M月", java.util.Locale.CHINESE);
            labels.add(sdf.format(new Date(ms)));
            values.add(petals);
        }
        trimLeadingZeros(labels, values, outLabels, outValues);
    }

    /** 裁掉前导零：从第一个非零数据点开始 */
    private void trimLeadingZeros(List<String> labels, List<Integer> values,
                                  List<String> outLabels, List<Integer> outValues) {
        int start = 0;
        while (start < values.size() - 1 && values.get(start) == 0) {
            start++;
        }
        for (int i = start; i < values.size(); i++) {
            outLabels.add(labels.get(i));
            outValues.add(values.get(i));
        }
    }

    /** 寒暑假按周分组的趋势（仅假期范围内的周，按实际数量，裁掉前导零） */
    private void computeVacationWeekTrend(List<String> outLabels,
                                          List<Integer> outValues) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(mRangeStartMs);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        long vacationEnd = mRangeEndMs;

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        int weekIndex = 1;
        while (c.getTimeInMillis() <= vacationEnd) {
            long ws = c.getTimeInMillis();
            c.add(Calendar.DAY_OF_MONTH, 7);
            long we = Math.min(c.getTimeInMillis() - 1, vacationEnd);

            int petals = countPetalsInRange(ws, we);
            labels.add("W" + weekIndex);
            values.add(petals);
            weekIndex++;
        }
        trimLeadingZeros(labels, values, outLabels, outValues);
    }

    /** 查询指定时间范围内的照片并累加花瓣数（每任务只计首张） */
    private int countPetalsInRange(long startMs, long endMs) {
        List<TaskPhotoWithTask> photos = mPhotoRepository.getFirstPhotoPerTaskInRange(startMs, endMs);
        int total = 0;
        for (TaskPhotoWithTask p : photos) {
            total += QUADRANT_PETALS[Math.min(p.taskQuadrant, 3)];
        }
        return total;
    }

    /**
     * 照片墙网格适配器
     */
    private class PhotoWallAdapter extends RecyclerView.Adapter<PhotoViewHolder> {

        @NonNull
        @Override
        public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(TimeCapsuleWallActivity.this)
                    .inflate(R.layout.item_time_capsule_card, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            TaskPhotoWithTask item = mPhotoList.get(position);

            // 1. 设置四象限主题色竖条
            int accentColor;
            switch (item.taskQuadrant) {
                case 0:
                    accentColor = ContextCompat.getColor(TimeCapsuleWallActivity.this, R.color.quadrant_urgent_important);
                    break;
                case 1:
                    accentColor = ContextCompat.getColor(TimeCapsuleWallActivity.this, R.color.quadrant_urgent_not_important);
                    break;
                case 2:
                    accentColor = ContextCompat.getColor(TimeCapsuleWallActivity.this, R.color.quadrant_not_urgent_important);
                    break;
                case 3:
                default:
                    accentColor = ContextCompat.getColor(TimeCapsuleWallActivity.this, R.color.quadrant_not_urgent_not_important);
                    break;
            }
            holder.vQuadrantBar.setBackgroundColor(accentColor);

            // 2. 异步加载缩略图
            holder.ivThumbnail.setImageDrawable(null);
            AppDatabase.execute(() -> {
                try {
                    Uri uri = Uri.parse(item.photo.photoUri);
                    Bitmap bitmap = decodeUriToBitmap(TimeCapsuleWallActivity.this, uri, 160, 160);
                    if (bitmap != null) {
                        holder.ivThumbnail.post(() -> holder.ivThumbnail.setImageBitmap(bitmap));
                    }
                } catch (Exception ignored) {}
            });

            // 3. 设置任务标题
            holder.tvTitle.setText(item.taskContent);

            // 4. 设置透明活动图标
            holder.ivIcon.setImageDrawable(null);
            if (item.taskIconName != null && !item.taskIconName.isEmpty()) {
                String resName = "ic_activity_" + item.taskIconName;
                int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
                if (resId != 0) {
                    holder.ivIcon.setImageResource(resId);
                }
            }

            // 5. 设置格式化的拍照完成时间
            SimpleDateFormat sdf = new SimpleDateFormat("E hh:mm a", Locale.CHINESE);
            String timeStr = sdf.format(new Date(item.photo.createdAt));
            holder.tvTime.setText(timeStr);

            // 6. 花瓣奖励说明
            int petals = QUADRANT_PETALS[Math.min(item.taskQuadrant, 3)];
            holder.tvFlowerHint.setText(
                getString(R.string.s_flower_reward_hint, petals));

            // 7. 点击卡片缩略图进入全屏左右划动浏览
            final long clickPhotoId = item.photo.id;
            final long clickTaskId = item.photo.taskId;
            holder.ivThumbnail.setOnClickListener(v ->
                showFullScreenPhotosByTaskId(clickTaskId, clickPhotoId));
        }

        @Override
        public int getItemCount() {
            return mPhotoList.size();
        }
    }

    private static class PhotoViewHolder extends RecyclerView.ViewHolder {
        View vQuadrantBar;
        ImageView ivThumbnail;
        ImageView ivIcon;
        TextView tvTitle;
        TextView tvTime;
        TextView tvFlowerHint;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            vQuadrantBar = itemView.findViewById(R.id.v_quadrant_bar);
            ivThumbnail = itemView.findViewById(R.id.iv_photo_thumbnail);
            ivIcon = itemView.findViewById(R.id.iv_task_icon);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvTime = itemView.findViewById(R.id.tv_photo_time);
            tvFlowerHint = itemView.findViewById(R.id.tv_flower_reward_hint);
        }
    }

    private static Bitmap decodeUriToBitmap(Context context, Uri uri, int maxW, int maxH) {
        try {
            InputStream in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, options);
            in.close();

            int scale = 1;
            while ((options.outWidth / scale / 2 >= maxW) && (options.outHeight / scale / 2 >= maxH)) {
                scale *= 2;
            }

            BitmapFactory.Options outOptions = new BitmapFactory.Options();
            outOptions.inSampleSize = scale;
            in = context.getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(in, null, outOptions);
            in.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    /** 全屏浏览某任务所有照片，支持左右划动 */
    private void showFullScreenPhotosByTaskId(long taskId, long startPhotoId) {
        AppDatabase.execute(() -> {
            List<TaskPhotoEntity> photos = mPhotoRepository.getPhotosForTask(taskId);
            if (photos.isEmpty()) return;

            // 找到 startPhotoId 在列表中的索引
            int idx = 0;
            for (int i = 0; i < photos.size(); i++) {
                if (photos.get(i).id == startPhotoId) {
                    idx = i;
                    break;
                }
            }

            final int startIndex = idx;
            runOnUiThread(() -> {
                Dialog detailDialog = new Dialog(
                    TimeCapsuleWallActivity.this, R.style.ThemeOverlay_JustNow_FullscreenDialog);
                detailDialog.setContentView(R.layout.dialog_photo_detail);

                ViewPager2 viewPager = detailDialog.findViewById(R.id.vp_fullscreen_photos);
                TextView tvClose = detailDialog.findViewById(R.id.tv_detail_close);

                PhotoPagerAdapter adapter = new PhotoPagerAdapter(photos);
                viewPager.setAdapter(adapter);
                viewPager.setCurrentItem(startIndex, false);

                tvClose.setOnClickListener(v -> detailDialog.dismiss());
                detailDialog.show();
            });
        });
    }

    private void setupZoomableImageView(final ImageView imageView) {
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private float mScaleFactor = 1.0f;
            private final ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(TimeCapsuleWallActivity.this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        mScaleFactor *= detector.getScaleFactor();
                        mScaleFactor = Math.max(1.0f, Math.min(mScaleFactor, 4.0f));
                        imageView.setScaleX(mScaleFactor);
                        imageView.setScaleY(mScaleFactor);
                        return true;
                    }
                });

            private final GestureDetector mGestureDetector = new GestureDetector(TimeCapsuleWallActivity.this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (mScaleFactor > 1.0f) {
                            mScaleFactor = 1.0f;
                        } else {
                            mScaleFactor = 2.0f;
                        }
                        imageView.setScaleX(mScaleFactor);
                        imageView.setScaleY(mScaleFactor);
                        imageView.setTranslationX(0f);
                        imageView.setTranslationY(0f);
                        return true;
                    }
                });

            private float mLastTouchX;
            private float mLastTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                mGestureDetector.onTouchEvent(event);

                if (mScaleFactor > 1.0f) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            mLastTouchX = event.getX();
                            mLastTouchY = event.getY();
                            break;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getX() - mLastTouchX;
                            float dy = event.getY() - mLastTouchY;
                            imageView.setTranslationX(imageView.getTranslationX() + dx);
                            imageView.setTranslationY(imageView.getTranslationY() + dy);
                            mLastTouchX = event.getX();
                            mLastTouchY = event.getY();
                            break;
                    }
                }
                return true;
            }
        });
    }

    /**
     * ViewPager2 适配器，每页显示一张全屏可缩放照片
     */
    private class PhotoPagerAdapter extends RecyclerView.Adapter<PhotoPagerAdapter.PhotoPageViewHolder> {

        private final List<TaskPhotoEntity> mPhotos;

        PhotoPagerAdapter(List<TaskPhotoEntity> photos) {
            mPhotos = photos;
        }

        @NonNull
        @Override
        public PhotoPageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(TimeCapsuleWallActivity.this);
            iv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            return new PhotoPageViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoPageViewHolder holder, int position) {
            String uri = mPhotos.get(position).photoUri;
            AppDatabase.execute(() -> {
                try {
                    Bitmap bitmap = decodeUriToBitmap(TimeCapsuleWallActivity.this,
                        Uri.parse(uri), 1200, 1200);
                    if (bitmap != null) {
                        holder.mImageView.post(() -> holder.mImageView.setImageBitmap(bitmap));
                    }
                } catch (Exception ignored) {}
            });
            setupZoomableImageView(holder.mImageView);
        }

        @Override
        public int getItemCount() {
            return mPhotos.size();
        }

        class PhotoPageViewHolder extends RecyclerView.ViewHolder {
            ImageView mImageView;
            PhotoPageViewHolder(@NonNull View itemView) {
                super(itemView);
                mImageView = (ImageView) itemView;
            }
        }
    }
}
