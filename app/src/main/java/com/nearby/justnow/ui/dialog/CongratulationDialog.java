package com.nearby.justnow.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;

import java.util.Locale;

/**
 * 任务完成时的祝贺及拍照引导弹窗 (CongratulationDialog - 单数)
 * 支持象限色彩自适应、保底提示音效及自动TTS语音播报
 */
public class CongratulationDialog extends Dialog implements TextToSpeech.OnInitListener {

    private final TaskEntity mTask;
    private final OnActionListener mListener;
    private TextToSpeech mTTS;
    private boolean mIsTtsInitialized = false;

    private static final String CONGRAT_SPEECH = "您好棒！快去让爸爸妈妈帮忙，拍照记录成果吧！";

    public interface OnActionListener {
        void onTakePhoto();
        void onSkip();
    }

    public CongratulationDialog(@NonNull Context context, @NonNull TaskEntity task, @NonNull OnActionListener listener) {
        super(context);
        this.mTask = task;
        this.mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_congratulation);

        // 设置全透明圆角背景
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        // 绑定组件
        TextView tvTitle = findViewById(R.id.tv_congrat_title);
        MaterialButton btnAction = findViewById(R.id.btn_congrat_action);
        MaterialButton btnSkip = findViewById(R.id.btn_congrat_skip);

        // 根据象限自适应着色
        int colorResId;
        switch (mTask.quadrant) {
            case 0:
                colorResId = R.color.quadrant_urgent_important;
                break;
            case 1:
                colorResId = R.color.quadrant_urgent_not_important;
                break;
            case 2:
                colorResId = R.color.quadrant_not_urgent_important;
                break;
            case 3:
            default:
                colorResId = R.color.quadrant_not_urgent_not_important;
                break;
        }
        int themeColor = ContextCompat.getColor(getContext(), colorResId);
        tvTitle.setTextColor(themeColor);
        btnAction.setBackgroundTintList(ColorStateList.valueOf(themeColor));

        // 按钮监听事件
        btnAction.setOnClickListener(v -> {
            mListener.onTakePhoto();
            dismiss();
        });

        btnSkip.setOnClickListener(v -> {
            mListener.onSkip();
            dismiss();
        });

        // 播放清脆的铃声音效作为音效保底 (确保各种系统下均有声音)
        playCongratsSound();

        // 初始化TTS自动播放
        mTTS = new TextToSpeech(getContext(), this);
    }

    private void playCongratsSound() {
        try {
            android.net.Uri notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION);
            android.media.Ringtone r = android.media.RingtoneManager.getRingtone(getContext(), notificationUri);
            if (r != null) {
                r.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // 中文语音引擎多语系回退加载
            int result = mTTS.setLanguage(Locale.CHINESE);
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = mTTS.setLanguage(Locale.SIMPLIFIED_CHINESE);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = mTTS.setLanguage(Locale.CHINA);
            }
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                result = mTTS.setLanguage(Locale.getDefault());
            }

            mIsTtsInitialized = true;
            mTTS.speak(CONGRAT_SPEECH, TextToSpeech.QUEUE_FLUSH, null, "congrat_task_tts_id");
        }
    }

    @Override
    public void dismiss() {
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
            mTTS = null;
        }
        super.dismiss();
    }
}
