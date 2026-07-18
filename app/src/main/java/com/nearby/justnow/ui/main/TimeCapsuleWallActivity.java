package com.nearby.justnow.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskPhotoWithTask;
import com.nearby.justnow.data.repository.TaskPhotoRepository;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 成果照片墙标准页面 (沉浸式 Activity 版，只读不可删除)
 */
public class TimeCapsuleWallActivity extends AppCompatActivity {

    private TaskPhotoRepository mPhotoRepository;
    private long mMondayStartMs;
    private RecyclerView mRecyclerView;
    private PhotoWallAdapter mAdapter;
    private final List<TaskPhotoWithTask> mPhotoList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(MainFragment.resolveThemeStyle(this));
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_time_capsule_wall);

        // ... 其余 onCreate 内容
        mMondayStartMs = getIntent().getLongExtra("monday_start_ms", 0L);
        mPhotoRepository = new TaskPhotoRepository(AppDatabase.getInstance(this));

        // 状态栏与标题栏颜色一致 (沉浸式风格，由当前主题 colorPrimary 决定)
        int themeColor = MainFragment.getGlobalThemeColor(this);
        Window window = getWindow();
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(themeColor); // 状态栏完美修改为主题色

            // 保持浅色文字以提供最佳对比度
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // 标题栏背景着色
        View titleBar = findViewById(R.id.layout_title_bar);
        if (titleBar != null) {
            titleBar.setBackgroundColor(themeColor);
        }

        // 3. 返回监听 (左侧白色箭头)
        View btnBack = findViewById(R.id.btn_back_wall);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        // 4. 网格列表展示
        mRecyclerView = findViewById(R.id.rv_time_capsule_wall);
        mRecyclerView.setLayoutManager(new GridLayoutManager(this, 2));

        mAdapter = new PhotoWallAdapter();
        mRecyclerView.setAdapter(mAdapter);

        loadPhotos();
    }


    private void loadPhotos() {
        AppDatabase.execute(() -> {
            List<TaskPhotoWithTask> list = mPhotoRepository.getPhotosWithTaskInWeek(mMondayStartMs);
            mPhotoList.clear();
            mPhotoList.addAll(list);
            mRecyclerView.post(() -> mAdapter.notifyDataSetChanged());
        });
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

            // 5. 设置格式化的拍照完成时间（例如：周一 10:15 AM）
            SimpleDateFormat sdf = new SimpleDateFormat("E hh:mm a", Locale.CHINESE);
            String timeStr = sdf.format(new Date(item.photo.createdAt));
            holder.tvTime.setText(timeStr);

            // 花瓣奖励说明
            // 花瓣奖励说明，根据象限加权显示
            int[] quadrantPetals = {3, 2, 2, 1};
            int petals = quadrantPetals[Math.min(item.taskQuadrant, 3)];
            holder.tvFlowerHint.setText(
                getString(R.string.s_flower_reward_hint, petals));

            // 6. 点击卡片缩略图拉起手势双击缩放全屏预览
            holder.ivThumbnail.setOnClickListener(v -> showFullScreenPhoto(item.photo.photoUri));
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

    private void showFullScreenPhoto(String photoUri) {
        Dialog detailDialog = new Dialog(this, R.style.ThemeOverlay_JustNow_FullscreenDialog);
        detailDialog.setContentView(R.layout.dialog_photo_detail);

        ImageView ivFullscreen = detailDialog.findViewById(R.id.iv_fullscreen_photo);
        TextView tvClose = detailDialog.findViewById(R.id.tv_detail_close);

        AppDatabase.execute(() -> {
            try {
                Uri uri = Uri.parse(photoUri);
                Bitmap bitmap = decodeUriToBitmap(this, uri, 1200, 1200);
                if (bitmap != null) {
                    ivFullscreen.post(() -> ivFullscreen.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
        });

        setupZoomableImageView(ivFullscreen);
        tvClose.setOnClickListener(v -> detailDialog.dismiss());
        detailDialog.show();
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
}
