package com.nearby.justnow.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;
import com.nearby.justnow.server.TaskPhotoHttpServer;
import com.nearby.justnow.util.NetworkUtils;
import com.nearby.justnow.util.QrCodeUtils;

import java.util.UUID;

/**
 * 平板端扫码传图交互对话框，承载 HTTP 服务生命周期、二维码渲染、心跳监听与休眠保护
 */
public class QrUploadDialog extends Dialog {

    public interface OnUploadFinishedListener {
        void onUploadFinished(int savedCount);
    }

    private static final int DEFAULT_PORT = 8888;
    private static final int MAX_PHOTOS_PER_TASK = 5;
    private static final long MAX_TIMEOUT_MS = 3 * 60 * 1000L; // 3分钟上限
    private static final long HEARTBEAT_TIMEOUT_MS = 5000L;    // 5秒对等感知

    private final TaskPhotoRepository mPhotoRepository;
    private final TaskEntity mTask;
    private final String mThemeColor;
    private final OnUploadFinishedListener mListener;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private TaskPhotoHttpServer mHttpServer;
    private ImageView mIvQrCode;
    private TextView mTvStatus;
    private TextView mTvUrl;
    private View mPbLoading;

    private long mLastHeartbeatMs = 0;
    private boolean mIsConnected = false;

    private final Runnable mHeartbeatChecker = new Runnable() {
        @Override
        public void run() {
            if (mIsConnected && mLastHeartbeatMs > 0
                    && (System.currentTimeMillis() - mLastHeartbeatMs > HEARTBEAT_TIMEOUT_MS)) {
                // 超过 5 秒无心跳，判定手机离线
                mIsConnected = false;
                mLastHeartbeatMs = 0;
                if (mTvStatus != null) {
                    mTvStatus.setText(R.string.s_qr_upload_waiting);
                }
                // 释放常亮锁，遵循系统自然休眠并重新调度超时关闭
                Window window = getWindow();
                if (window != null) {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                scheduleAutoTimeout();
            }
            mHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable mTimeoutDismissRunnable = this::dismiss;

    public QrUploadDialog(@NonNull Context context,
                          @NonNull TaskPhotoRepository photoRepository,
                          @NonNull TaskEntity task,
                          @NonNull String themeColor,
                          OnUploadFinishedListener listener) {
        super(context);
        this.mPhotoRepository = photoRepository;
        this.mTask = task;
        this.mThemeColor = themeColor;
        this.mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_qr_upload);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getContext().getResources().getDisplayMetrics().widthPixels * 0.75f);
            if (lp.width > 560 * getContext().getResources().getDisplayMetrics().density) {
                lp.width = (int) (560 * getContext().getResources().getDisplayMetrics().density);
            }
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        setCanceledOnTouchOutside(true);

        mIvQrCode = findViewById(R.id.iv_qr_code);
        mTvStatus = findViewById(R.id.tv_qr_status);
        mTvUrl = findViewById(R.id.tv_qr_url);
        mPbLoading = findViewById(R.id.pb_qr_loading);

        findViewById(R.id.btn_close_qr_dialog).setOnClickListener(v -> dismiss());

        initTaskHeader();
        startServerAndGenerateQr();
        scheduleAutoTimeout();
    }

    private void initTaskHeader() {
        TextView tvTitle = findViewById(R.id.tv_qr_task_title);
        ImageView ivIcon = findViewById(R.id.iv_qr_task_icon);
        View vQuadrant = findViewById(R.id.v_qr_task_quadrant_bar);
        TextView tvRemaining = findViewById(R.id.tv_qr_remaining_count);

        if (tvTitle != null) {
            tvTitle.setText(mTask.content != null ? mTask.content : "");
        }

        if (ivIcon != null) {
            if (mTask.iconName != null && !mTask.iconName.isEmpty()) {
                String resName = "ic_activity_" + mTask.iconName;
                int resId = getContext().getResources().getIdentifier(
                        resName, "drawable", getContext().getPackageName());
                if (resId != 0) {
                    ivIcon.setImageResource(resId);
                    ivIcon.setVisibility(View.VISIBLE);
                } else {
                    ivIcon.setVisibility(View.GONE);
                }
            } else {
                ivIcon.setVisibility(View.GONE);
            }
        }

        if (vQuadrant != null) {
            int quadrantColor;
            switch (mTask.quadrant) {
                case 1:
                    quadrantColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_not_important);
                    break;
                case 2:
                    quadrantColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_important);
                    break;
                case 3:
                    quadrantColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_not_important);
                    break;
                case 0:
                default:
                    quadrantColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_important);
                    break;
            }
            vQuadrant.setBackgroundColor(quadrantColor);
        }

        AppDatabase.execute(() -> {
            int currentCount = mPhotoRepository.getPhotoCountForTask(mTask.id);
            int remaining = Math.max(0, MAX_PHOTOS_PER_TASK - currentCount);
            if (tvRemaining != null) {
                tvRemaining.post(() -> tvRemaining.setText(
                        getContext().getString(R.string.s_qr_remaining_photos, remaining)));
            }
        });
    }

    private void startServerAndGenerateQr() {
        String localIp = NetworkUtils.getLocalIpAddress(getContext());
        if (localIp == null) {
            if (mTvStatus != null) {
                mTvStatus.setText(R.string.s_qr_no_network);
            }
            if (mIvQrCode != null) {
                mIvQrCode.setVisibility(View.GONE);
            }
            Toast.makeText(getContext(), R.string.s_qr_no_network, Toast.LENGTH_SHORT).show();
            return;
        }

        String token = UUID.randomUUID().toString().substring(0, 8);
        mHttpServer = new TaskPhotoHttpServer(
                getContext(), mPhotoRepository, mTask, token, MAX_PHOTOS_PER_TASK, mThemeColor);

        mHttpServer.setHeartbeatListener(taskId -> {
            mLastHeartbeatMs = System.currentTimeMillis();
            if (!mIsConnected) {
                mIsConnected = true;
                if (mTvStatus != null) {
                    mTvStatus.setText(R.string.s_qr_upload_connected);
                }
                // 手机连接期间保持常亮，并暂缓自然休眠自动关闭
                mHandler.removeCallbacks(mTimeoutDismissRunnable);
                Window window = getWindow();
                if (window != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
        });

        mHttpServer.setUploadCompleteListener(new TaskPhotoHttpServer.OnUploadCompleteListener() {
            @Override
            public void onUploadSuccess(long taskId, int savedCount) {
                if (mTvStatus != null) {
                    mTvStatus.setText(getContext().getString(R.string.s_qr_upload_success, savedCount));
                }
                if (mListener != null) {
                    mListener.onUploadFinished(savedCount);
                }
                mHandler.postDelayed(QrUploadDialog.this::dismiss, 1500);
            }

            @Override
            public void onUploadFailure(long taskId, String errorMsg) {
                Toast.makeText(getContext(), "上传异常: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });

        int port = mHttpServer.start(DEFAULT_PORT);
        if (port == -1) {
            Toast.makeText(getContext(), "无法启动本地网络服务", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        String uploadUrl = NetworkUtils.buildUploadUrl(localIp, port, mTask.id, token);
        if (mTvUrl != null) {
            mTvUrl.setText(uploadUrl);
        }

        // 生成二维码位图
        int qrSize = (int) (240 * getContext().getResources().getDisplayMetrics().density);
        Bitmap qrBitmap = QrCodeUtils.generateQrCode(uploadUrl, qrSize, qrSize);
        if (qrBitmap != null && mIvQrCode != null) {
            mIvQrCode.setImageBitmap(qrBitmap);
            mIvQrCode.setVisibility(View.VISIBLE);
        }

        // 启动本地心跳检查轮询
        mHandler.postDelayed(mHeartbeatChecker, 1000);
    }

    private void scheduleAutoTimeout() {
        long systemTimeout = 60000L;
        try {
            systemTimeout = Settings.System.getInt(
                    getContext().getContentResolver(),
                    Settings.System.SCREEN_OFF_TIMEOUT, 60000);
        } catch (Exception ignored) {}

        long effectiveTimeout = Math.min(systemTimeout, MAX_TIMEOUT_MS);
        mHandler.postDelayed(mTimeoutDismissRunnable, effectiveTimeout);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mHandler.removeCallbacksAndMessages(null);
        if (mHttpServer != null) {
            mHttpServer.stop();
            mHttpServer = null;
        }
        Window window = getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
