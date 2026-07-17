package com.nearby.justnow.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.nearby.justnow.R;

import java.util.Locale;

/**
 * 周挑战达成 5 朵花点亮后的通关大奖祝贺弹窗
 */
public class CongratulationsDialog extends Dialog implements TextToSpeech.OnInitListener {

    private TextToSpeech mTTS;
    private boolean mIsTtsInitialized = false;
    private static final String CONGRATS_SPEECH = "太棒了，本周通关了，快让爸爸妈妈帮忙制作纪念作品吧";

    public CongratulationsDialog(@NonNull Context context) {
        super(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_congrats);

        // 设置圆角和大小
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        // 初始化TTS，开始自动播报
        mTTS = new TextToSpeech(getContext(), this);

        findViewById(R.id.btn_congrats_ok).setOnClickListener(v -> dismiss());
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // 中文语音引擎多语系回退策略，支持各大定制版或原生Android系统TTS组件
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
            // 立即开始自动语音播报祝贺
            mTTS.speak(CONGRATS_SPEECH, TextToSpeech.QUEUE_FLUSH, null, "congrats_tts_id");
        }
    }

    @Override
    public void dismiss() {
        // 释放TTS资源，防内存泄漏
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
            mTTS = null;
        }
        super.dismiss();
    }
}
