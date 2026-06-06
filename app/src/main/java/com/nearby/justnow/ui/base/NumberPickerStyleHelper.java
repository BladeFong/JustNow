package com.nearby.justnow.ui.base;

import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import com.nearby.justnow.R;

import java.lang.reflect.Field;

/** NumberPicker 没有公开字号 API，统一处理时间滚轮显示字号。 */
public final class NumberPickerStyleHelper {

    private static final String SELECTOR_WHEEL_PAINT_FIELD = "mSelectorWheelPaint";

    private NumberPickerStyleHelper() {}

    public static void applyTimeTextSize(NumberPicker... pickers) {
        if (pickers == null) return;
        for (NumberPicker picker : pickers) {
            if (picker == null) continue;
            float textSizePx = picker.getResources().getDimension(R.dimen.text_size_title);
            applyTextSize(picker, textSizePx);
            applySelectorPaintTextSize(picker, textSizePx);
            picker.invalidate();
        }
    }

    private static void applyTextSize(View view, float textSizePx) {
        if (view instanceof TextView) {
            ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            applyTextSize(group.getChildAt(i), textSizePx);
        }
    }

    private static void applySelectorPaintTextSize(NumberPicker picker, float textSizePx) {
        try {
            Field field = NumberPicker.class.getDeclaredField(SELECTOR_WHEEL_PAINT_FIELD);
            field.setAccessible(true);
            Object value = field.get(picker);
            if (value instanceof Paint) {
                ((Paint) value).setTextSize(textSizePx);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // 内部字段不可访问时，保留 TextView 字号调整。
        }
    }
}
