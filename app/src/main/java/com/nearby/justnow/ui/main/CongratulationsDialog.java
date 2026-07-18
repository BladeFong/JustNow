package com.nearby.justnow.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.nearby.justnow.R;

/**
 * 周挑战达成 5 朵花点亮后的通关大奖祝贺弹窗
 * 按钮与字体颜色全部跟随全局主题色，彻底去紫色
 */
public class CongratulationsDialog extends Dialog {

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
    }
}
