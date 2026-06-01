package com.nearby.justnow.ui.main;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TimePeriodEntity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 小时数列 — 贯穿左侧栏全高，显示整点数字
 */
public class HourColumnView extends View {

    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float mDensity;
    private final float mOverflowSpace;
    private List<TimePeriodEntity> mPeriods = new ArrayList<>();

    private int mTopOffset = 0;

    public HourColumnView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        mOverflowSpace = context.getResources().getDimension(R.dimen.timeline_overflow_space);

        int tickColor = ContextCompat.getColor(context, R.color.timeline_tick);
        mTextPaint.setColor(tickColor);
        mTextPaint.setTextSize(context.getResources().getDimension(R.dimen.text_size_caption));
        mTextPaint.setTextAlign(Paint.Align.RIGHT);
    }

    public void setPeriods(List<TimePeriodEntity> periods) {
        mPeriods = periods != null ? periods : new ArrayList<>();
        invalidate();
    }

    /** 设置顶部偏移（像素），用于对齐右侧 TimelineView */
    public void setTopOffset(int px) {
        mTopOffset = px;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || mPeriods.isEmpty()) return;

        Calendar cal = Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        // 查找当前时段
        TimePeriodEntity currentPeriod = null;
        TimePeriodEntity nextPeriod = null;
        for (TimePeriodEntity p : mPeriods) {
            if (nowMinute >= p.startMinute && nowMinute < p.endMinute) {
                currentPeriod = p;
                break;
            }
            if (nextPeriod == null && p.startMinute > nowMinute) {
                nextPeriod = p;
            }
        }
        if (currentPeriod == null && nextPeriod == null) {
            nextPeriod = mPeriods.get(0);
        }
        TimePeriodEntity displayPeriod = (currentPeriod != null) ? currentPeriod : nextPeriod;

        int rangeStart = displayPeriod.startMinute;
        int rangeEnd = displayPeriod.endMinute;
        int totalMinutes = rangeEnd - rangeStart;
        if (totalMinutes <= 0) return;

        int paddingTop = mTopOffset + (int) (4 * mDensity);
        int paddingBottom = (int) (8 * mDensity);
        int drawHeight = h - paddingTop - paddingBottom;
        float overflowSpace = Math.min(mOverflowSpace, Math.max(0, drawHeight * 0.25f));
        float periodHeight = drawHeight - overflowSpace;
        if (periodHeight <= 0) return;
        float textBaseOffset = mTextPaint.getTextSize() * 0.3f;

        // 整点数字（右对齐）
        int firstHour = (rangeStart % 60 == 0) ? rangeStart : (rangeStart / 60 + 1) * 60;
        for (int m = firstHour; m <= rangeEnd; m += 60) {
            float y = paddingTop + (float) (m - rangeStart) / totalMinutes * periodHeight;
            String hourText = String.valueOf(m / 60);
            canvas.drawText(hourText, w - mDensity, y + textBaseOffset, mTextPaint);
        }

    }
}
