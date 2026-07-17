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
import android.widget.Toast;

import androidx.annotation.NonNull;
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
 * 时光胶囊周照片回顾墙弹窗
 */
public class TimeCapsuleWallDialog extends Dialog {

    private final TaskPhotoRepository mPhotoRepository;
    private final long mMondayStartMs;
    private RecyclerView mRecyclerView;
    private PhotoWallAdapter mAdapter;
    private final List<TaskPhotoWithTask> mPhotoList = new ArrayList<>();

    public TimeCapsuleWallDialog(@NonNull Context context, long mondayStartMs) {
        super(context);
        mPhotoRepository = new TaskPhotoRepository(AppDatabase.getInstance(context));
        mMondayStartMs = mondayStartMs;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_time_capsule_wall);

        // 设置全屏弹窗宽高
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(lp);
        }

        mRecyclerView = findViewById(R.id.rv_time_capsule_wall);
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2)); // 两列网格

        mAdapter = new PhotoWallAdapter();
        mRecyclerView.setAdapter(mAdapter);

        findViewById(R.id.btn_close_wall).setOnClickListener(v -> dismiss());

        loadPhotos();
    }

    private void loadPhotos() {
        AppDatabase.execute(() -> {
            List<TaskPhotoWithTask> list = mPhotoRepository.getPhotosWithTaskInWeek(mMondayStartMs);
            mPhotoList.clear();
            mPhotoList.addAll(list);
            // 切回主线程刷新界面
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
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_time_capsule_card, parent, false);
            return new PhotoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
            TaskPhotoWithTask item = mPhotoList.get(position);

            // 1. 设置四象限主题色竖条
            int accentColor;
            switch (item.taskQuadrant) {
                case 0:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_important);
                    break;
                case 1:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_not_important);
                    break;
                case 2:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_important);
                    break;
                case 3:
                default:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_not_important);
                    break;
            }
            holder.vQuadrantBar.setBackgroundColor(accentColor);

            // 2. 异步加载缩略图以防止大图OOM
            holder.ivThumbnail.setImageDrawable(null);
            AppDatabase.execute(() -> {
                try {
                    Uri uri = Uri.parse(item.photo.photoUri);
                    Bitmap bitmap = decodeUriToBitmap(getContext(), uri, 160, 160);
                    if (bitmap != null) {
                        holder.ivThumbnail.post(() -> holder.ivThumbnail.setImageBitmap(bitmap));
                    }
                } catch (Exception ignored) {}
            });

            // 3. 设置任务标题
            holder.tvTitle.setText(item.taskContent);

            // 4. 设置透明卡通图标本身 (40dp x 40dp，去除背景框)
            holder.ivIcon.setImageDrawable(null);
            if (item.taskIconName != null && !item.taskIconName.isEmpty()) {
                String resName = "ic_activity_" + item.taskIconName;
                int resId = getContext().getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
                if (resId != 0) {
                    holder.ivIcon.setImageResource(resId);
                }
            }

            // 5. 设置格式化的拍照完成时间（例如：周一 10:15 AM）
            SimpleDateFormat sdf = new SimpleDateFormat("E hh:mm a", Locale.CHINESE);
            String timeStr = sdf.format(new Date(item.photo.createdAt));
            holder.tvTime.setText(timeStr);

            // 🌸花瓣奖励说明
            holder.tvFlowerHint.setText("🌸 本成果已贡献 3 片花瓣");

            // 6. 点击卡片照片缩略图拉起手势双击缩放全屏大图预览
            holder.ivThumbnail.setOnClickListener(v -> showFullScreenPhoto(item.photo.photoUri));

            // 7. 点击右上角小红叉执行防裂图物理及数据库删除
            holder.ivDelete.setOnClickListener(v -> {
                AppDatabase.execute(() -> {
                    mPhotoRepository.deletePhoto(item.photo);
                    mRecyclerView.post(() -> {
                        Toast.makeText(getContext(), "已成功删除该照片关联", Toast.LENGTH_SHORT).show();
                        loadPhotos();
                    });
                });
            });
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
        ImageView ivDelete;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            vQuadrantBar = itemView.findViewById(R.id.v_quadrant_bar);
            ivThumbnail = itemView.findViewById(R.id.iv_photo_thumbnail);
            ivIcon = itemView.findViewById(R.id.iv_task_icon);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvTime = itemView.findViewById(R.id.tv_photo_time);
            tvFlowerHint = itemView.findViewById(R.id.tv_flower_reward_hint);
            ivDelete = itemView.findViewById(R.id.iv_delete_photo);
        }
    }

    /**
     * 高效安全的Uri采样率解码，从ContentProvider加载图片防OOM
     */
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

    /**
     * 弹出全屏大图预览并注册手势双击缩放和拖拽
     */
    private void showFullScreenPhoto(String photoUri) {
        Dialog detailDialog = new Dialog(getContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        detailDialog.setContentView(R.layout.dialog_photo_detail);

        ImageView ivFullscreen = detailDialog.findViewById(R.id.iv_fullscreen_photo);
        TextView tvClose = detailDialog.findViewById(R.id.tv_detail_close);

        // 异步解码大图
        AppDatabase.execute(() -> {
            try {
                Uri uri = Uri.parse(photoUri);
                // 屏幕尺寸适配，限制在 1200x1200 以内解码，防超高分辨率崩溃
                Bitmap bitmap = decodeUriToBitmap(getContext(), uri, 1200, 1200);
                if (bitmap != null) {
                    ivFullscreen.post(() -> ivFullscreen.setImageBitmap(bitmap));
                }
            } catch (Exception ignored) {}
        });

        // 注册手势缩放支持
        setupZoomableImageView(ivFullscreen);

        tvClose.setOnClickListener(v -> detailDialog.dismiss());
        detailDialog.show();
    }

    /**
     * 自定义极简双击手势+双指缩放拖拽功能实现，防闪退且零依赖
     */
    private void setupZoomableImageView(final ImageView imageView) {
        imageView.setOnTouchListener(new View.OnTouchListener() {
            private float mScaleFactor = 1.0f;
            private float mFocusX = 0f;
            private float mFocusY = 0f;

            // 监听缩放
            private final ScaleGestureDetector mScaleDetector = new ScaleGestureDetector(getContext(), 
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        mScaleFactor *= detector.getScaleFactor();
                        // 缩放范围限制在 1.0 ~ 4.0 倍
                        mScaleFactor = Math.max(1.0f, Math.min(mScaleFactor, 4.0f));
                        imageView.setScaleX(mScaleFactor);
                        imageView.setScaleY(mScaleFactor);
                        return true;
                    }
                });

            // 监听双击
            private final GestureDetector mGestureDetector = new GestureDetector(getContext(), 
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (mScaleFactor > 1.0f) {
                            // 大于 1.0，双击恢复原状
                            mScaleFactor = 1.0f;
                        } else {
                            // 1.0 倍，双击放大至 2.0 倍
                            mScaleFactor = 2.0f;
                        }
                        imageView.setScaleX(mScaleFactor);
                        imageView.setScaleY(mScaleFactor);
                        // 恢复平移
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

                // 只有放大状态下，才支持拖拽平移
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
