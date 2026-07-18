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

    // 每一片花瓣独立的动画缩放比例，取值 0.0f 到 1.0f
    private float[] mPetalScales = new float[]{0f, 0f, 0f, 0f, 0f};
    private android.animation.ValueAnimator[] mAnimators = new android.animation.ValueAnimator[5];

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

        // 初始化静态花瓣状态
        for (int i = 0; i < 5; i++) {
            mPetalScales[i] = (i < mProgress) ? 1.0f : 0.0f;
        }
    }

    public void setProgress(int progress) {
        int oldProgress = mProgress;
        mProgress = Math.max(0, Math.min(5, progress));
        if (oldProgress != mProgress) {
            animatePetals();
        } else {
            invalidate();
        }
    }

    private void animatePetals() {
        for (int i = 0; i < 5; i++) {
            final int index = i;
            float targetScale = (i < mProgress) ? 1.0f : 0.0f;
            if (mPetalScales[index] == targetScale) continue;

            if (mAnimators[index] != null) {
                mAnimators[index].cancel();
            }

            mAnimators[index] = android.animation.ValueAnimator.ofFloat(mPetalScales[index], targetScale);
            mAnimators[index].setDuration(350);
            mAnimators[index].setInterpolator(new android.view.animation.DecelerateInterpolator());
            mAnimators[index].addUpdateListener(animation -> {
                mPetalScales[index] = (float) animation.getAnimatedValue();
                invalidate();
            });
            mAnimators[index].start();
        }
    }

    public void setActiveColor(int activeColor) {
        mActiveColor = activeColor;
        invalidate();
    }

    public void setBaseColor(int baseColor) {
        mBaseColor = baseColor;
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
            
            float scaleVal = mPetalScales[i];
            
            // 绘制填充的已点亮实心花瓣：带缩放微生长效果
            if (scaleVal > 0.0f) {
                canvas.save();
                canvas.scale(scaleVal, scaleVal);
                mFillPaint.setColor(mActiveColor);
                canvas.drawPath(mPetalPath, mFillPaint);
                mStrokePaint.setColor(mBaseColor);
                canvas.drawPath(mPetalPath, mStrokePaint);
                canvas.restore();
            }
            
            // 绘制未点亮虚线花瓣：带逐渐淡出淡入过渡
            if (scaleVal < 1.0f) {
                mDashedPaint.setColor(mBaseColor);
                mDashedPaint.setAlpha((int) ((1.0f - scaleVal) * 255));
                canvas.drawPath(mPetalPath, mDashedPaint);
            }
            
            canvas.restore();
        }

        // 2. 绘制始终填充的黄色实心花芯 (半径 15.5)
        mCenterPaint.setColor(0xFFFFEB3B); // 亮黄色
        canvas.drawCircle(0, 0, 15.5f, mCenterPaint);
        mStrokePaint.setColor(mBaseColor);
        mStrokePaint.setAlpha(255); // 确保边线完全不透明
        canvas.drawCircle(0, 0, 15.5f, mStrokePaint);

        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 当 View 附着到窗口上渲染时，先清空比例然后执行向当前进度的平滑生长动画
        for (int i = 0; i < 5; i++) {
            mPetalScales[i] = 0f;
        }
        animatePetals();
    }

    @Override
    protected void onDetachedFromWindow() {
        for (int i = 0; i < 5; i++) {
            if (mAnimators[i] != null) {
                mAnimators[i].cancel();
                mAnimators[i] = null;
            }
        }
        super.onDetachedFromWindow();
    }
}
