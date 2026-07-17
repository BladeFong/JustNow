package com.nearby.justnow.ui.main;

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
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.nearby.justnow.R;

import java.util.Locale;

/**
 * 周挑战达成 5 朵花点亮后的通关大奖祝贺弹窗
 * 按钮与字体颜色全部跟随全局主题色，彻底去紫色
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

        // 设置全透明圆角底框
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT;
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(lp);
        }

        // 获取全局主题色并渲染
        int themeColor = MainFragment.getGlobalThemeColor(getContext());
        
        TextView tvTitle = findViewById(R.id.tv_congrats_title);
        MaterialButton btnOk = findViewById(R.id.btn_congrats_ok);
        
        if (tvTitle != null) {
            tvTitle.setTextColor(themeColor);
        }
        if (btnOk != null) {
            btnOk.setBackgroundTintList(ColorStateList.valueOf(themeColor));
            btnOk.setTextColor(Color.WHITE); // 强行设白色，去紫色
            btnOk.setOnClickListener(v -> dismiss());
        }

        // 初始化TTS，Context 还原为 getContext()，确保厂商定制TTS能正常完成Service绑定
        mTTS = new TextToSpeech(getContext(), this);
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
            
            // 将语音也强行路由到 STREAM_MUSIC 媒体通道播放
            Bundle params = new Bundle();
            params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
            mTTS.speak(CONGRATS_SPEECH, TextToSpeech.QUEUE_FLUSH, params, "congrats_tts_id");
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
