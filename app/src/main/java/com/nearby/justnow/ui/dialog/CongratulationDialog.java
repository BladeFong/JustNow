package com.nearby.justnow.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
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
 * 支持象限色彩自适应、去紫色字体、保底提示音效及自动TTS语音播报
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
        TextView tvQuadrantTag = findViewById(R.id.tv_congrat_quadrant_tag);
        TextView tvCongratsTitle = findViewById(R.id.tv_congrat_congrats_title);
        MaterialButton btnAction = findViewById(R.id.btn_congrat_action);
        MaterialButton btnSkip = findViewById(R.id.btn_congrat_skip);

        // 象限文本指示
        String quadrantText;
        switch (mTask.quadrant) {
            case 0:
                quadrantText = "Q1 象限";
                break;
            case 1:
                quadrantText = "Q2 象限";
                break;
            case 2:
                quadrantText = "Q3 象限";
                break;
            case 3:
            default:
                quadrantText = "Q4 象限";
                break;
        }
        // 拍照提示等定制对话框也统统改用全局主题色以求色彩完全统一
        int themeColor = com.nearby.justnow.ui.main.MainFragment.getGlobalThemeColor(getContext());

        // 象限色彩应用：Header 背景、象限 Tag 文本、加粗大字“您好棒！”
        rlHeader.setBackgroundColor(themeColor);
        tvQuadrantTag.setText(quadrantText);
        tvCongratsTitle.setTextColor(themeColor);

        // 按钮统一着色以彻底去除默认紫色
        btnAction.setBackgroundTintList(ColorStateList.valueOf(themeColor));
        btnAction.setTextColor(Color.WHITE);
        btnSkip.setTextColor(themeColor); // 强制把暂不拍照的TextButton设为该象限配色

        // 按钮监听事件
        btnAction.setOnClickListener(v -> {
            mListener.onTakePhoto();
            dismiss();
        });

        btnSkip.setOnClickListener(v -> {
            mListener.onSkip();
            dismiss();
        });

        // 播放清脆的铃声音效作为音效保底 (通过STREAM_MUSIC强制输出以防静音)
        playCongratsSound();

        // 使用 getApplicationContext() 初始化TTS以确保Service绑定成功
        mTTS = new TextToSpeech(getContext().getApplicationContext(), this);
    }

    private void playCongratsSound() {
        try {
            Uri notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(getContext(), notificationUri);
            if (r != null) {
                // 强行设定为音乐通道，防通知免打扰无声屏蔽
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build();
                r.setAudioAttributes(attrs);
                r.play();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
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
            
            // 将语音也路由到 STREAM_MUSIC 媒体通道播放
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            mTTS.speak(CONGRAT_SPEECH, TextToSpeech.QUEUE_FLUSH, params, "congrat_task_tts_id");
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
