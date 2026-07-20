package com.nearby.justnow.ui.trendchart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 花瓣总数趋势折线图。
 * Y 轴自适应最大值（向上取整到 5 的倍数），画 5 档虚线 + 数值标签。
 * 数据点圆点+折线连接，风格与 TrendChartView 一致。
 */
public class PetalTrendChartView extends View {

    private static final int DASH_COLOR = Color.parseColor("#E0E0E0");
    private static final int BASELINE_COLOR = Color.parseColor("#BDBDBD");
    private static final int TEXT_COLOR = Color.parseColor("#757575");
    private static final int DEFAULT_LINE_COLOR = Color.parseColor("#2196F3");

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBaselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<String> mLabels = new ArrayList<>();
    private final List<Integer> mValues = new ArrayList<>();
    private int mMaxValue;

    public PetalTrendChartView(Context context) { super(context); init(); }
    public PetalTrendChartView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        mLinePaint.setColor(DEFAULT_LINE_COLOR);
        mLinePaint.setStrokeWidth(2f * density);
        mLinePaint.setStyle(Paint.Style.STROKE);

        mDotPaint.setColor(DEFAULT_LINE_COLOR);
        mDotPaint.setStyle(Paint.Style.FILL);

        mFillPaint.setColor(makeLightColor(DEFAULT_LINE_COLOR));
        mFillPaint.setStyle(Paint.Style.FILL);

        mDashPaint.setColor(DASH_COLOR);
        mDashPaint.setStrokeWidth(1f * density);
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setPathEffect(new DashPathEffect(new float[]{8f * density, 4f * density}, 0));

        mBaselinePaint.setColor(BASELINE_COLOR);
        mBaselinePaint.setStrokeWidth(1.5f * density);
        mBaselinePaint.setStyle(Paint.Style.STROKE);

        mTextPaint.setColor(TEXT_COLOR);
        mTextPaint.setTextSize(10f * density);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        setMinimumHeight((int) (80 * density));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int desiredH = (int) (120 * density);
        int height = MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
            ? MeasureSpec.getSize(heightMeasureSpec)
            : Math.min(desiredH, MeasureSpec.getSize(heightMeasureSpec));
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            height);
    }

    /**
     * 设置数据：labels 为 X 轴标签（从旧到新），values 为对应花瓣数。
     * 数据点不足 2 个时隐藏图表。
     */
    public void setData(List<String> labels, List<Integer> values) {
        mLabels.clear();
        mValues.clear();
        mMaxValue = 0;
        if (labels != null && values != null) {
            for (int i = 0; i < Math.min(labels.size(), values.size()); i++) {
                mLabels.add(labels.get(i));
                int v = values.get(i) != null ? values.get(i) : 0;
                mValues.add(v);
                if (v > mMaxValue) mMaxValue = v;
            }
        }
        // Y 轴最大值向上取整到 5 的倍数，至少 5
        mMaxValue = ((mMaxValue + 4) / 5) * 5;
        if (mMaxValue < 5) mMaxValue = 5;
        invalidate();
    }

    /** 设置折线/圆点/填充的颜色（跟随主题色） */
    public void setColor(int color) {
        mLinePaint.setColor(color);
        mDotPaint.setColor(color);
        mFillPaint.setColor(makeLightColor(color));
        invalidate();
    }

    /** 生成浅色填充色：取主题色的低透明度版本 */
    private static int makeLightColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        return Color.argb(30, r, g, b);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int count = mValues.size();
        if (count < 2) return;

        int w = getWidth();
        int h = getHeight();
        float density = getResources().getDisplayMetrics().density;
        float dotRadius = 4f * density;

        float padLeft = 40f * density;
        float padRight = 16f * density;
        float padTop = 8f * density;
        float padBottom = 28f * density;
        float chartLeft = padLeft;
        float chartRight = w - padRight;
        float chartTop = padTop;
        float chartBottom = h - padBottom;
        float chartWidth = chartRight - chartLeft;
        float chartHeight = chartBottom - chartTop;
        int yLevels = 5;

        // Y 轴虚线 + 数值标签
        for (int i = 0; i < yLevels; i++) {
            float y = chartTop + chartHeight * i / (yLevels - 1);
            int val = mMaxValue * (yLevels - 1 - i) / (yLevels - 1);
            if (i == yLevels - 1) {
                canvas.drawLine(chartLeft, y, chartRight, y, mBaselinePaint);
            } else {
                canvas.drawLine(chartLeft, y, chartRight, y, mDashPaint);
            }
            // Y 轴数值标签
            canvas.drawText(String.valueOf(val), chartLeft - 8f * density, y + 4f * density, mTextPaint);
        }

        // 数据点坐标
        float[] xs = new float[count];
        float[] ys = new float[count];
        float stepX = count > 1 ? chartWidth / (count - 1) : 0;
        for (int i = 0; i < count; i++) {
            xs[i] = chartLeft + stepX * i;
            float ratio = mMaxValue > 0 ? (float) mValues.get(i) / mMaxValue : 0;
            ys[i] = chartBottom - chartHeight * ratio;
        }

        // 折线下方填充
        Path fillPath = new Path();
        fillPath.moveTo(xs[0], chartBottom);
        for (int i = 0; i < count; i++) {
            fillPath.lineTo(xs[i], ys[i]);
        }
        fillPath.lineTo(xs[count - 1], chartBottom);
        fillPath.close();
        canvas.drawPath(fillPath, mFillPaint);

        // 折线
        Path linePath = new Path();
        linePath.moveTo(xs[0], ys[0]);
        for (int i = 1; i < count; i++) {
            linePath.lineTo(xs[i], ys[i]);
        }
        canvas.drawPath(linePath, mLinePaint);

        // 圆点
        for (int i = 0; i < count; i++) {
            canvas.drawCircle(xs[i], ys[i], dotRadius, mDotPaint);
        }

        // X 轴标签（间隔显示避免重叠）
        int labelInterval = count > 7 ? 2 : 1;
        Paint.Align prevAlign = mTextPaint.getTextAlign();
        mTextPaint.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < count; i += labelInterval) {
            float labelY = chartBottom + 16f * density;
            canvas.drawText(mLabels.get(i), xs[i], labelY, mTextPaint);
        }
        mTextPaint.setTextAlign(prevAlign);
    }
}
