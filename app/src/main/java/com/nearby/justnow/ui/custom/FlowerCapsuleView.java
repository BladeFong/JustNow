package com.nearby.justnow.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.nearby.justnow.R;

/**
 * 卡通无切线几何外切胖胖花自定义View
 */
public class FlowerCapsuleView extends View {

    private int mProgress = 0; // 0 ~ 5
    private int mBaseColor = 0xFFE91E63; // 默认深粉色描边
    private int mActiveColor = 0xFFFF80AB; // 默认浅粉色花瓣填充
    
    private Paint mFillPaint;
    private Paint mStrokePaint;
    private Paint mDashedPaint;
    private Paint mCenterPaint;
    private Path mPetalPath;

    public FlowerCapsuleView(Context context) {
        this(context, null);
    }

    public FlowerCapsuleView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowerCapsuleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FlowerCapsuleView);
            mProgress = a.getInt(R.styleable.FlowerCapsuleView_flowerProgress, 0);
            mBaseColor = a.getColor(R.styleable.FlowerCapsuleView_flowerBaseColor, 0xFFE91E63);
            mActiveColor = a.getColor(R.styleable.FlowerCapsuleView_flowerActiveColor, 0xFFFF80AB);
            a.recycle();
        }

        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);

        mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mStrokePaint.setStrokeWidth(3f); // 在100x100基准下约1.5dp

        mDashedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDashedPaint.setStyle(Paint.Style.STROKE);
        mDashedPaint.setStrokeWidth(3f);
        // 大线段舒缓虚线：线段8dp，间距6dp。在100x100基准下定义比例为 [12f, 9f]
        mDashedPaint.setPathEffect(new DashPathEffect(new float[]{12f, 9f}, 0f));

        mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterPaint.setStyle(Paint.Style.FILL);
        mCenterPaint.setColor(0xFFFFEB3B); // 亮黄色花芯

        // 几何精确无交叉开口花瓣路径
        mPetalPath = new Path();
        mPetalPath.moveTo(-9.1f, -12.5f);
        mPetalPath.quadTo(-16f, -25f, 0f, -35f); // 鼓向左侧的侧弧线
        mPetalPath.quadTo(16f, -25f, 9.1f, -12.5f); // 鼓向右侧的侧弧线
        // 不调用 mPetalPath.close()，底端开口直接无缝贴合花芯
    }

    public void setProgress(int progress) {
        mProgress = Math.max(0, Math.min(5, progress));
        invalidate();
    }

    public int getProgress() {
        return mProgress;
    }

    public void setFlowerColors(int baseColor, int activeColor) {
        mBaseColor = baseColor;
        mActiveColor = activeColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        float scale = Math.min(width, height) / 100f;

        canvas.save();
        // 平移到控件中心并等比缩放
        canvas.translate(width / 2f, height / 2f);
        canvas.scale(scale, scale);

        // 1. 绘制 5 片旋转花瓣
        for (int i = 0; i < 5; i++) {
            canvas.save();
            canvas.rotate(i * 72f);
            if (i < mProgress) {
                // 已点亮：粉色填充 + 实线描边
                mFillPaint.setColor(mActiveColor);
                canvas.drawPath(mPetalPath, mFillPaint);
                mStrokePaint.setColor(mBaseColor);
                canvas.drawPath(mPetalPath, mStrokePaint);
            } else {
                // 未点亮：大线段虚线描边
                mDashedPaint.setColor(mBaseColor);
                canvas.drawPath(mPetalPath, mDashedPaint);
            }
            canvas.restore();
        }

        // 2. 绘制始终填充的黄色实心花芯 (半径 15.5)
        mCenterPaint.setColor(0xFFFFEB3B); // 亮黄色
        canvas.drawCircle(0, 0, 15.5f, mCenterPaint);
        mStrokePaint.setColor(mBaseColor);
        canvas.drawCircle(0, 0, 15.5f, mStrokePaint);

        canvas.restore();
    }
}
