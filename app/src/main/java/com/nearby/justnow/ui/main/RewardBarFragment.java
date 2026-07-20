package com.nearby.justnow.ui.main;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskPhotoEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;
import com.nearby.justnow.databinding.FragmentRewardBarBinding;
import com.nearby.justnow.ui.custom.FlowerCapsuleView;

import java.io.File;
import java.util.Calendar;
import java.util.List;

/**
 * 时光胶囊七朵花收集栏 + 补拍照片按钮。
 * 由 MainPage0Fragment 承载，作为其 child fragment 自动管理。
 */
public class RewardBarFragment extends Fragment {

    private FragmentRewardBarBinding mBinding;
    private final FlowerCapsuleView[] mFlowerViews = new FlowerCapsuleView[7];
    private MaterialButton mBtnRetroactivePhoto;
    private LinearLayout mFlowerCapsuleContainer;
    private CongratulationsDialog mCongratsDialog;
    private com.nearby.justnow.ui.dialog.CongratulationDialog mCongratulationDialog;
    private TaskPhotoRepository mPhotoRepository;
    private long mPendingPhotoTaskId = -1;
    private Uri mPendingPhotoUri;
    private static final int REQUEST_CODE_CAPTURE_PHOTO = 9988;
    private boolean mHasPromptedRetroactiveOnStart = false;
    private boolean mHasCongratulatedThisWeek = false;
    private boolean mIsFirstWeeklyFlowersRefresh = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mBinding = FragmentRewardBarBinding.inflate(inflater, container, false);
        return mBinding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (savedInstanceState != null) {
            mPendingPhotoTaskId = savedInstanceState.getLong("pending_photo_task_id", -1);
            String uriStr = savedInstanceState.getString("pending_photo_uri", null);
            if (uriStr != null) {
                mPendingPhotoUri = Uri.parse(uriStr);
            }
            mHasPromptedRetroactiveOnStart = savedInstanceState.getBoolean("has_prompted_retroactive_on_start", false);
            mHasCongratulatedThisWeek = savedInstanceState.getBoolean("has_congratulated_this_week", false);
        }

        mBtnRetroactivePhoto = mBinding.btnRetroactivePhoto;
        mFlowerCapsuleContainer = mBinding.flowerCapsuleContainer;
        long currentUserId = ((com.nearby.justnow.JustNowApplication) requireActivity()
            .getApplication()).getCurrentUserId();
        mPhotoRepository = new TaskPhotoRepository(
            AppDatabase.getInstance(requireContext(), currentUserId));

        setupFlowerCapsuleLayout();
        setupRetroactivePhotoButton();
        applyThemeColor();
    }

    // ==========================================
    // 时光胶囊"七朵花"自适应收集栏与补拍核心逻辑
    // ==========================================

    private void setupFlowerCapsuleLayout() {
        if (mFlowerCapsuleContainer == null) return;
        mFlowerCapsuleContainer.removeAllViews();

        // 1. 动态生成最左侧/最上方的彩色相册/照片图标
        ImageView ivAlbum = new ImageView(requireContext());
        ivAlbum.setImageResource(R.drawable.ic_album);
        ivAlbum.setScaleType(ImageView.ScaleType.FIT_CENTER);

        // 单独点击照片图标拉起时光胶囊周照片回顾墙页面
        ivAlbum.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), TimeCapsuleWallActivity.class);
            intent.putExtra("monday_start_ms", getMondayStartMs());
            startActivity(intent);
        });

        // 2. 动态生成 7 个 FlowerCapsuleView
        for (int i = 0; i < 7; i++) {
            FlowerCapsuleView flowerView = new FlowerCapsuleView(requireContext());
            flowerView.setFlowerColors(0xFFE91E63, 0xFFFF80AB);
            flowerView.setProgress(0);
            mFlowerViews[i] = flowerView;
        }

        // 3. 收集栏本体点击逻辑：通关弹出祝贺 / 普通进度 Toast
        mFlowerCapsuleContainer.setOnClickListener(v -> {
            int currentWeeklyActiveFlowers = 0;
            for (FlowerCapsuleView f : mFlowerViews) {
                if (f.getProgress() >= 1) {
                    currentWeeklyActiveFlowers++;
                }
            }
            if (currentWeeklyActiveFlowers >= 5) {
                mCongratsDialog = new CongratulationsDialog(requireContext());
                mCongratsDialog.setOnDismissListener(d -> mCongratsDialog = null);
                mCongratsDialog.show();
            } else {
                Toast.makeText(requireContext(),
                    getString(R.string.s_flower_progress, currentWeeklyActiveFlowers),
                    Toast.LENGTH_SHORT).show();
            }
        });

        // 4. 根据当前方向执行自适应排列
        boolean isLandscape = getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int sizePx = getResources().getDimensionPixelSize(R.dimen.flower_item_view_size);
        if (sizePx <= 0) {
            sizePx = (int) (48 * getResources().getDisplayMetrics().density);
        }

        mFlowerCapsuleContainer.removeAllViews();

        if (isLandscape) {
            // 横屏：竖向一列排布
            mFlowerCapsuleContainer.setOrientation(LinearLayout.VERTICAL);
            mFlowerCapsuleContainer.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

            // 照片图标居上
            LinearLayout.LayoutParams albumLp = new LinearLayout.LayoutParams(sizePx, sizePx);
            albumLp.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
            albumLp.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
            ivAlbum.setLayoutParams(albumLp);
            mFlowerCapsuleContainer.addView(ivAlbum);

            // 分隔线
            View divider = new View(requireContext());
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider));
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                (int) (sizePx * 0.7f), (int) (1.5f * getResources().getDisplayMetrics().density));
            dividerLp.bottomMargin = (int) (12 * getResources().getDisplayMetrics().density);
            mFlowerCapsuleContainer.addView(divider, dividerLp);

            // 7 朵花竖直排列，weight=1f 均匀平铺
            for (FlowerCapsuleView f : mFlowerViews) {
                LinearLayout.LayoutParams flowerLp = new LinearLayout.LayoutParams(sizePx, 0, 1.0f);
                flowerLp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
                f.setLayoutParams(flowerLp);
                mFlowerCapsuleContainer.addView(f);
            }
        } else {
            // 竖屏：横向一排展示
            mFlowerCapsuleContainer.setOrientation(LinearLayout.HORIZONTAL);
            mFlowerCapsuleContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);

            // 照片图标居左
            LinearLayout.LayoutParams albumLp = new LinearLayout.LayoutParams(sizePx, sizePx);
            albumLp.rightMargin = (int) (12 * getResources().getDisplayMetrics().density);
            albumLp.leftMargin = (int) (12 * getResources().getDisplayMetrics().density);
            ivAlbum.setLayoutParams(albumLp);
            mFlowerCapsuleContainer.addView(ivAlbum);

            // 分隔线
            View divider = new View(requireContext());
            divider.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider));
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                (int) (1.5f * getResources().getDisplayMetrics().density), (int) (sizePx * 0.7f));
            dividerLp.rightMargin = (int) (12 * getResources().getDisplayMetrics().density);
            mFlowerCapsuleContainer.addView(divider, dividerLp);

            // 7 朵花横向排列，weight=1f 均匀平铺
            for (FlowerCapsuleView f : mFlowerViews) {
                LinearLayout.LayoutParams flowerLp = new LinearLayout.LayoutParams(0, sizePx, 1.0f);
                flowerLp.rightMargin = (int) (6 * getResources().getDisplayMetrics().density);
                f.setLayoutParams(flowerLp);
                mFlowerCapsuleContainer.addView(f);
            }
        }
    }

    private void setupRetroactivePhotoButton() {
        if (mBtnRetroactivePhoto == null) return;
        mBtnRetroactivePhoto.setOnClickListener(v -> {
            AppDatabase.execute(() -> {
                long monday = getMondayStartMs();
                long sundayEnd = monday + (7 * 24 * 60 * 60 * 1000L) - 1;
                List<TaskEntity> completedWithoutPhotos =
                    mPhotoRepository.getCompletedTasksWithoutPhotos(monday, sundayEnd);

                mBtnRetroactivePhoto.post(() -> {
                    if (completedWithoutPhotos.isEmpty()) {
                        Toast.makeText(requireContext(),
                            R.string.s_retroactive_no_pending, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    RetroactivePhotoDialog dialog = new RetroactivePhotoDialog(
                        requireContext(), completedWithoutPhotos,
                        task -> startCameraForTask(task.id));
                    dialog.show();
                });
            });
        });
    }

    private void startCameraForTask(long taskId) {
        try {
            long userId = ((com.nearby.justnow.JustNowApplication) requireActivity()
                .getApplication()).getCurrentUserId();
            File photoDir = new File(requireContext().getExternalFilesDir(
                android.os.Environment.DIRECTORY_PICTURES), String.valueOf(userId));
            if (!photoDir.exists()) {
                photoDir.mkdirs();
            }
            File tempFile = new File(photoDir, "temp_photo_" + taskId + ".jpg");
            if (tempFile.exists()) {
                tempFile.delete();
            }
            tempFile.createNewFile();
            mPendingPhotoUri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", tempFile);
            mPendingPhotoTaskId = taskId;

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mPendingPhotoUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CODE_CAPTURE_PHOTO);
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                getString(R.string.s_camera_start_failed, e.getMessage()),
                Toast.LENGTH_SHORT).show();
        }
    }

    /** 由 MainFragment 在任务完成时调用，弹出拍照引导弹窗 */
    public void showPhotoReminderDialog(TaskEntity task) {
        if (task == null) return;
        mCongratulationDialog = new com.nearby.justnow.ui.dialog.CongratulationDialog(
            requireContext(), task,
            new com.nearby.justnow.ui.dialog.CongratulationDialog.OnActionListener() {
                @Override
                public void onTakePhoto() {
                    startCameraForTask(task.id);
                }

                @Override
                public void onSkip() {
                    refreshWeeklyFlowers();
                }
            });
        mCongratulationDialog.setOnDismissListener(d -> {
            mCongratulationDialog = null;
            refreshWeeklyFlowers();
        });
        mCongratulationDialog.show();
    }

    /** 四象限对应的花瓣加权：紧急重要=3，不紧急重要=2，紧急不重要=2，不紧急不重要=1 */
    private static final int[] QUADRANT_PETALS = {3, 2, 2, 1};

    public void refreshWeeklyFlowers() {
        if (mFlowerCapsuleContainer == null) return;
        AppDatabase.execute(() -> {
            long monday = getMondayStartMs();
            // 用 JOIN 任务表的方法获取每个照片的四象限，按象限加权花瓣数
            List<com.nearby.justnow.data.entity.TaskPhotoWithTask> photos =
                mPhotoRepository.getPhotosWithTaskInWeek(monday);

            // 按时序排列，确保首个紧急重要任务正确识别填花芯
            java.util.Collections.sort(photos, (a, b) ->
                Long.compare(a.photo.createdAt, b.photo.createdAt));

            int[] flowerProgress = new int[7];
            boolean[] centerFilled = new boolean[7];
            Calendar cal = Calendar.getInstance();
            for (com.nearby.justnow.data.entity.TaskPhotoWithTask p : photos) {
                cal.setTimeInMillis(p.photo.createdAt);
                int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
                int index = (dayOfWeek + 5) % 7;
                int quadrant = Math.min(p.taskQuadrant, 3);
                // 紧急重要(Q0)且当天花芯未填 → 填花芯不计花瓣
                if (quadrant == 0 && !centerFilled[index]) {
                    centerFilled[index] = true;
                } else {
                    flowerProgress[index] += QUADRANT_PETALS[quadrant];
                }
            }

            int activeFlowersCount = 0;
            for (int i = 0; i < 7; i++) {
                int progress = Math.min(5, flowerProgress[i]);
                int index = i;
                boolean center = centerFilled[i];
                mFlowerCapsuleContainer.post(() -> {
                    mFlowerViews[index].setProgress(progress);
                    mFlowerViews[index].setCenterFilled(center);
                });
                if (progress == 5 && centerFilled[i]) {
                    activeFlowersCount++;
                }
            }

            final int activeCount = activeFlowersCount;
            mFlowerCapsuleContainer.post(() -> {
                if (activeCount >= 5) {
                    int themeColor = MainFragment.getGlobalThemeColor(requireContext());
                    android.graphics.drawable.GradientDrawable gd =
                        new android.graphics.drawable.GradientDrawable();
                    gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                    if (themeColor == Color.parseColor("#FF4081")) {
                        gd.setColor(Color.parseColor("#FFF9FC"));
                    } else {
                        gd.setColor(Color.parseColor("#F4F8FF"));
                    }
                    int strokeWidth = (int) (2 * getResources().getDisplayMetrics().density);
                    gd.setStroke(strokeWidth, themeColor);
                    gd.setCornerRadius(0f);
                    mFlowerCapsuleContainer.setBackground(gd);

                    int pHor = (int) (8 * getResources().getDisplayMetrics().density);
                    int pVer = (int) (6 * getResources().getDisplayMetrics().density);
                    mFlowerCapsuleContainer.setPadding(pHor, pVer, pHor, pVer);

                    if (!mHasCongratulatedThisWeek) {
                        if (!mIsFirstWeeklyFlowersRefresh && isAdded()) {
                            mCongratsDialog = new CongratulationsDialog(requireContext());
                            mCongratsDialog.setOnDismissListener(d -> mCongratsDialog = null);
                            mCongratsDialog.show();
                        }
                        mHasCongratulatedThisWeek = true;
                    }
                } else {
                    mFlowerCapsuleContainer.setBackgroundResource(
                        R.drawable.bg_flower_container_normal);
                    int pHor = (int) (8 * getResources().getDisplayMetrics().density);
                    int pVer = (int) (6 * getResources().getDisplayMetrics().density);
                    mFlowerCapsuleContainer.setPadding(pHor, pVer, pHor, pVer);
                    mHasCongratulatedThisWeek = false;
                }
                mIsFirstWeeklyFlowersRefresh = false;
            });

            long sundayEnd = monday + (7 * 24 * 60 * 60 * 1000L) - 1;
            List<TaskEntity> completedWithoutPhotos =
                mPhotoRepository.getCompletedTasksWithoutPhotos(monday, sundayEnd);
            if (mPendingPhotoTaskId != -1) {
                java.util.Iterator<TaskEntity> iterator = completedWithoutPhotos.iterator();
                while (iterator.hasNext()) {
                    if (iterator.next().id == mPendingPhotoTaskId) {
                        iterator.remove();
                    }
                }
            }
            mBtnRetroactivePhoto.post(() -> {
                if (completedWithoutPhotos.isEmpty()) {
                    mBtnRetroactivePhoto.setVisibility(View.GONE);
                } else {
                    mBtnRetroactivePhoto.setVisibility(View.VISIBLE);
                    mBtnRetroactivePhoto.setText(
                        getString(R.string.s_retroactive_photo_count,
                            completedWithoutPhotos.size()));

                    if (!mHasPromptedRetroactiveOnStart && isAdded()) {
                        mHasPromptedRetroactiveOnStart = true;
                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                requireContext())
                            .setTitle(R.string.s_retroactive_remind_title)
                            .setMessage(getString(R.string.s_retroactive_remind_msg,
                                completedWithoutPhotos.size()))
                            .setPositiveButton(R.string.s_retroactive_go_shoot,
                                (dialog, which) -> mBtnRetroactivePhoto.performClick())
                            .setNegativeButton(R.string.s_retroactive_later, null)
                            .show();
                    }
                }
            });
        });
    }

    /** 应用主题色到花朵和补拍按钮 */
    public void applyThemeColor() {
        if (!isAdded()) return;
        int themeColor = MainFragment.getGlobalThemeColor(requireContext());

        // 补拍按钮
        if (mBtnRetroactivePhoto != null) {
            mBtnRetroactivePhoto.setStrokeColor(ColorStateList.valueOf(themeColor));
            mBtnRetroactivePhoto.setTextColor(themeColor);
        }

        // 花朵
        for (FlowerCapsuleView f : mFlowerViews) {
            if (f != null) {
                f.setActiveColor(themeColor);
                f.setBaseColor(themeColor);
            }
        }

        // 刷新收集进度
        refreshWeeklyFlowers();
    }

    private long getMondayStartMs() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysOffset = (dayOfWeek + 5) % 7;
        cal.add(Calendar.DAY_OF_YEAR, -daysOffset);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 方向变化时重排花收集栏
        if (mFlowerCapsuleContainer != null) {
            mFlowerCapsuleContainer.removeAllViews();
            setupFlowerCapsuleLayout();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CAPTURE_PHOTO) {
            if (resultCode == android.app.Activity.RESULT_OK) {
                if (mPendingPhotoTaskId != -1 && mPendingPhotoUri != null) {
                    final Uri tempPhotoUri = mPendingPhotoUri;
                    final long finalTaskId = mPendingPhotoTaskId;

                    mPendingPhotoTaskId = -1;
                    mPendingPhotoUri = null;

                    AppDatabase.execute(() -> {
                        android.content.ContentResolver resolver =
                            requireContext().getContentResolver();
                        Uri albumUri = null;

                        try {
                            android.content.ContentValues values =
                                new android.content.ContentValues();
                            String fileName = "IMG_JustNow_task_" + finalTaskId + "_"
                                + System.currentTimeMillis();
                            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName + ".jpg");
                            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                            if (android.os.Build.VERSION.SDK_INT
                                >= android.os.Build.VERSION_CODES.Q) {
                                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/JustNow");
                                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                            }
                            albumUri = resolver.insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

                            if (albumUri != null) {
                                try (java.io.InputStream is = resolver.openInputStream(tempPhotoUri);
                                     java.io.OutputStream os = resolver.openOutputStream(albumUri)) {
                                    if (is != null && os != null) {
                                        byte[] buffer = new byte[8192];
                                        int read;
                                        while ((read = is.read(buffer)) != -1) {
                                            os.write(buffer, 0, read);
                                        }
                                    }
                                }

                                if (android.os.Build.VERSION.SDK_INT
                                    >= android.os.Build.VERSION_CODES.Q) {
                                    android.content.ContentValues updateValues =
                                        new android.content.ContentValues();
                                    updateValues.put(MediaStore.Images.Media.IS_PENDING, 0);
                                    resolver.update(albumUri, updateValues, null, null);
                                }

                                mPhotoRepository.bindPhotoToTask(
                                    finalTaskId, albumUri.toString());

                                try {
                                    android.media.MediaScannerConnection.scanFile(
                                        requireContext(),
                                        new String[]{albumUri.getPath()},
                                        new String[]{"image/jpeg"}, null);
                                } catch (Exception ignored) {}
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                resolver.delete(tempPhotoUri, null, null);
                            } catch (Exception ignored) {}
                        }

                        mBtnRetroactivePhoto.post(() -> {
                            Toast.makeText(requireContext(),
                                R.string.s_photo_saved_album, Toast.LENGTH_SHORT).show();
                            refreshWeeklyFlowers();
                        });
                    });
                }
            } else {
                if (mPendingPhotoUri != null) {
                    final Uri tempPhotoUri = mPendingPhotoUri;
                    mPendingPhotoTaskId = -1;
                    mPendingPhotoUri = null;
                    AppDatabase.execute(() -> {
                        try {
                            requireContext().getContentResolver().delete(
                                tempPhotoUri, null, null);
                        } catch (Exception ignored) {}
                    });
                }
            }
        }
    }

    @Override
    public void onDestroyView() {
        if (mCongratsDialog != null && mCongratsDialog.isShowing()) {
            mCongratsDialog.dismiss();
            mCongratsDialog = null;
        }
        if (mCongratulationDialog != null && mCongratulationDialog.isShowing()) {
            mCongratulationDialog.dismiss();
            mCongratulationDialog = null;
        }
        super.onDestroyView();
        mBinding = null;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong("pending_photo_task_id", mPendingPhotoTaskId);
        if (mPendingPhotoUri != null) {
            outState.putString("pending_photo_uri", mPendingPhotoUri.toString());
        }
        outState.putBoolean("has_prompted_retroactive_on_start",
            mHasPromptedRetroactiveOnStart);
        outState.putBoolean("has_congratulated_this_week", mHasCongratulatedThisWeek);
    }
}
