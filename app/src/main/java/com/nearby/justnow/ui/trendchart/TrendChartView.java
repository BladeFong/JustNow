package com.nearby.justnow.ui.trendchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import com.nearby.justnow.data.entity.TaskCompletionCounterEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 任务完成趋势折线图 — 最近 10 个周期完成率。
 * Y 轴 5 档（0%/25%/50%/75%/100%），0% 为浅色实线+刻度，其余为浅色虚线。
 * 数据点圆点+折线连接，风格类似基金业绩走势。
 */
public class TrendChartView extends View {

    private static final int Y_LEVELS = 5; // 0%, 25%, 50%, 75%, 100%
    private static final int LINE_COLOR = Color.parseColor("#2196F3");
    private static final int DASH_COLOR = Color.parseColor("#E0E0E0");
    private static final int BASELINE_COLOR = Color.parseColor("#BDBDBD");

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBaselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 完成率 0..1，index 0 = 最新周期（图表最右侧） */
    private final List<Float> mRatios = new ArrayList<>();
    private int mQuota = 1;

    public TrendChartView(Context context) { super(context); init(); }
    public TrendChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        mLinePaint.setColor(LINE_COLOR);
        mLinePaint.setStrokeWidth(2f * density);
        mLinePaint.setStyle(Paint.Style.STROKE);

        mDotPaint.setColor(LINE_COLOR);
        mDotPaint.setStyle(Paint.Style.FILL);

        mDashPaint.setColor(DASH_COLOR);
        mDashPaint.setStrokeWidth(1f * density);
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setPathEffect(new DashPathEffect(new float[]{8f * density, 4f * density}, 0));

        mBaselinePaint.setColor(BASELINE_COLOR);
        mBaselinePaint.setStrokeWidth(1.5f * density);
        mBaselinePaint.setStyle(Paint.Style.STROKE);

        setMinimumHeight((int) (80 * density));
    }

    /**
     * 设置数据：counters 来自 task_completion_counter 表（ORDER BY period_key DESC，period_key 倒序），
     * 第 0 个 = 最新周期（图表最右侧）。
     */
    public void setData(List<TaskCompletionCounterEntity> counters, int quota) {
        mQuota = Math.max(1, quota);
        mRatios.clear();
        if (counters != null) {
            for (TaskCompletionCounterEntity c : counters) {
                mRatios.add(Math.min(1f, (float) c.completed / mQuota));
            }
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = mRatios.size();
        if (count < 2) return;

        int w = getWidth();
        int h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float dotRadius = 4f * density;

        float padLeft = 32f * density;
        float padRight = 32f * density;
        float padTop = 12f * density;
        float padBottom = 12f * density;
        float chartLeft = padLeft;
        float chartRight = w - padRight;
        float chartTop = padTop;
        float chartBottom = h - padBottom;
        float chartWidth = chartRight - chartLeft;
        float chartHeight = chartBottom - chartTop;

        // Y 轴 5 档横线：从上到下 100% → 0%
        for (int i = 0; i < Y_LEVELS; i++) {
            float y = chartTop + chartHeight * i / (Y_LEVELS - 1);
            if (i == Y_LEVELS - 1) {
                // 0% 基线：浅色实线 + 底部竖线刻度
                canvas.drawLine(chartLeft, y, chartRight, y, mBaselinePaint);
                for (int tick = 0; tick <= 10; tick++) {
                    float tx = chartLeft + chartWidth * tick / 10;
                    canvas.drawLine(tx, y - 3f * density, tx, y + 3f * density, mBaselinePaint);
                }
            } else {
                // 其他 4 档：浅色虚线
                canvas.drawLine(chartLeft, y, chartRight, y, mDashPaint);
            }
        }

        // X 轴数据点（从左到右 = 从旧到新，即 index count-1 → 0）
        float[] xs = new float[count];
        float[] ys = new float[count];
        for (int i = 0; i < count; i++) {
            // 数据 index 0 = 最新 = 图表最右侧
            // 所以数据 index i 映射到图表 x = chartRight - (i * step)
            xs[i] = chartRight - chartWidth * i / (count - 1);
            ys[i] = chartBottom - chartHeight * mRatios.get(i); // 0% 在底部
        }

        // 折线（从左到右绘制，所以从 index count-1 开始，到 index 0）
        Path path = new Path();
        path.moveTo(xs[count - 1], ys[count - 1]);
        for (int i = count - 2; i >= 0; i--) {
            path.lineTo(xs[i], ys[i]);
        }
        canvas.drawPath(path, mLinePaint);

        // 圆点
        for (int i = 0; i < count; i++) {
            canvas.drawCircle(xs[i], ys[i], dotRadius, mDotPaint);
        }
    }
}
