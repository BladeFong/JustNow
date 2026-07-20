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
 * 卡通无切线几何外切胖胖花自定义View (纯静态稳定版)
 */
public class FlowerCapsuleView extends View {

    private int mProgress = 0; // 0 ~ 5
    private boolean mCenterFilled = false; // 花芯是否填充
    private int mBaseColor = 0xFFE91E63; // 默认深粉色描边
    private int mActiveColor = 0xFFFF80AB; // 默认浅粉色花瓣填充

    private Paint mFillPaint;
    private Paint mStrokePaint;
    private Paint mDashedPaint;
    private Paint mCenterFillPaint;
    private Paint mCenterStrokePaint;
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
        // 大线段舒缓虚线
        mDashedPaint.setPathEffect(new DashPathEffect(new float[]{12f, 9f}, 0f));

        mCenterFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterFillPaint.setStyle(Paint.Style.FILL);
        mCenterFillPaint.setColor(0xFFFFEB3B); // 亮黄色花芯填充

        mCenterStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterStrokePaint.setStyle(Paint.Style.STROKE);
        mCenterStrokePaint.setStrokeWidth(3f);

        // 几何精确无交叉开口花瓣路径
        mPetalPath = new Path();
        mPetalPath.moveTo(-9.1f, -12.5f);
        mPetalPath.quadTo(-16f, -25f, 0f, -35f); // 鼓向左侧的侧弧线
        mPetalPath.quadTo(16f, -25f, 9.1f, -12.5f); // 鼓向右侧的侧弧线
    }

    public void setProgress(int progress) {
        mProgress = Math.max(0, Math.min(5, progress));
        invalidate(); // 直接重绘，不再通过Animator过渡，保障绝对正确的显示状态
    }

    public void setActiveColor(int activeColor) {
        mActiveColor = activeColor;
        invalidate();
    }

    public void setBaseColor(int baseColor) {
        mBaseColor = baseColor;
        invalidate();
    }

    public void setCenterFilled(boolean filled) {
        mCenterFilled = filled;
        invalidate();
    }

    public boolean isCenterFilled() {
        return mCenterFilled;
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
        canvas.translate(width / 2f, height / 2f);
        canvas.scale(scale, scale);

        // 绘制 5 片旋转花瓣
        for (int i = 0; i < 5; i++) {
            canvas.save();
            canvas.rotate(i * 72f);
            
            boolean isActivated = (i < mProgress);
            
            if (isActivated) {
                // 已点亮花瓣：静态 100% 完整展现
                mFillPaint.setColor(mActiveColor);
                canvas.drawPath(mPetalPath, mFillPaint);
                mStrokePaint.setColor(mBaseColor);
                canvas.drawPath(mPetalPath, mStrokePaint);
            } else {
                // 未点亮花瓣：静态虚线描边展现
                mDashedPaint.setColor(mBaseColor);
                mDashedPaint.setAlpha(255);
                canvas.drawPath(mPetalPath, mDashedPaint);
            }
            
            canvas.restore();
        }

        // 花芯：填充时实心黄色+实线边框；未填充时空心+虚线边框
        if (mCenterFilled) {
            mCenterFillPaint.setColor(0xFFFFEB3B);
            canvas.drawCircle(0, 0, 15.5f, mCenterFillPaint);
            mStrokePaint.setColor(mBaseColor);
            mStrokePaint.setAlpha(255);
            canvas.drawCircle(0, 0, 15.5f, mStrokePaint);
        } else {
            mDashedPaint.setColor(mBaseColor);
            mDashedPaint.setAlpha(160);
            canvas.drawCircle(0, 0, 15.5f, mDashedPaint);
        }

    }
}
