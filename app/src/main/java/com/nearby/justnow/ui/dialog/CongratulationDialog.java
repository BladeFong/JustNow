package com.nearby.justnow.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;

/**
 * 任务完成时的祝贺及拍照引导弹窗 (CongratulationDialog - 单数)
 * 支持象限色彩自适应、去紫色字体、保底提示音效及自动TTS语音播报
 */
public class CongratulationDialog extends Dialog {

    private final TaskEntity mTask;
    private final TaskPhotoRepository mPhotoRepository;
    private final OnActionListener mListener;

    public interface OnActionListener {
        void onTakePhoto();
        void onSkip();
    }

    public CongratulationDialog(@NonNull Context context, @NonNull TaskEntity task,
                                @NonNull TaskPhotoRepository photoRepository,
                                @NonNull OnActionListener listener) {
        super(context);
        this.mTask = task;
        this.mPhotoRepository = photoRepository;
        this.mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_congratulation);

        // 设置全透明圆角背景，消除多余框
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        // 绑定组件
        View rlHeader = findViewById(R.id.rl_congrat_header);
        TextView tvCongratsTitle = findViewById(R.id.tv_congrat_congrats_title);
        MaterialButton btnAction = findViewById(R.id.btn_congrat_action);
        MaterialButton btnSkip = findViewById(R.id.btn_congrat_skip);

        // 拍照提示等定制对话框也统统改用全局主题色以求色彩完全统一
        int themeColor = com.nearby.justnow.ui.main.MainFragment.getGlobalThemeColor(getContext());

        // 色彩应用：Header 背景、加粗大字“您好棒！”
        rlHeader.setBackgroundColor(themeColor);
        tvCongratsTitle.setTextColor(themeColor);

        // 按钮统一着色以彻底去除默认紫色
        btnAction.setBackgroundTintList(ColorStateList.valueOf(themeColor));
        btnAction.setTextColor(Color.WHITE);
        btnSkip.setTextColor(themeColor); // 强制把暂不拍照的TextButton设为该象限配色

        // 按钮监听事件：后台检查张数后再决定拍照或 Toast
        btnAction.setOnClickListener(v -> {
            AppDatabase.execute(() -> {
                boolean reached = mPhotoRepository.isPhotoLimitReached(mTask.id);
                v.post(() -> {
                    if (reached) {
                        android.widget.Toast.makeText(getContext(),
                            R.string.s_photo_limit_reached,
                            android.widget.Toast.LENGTH_SHORT).show();
                        dismiss();
                        return;
                    }
                    mListener.onTakePhoto();
                    dismiss();
                });
            });
        });

        btnSkip.setOnClickListener(v -> {
            mListener.onSkip();
            dismiss();
        });
    }
}
