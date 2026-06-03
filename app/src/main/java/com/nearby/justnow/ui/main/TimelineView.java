package com.nearby.justnow.ui.main;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 时间线容器 — 左侧栏背景色块 + 刻度尺 + 任务条形图 + 当前时间浮标
 */
public class TimelineView extends LinearLayout {

    private static final float TICK_LEN_30 = 12;
    private static final float TICK_LEN_15 = 6;
    private static final int OVERFLOW_MINUTES = 15;

    private final Paint mTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mHourTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActiveTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActiveHourTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActiveLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarDonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarDoneStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarStatusStripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarTextDonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarMetaTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBarMetaTextDonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLiquidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNowBuoyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mNowBuoyInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect mCachedAreaRect = new Rect();
    private final RectF mTaskBarRect = new RectF();
    private final RectF mTaskStatusStripRect = new RectF();
    private final Path mNowBuoyPath = new Path();

    private final float mDensity;

    private final int mHighlightColor;
    private final int mTimelineTickColor;
    private final int mTimelineNowColor;
    private final float mNowBuoyHalfWidth;
    private final float mNowBuoyHalfHeight;
    private final float mNowBuoyInnerRadius;
    private final float mNowBuoyJitter;
    private final float mTaskCornerRadius;
    private final float mTaskTextPadding;
    private final float mTaskStatusStripWidth;
    private final float mTaskStrokeWidth;
    private final float mOverflowSpace;
    private final float mBarTextLineGap;

    private List<TimePeriodEntity> mPeriods = new ArrayList<>();
    private List<TimePeriodEntity> mActivePeriods = new ArrayList<>();
    private List<TimelineItem> mTimelineItems = new ArrayList<>();
    private OnTimelineItemClickListener mTimelineItemClickListener;

    private boolean mHasRunningTask = false;
    private boolean mHasExternalRunningTask = false;

    /** 浮标抖动动画 */
    private ValueAnimator mBuoyAnimator;
    private float mBuoyJitterOffset = 0f;

    /** 缓存的 timelineArea View，避免 onDraw 中重复 findViewById */
    private View mTimelineAreaView;
    private boolean mAreaViewResolved = false;

    public TimelineView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mDensity = context.getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setClickable(true);

        mHighlightColor = ContextCompat.getColor(context, R.color.highlight);
        mTimelineTickColor = ContextCompat.getColor(context, R.color.timeline_tick);
        mTimelineNowColor = ContextCompat.getColor(context, R.color.timeline_now);
        mNowBuoyHalfWidth = context.getResources().getDimension(R.dimen.timeline_now_buoy_half_width);
        mNowBuoyHalfHeight = context.getResources().getDimension(R.dimen.timeline_now_buoy_half_height);
        mNowBuoyInnerRadius = context.getResources().getDimension(R.dimen.timeline_now_buoy_inner_radius);
        mNowBuoyJitter = context.getResources().getDimension(R.dimen.timeline_now_buoy_jitter);
        mTaskCornerRadius = context.getResources().getDimension(R.dimen.timeline_task_corner_radius);
        mTaskTextPadding = context.getResources().getDimension(R.dimen.timeline_task_text_padding);
        mTaskStatusStripWidth = context.getResources().getDimension(R.dimen.timeline_task_status_strip_width);
        mTaskStrokeWidth = context.getResources().getDimension(R.dimen.timeline_task_stroke_width);
        mOverflowSpace = context.getResources().getDimension(R.dimen.timeline_overflow_space);

        mTickPaint.setColor(mTimelineTickColor);
        mTickPaint.setStrokeWidth(mDensity * 1.2f);

        mHourTickPaint.setColor(mTimelineTickColor);
        mHourTickPaint.setStrokeWidth(mDensity * 3.0f);

        int activeTickColor = ContextCompat.getColor(context, R.color.timeline_active_tick);
        mActiveTickPaint.setColor(activeTickColor);
        mActiveTickPaint.setStrokeWidth(mDensity * 1.8f);

        mActiveHourTickPaint.setColor(activeTickColor);
        mActiveHourTickPaint.setStrokeWidth(mDensity * 3.6f);

        mActiveLinePaint.setColor(activeTickColor);
        mActiveLinePaint.setStrokeWidth(mDensity * 2.2f);

        mBarPaint.setColor(ContextCompat.getColor(context, R.color.timeline_task_running));
        mBarPaint.setAlpha(255);
        mBarPaint.setStyle(Paint.Style.FILL);

        mBarDonePaint.setColor(ContextCompat.getColor(context, R.color.timeline_task_completed));
        mBarDonePaint.setAlpha(255);
        mBarDonePaint.setStyle(Paint.Style.FILL);

        mBarStrokePaint.setColor(ContextCompat.getColor(context, R.color.timeline_task_running_stroke));
        mBarStrokePaint.setStrokeWidth(mTaskStrokeWidth);
        mBarStrokePaint.setStyle(Paint.Style.STROKE);

        mBarDoneStrokePaint.setColor(ContextCompat.getColor(context, R.color.timeline_task_completed_stroke));
        mBarDoneStrokePaint.setStrokeWidth(mTaskStrokeWidth);
        mBarDoneStrokePaint.setStyle(Paint.Style.STROKE);

        mBarStatusStripPaint.setColor(ContextCompat.getColor(context, R.color.timeline_task_status_strip));
        mBarStatusStripPaint.setStyle(Paint.Style.FILL);

        mBarTextPaint.setColor(ContextCompat.getColor(context, R.color.text_primary));
        mBarTextPaint.setTextSize(context.getResources().getDimension(R.dimen.text_size_caption));
        mBarTextPaint.setFakeBoldText(true);

        mBarTextDonePaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        mBarTextDonePaint.setTextSize(context.getResources().getDimension(R.dimen.text_size_caption));
        mBarTextDonePaint.setFakeBoldText(true);

        mBarMetaTextPaint.setColor(ContextCompat.getColor(context, R.color.text_secondary));
        mBarMetaTextPaint.setTextSize(context.getResources().getDimension(R.dimen.text_size_caption));

        mBarMetaTextDonePaint.setColor(ContextCompat.getColor(context, R.color.text_tertiary));
        mBarMetaTextDonePaint.setTextSize(context.getResources().getDimension(R.dimen.text_size_caption));

        mBarTextLineGap = 2 * mDensity;

        mLiquidPaint.setColor(mHighlightColor);
        mLiquidPaint.setAlpha(40);
        mLiquidPaint.setStyle(Paint.Style.FILL);
        mLiquidPaint.setAntiAlias(false);

        mNowBuoyPaint.setColor(mTimelineNowColor);
        mNowBuoyPaint.setStyle(Paint.Style.FILL);

        mNowBuoyInnerPaint.setColor(ContextCompat.getColor(context, R.color.background_surface));
        mNowBuoyInnerPaint.setStyle(Paint.Style.FILL);

        // 浮标抖动动画：2 秒周期正弦波
        mBuoyAnimator = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        mBuoyAnimator.setDuration(2000);
        mBuoyAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mBuoyAnimator.addUpdateListener(anim -> {
            mBuoyJitterOffset = (float) Math.sin((float) anim.getAnimatedValue()) * mNowBuoyJitter;
            invalidate();
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!mHasRunningTask && mBuoyAnimator != null && !mBuoyAnimator.isStarted()) {
            mBuoyAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mBuoyAnimator != null) {
            mBuoyAnimator.cancel();
        }
    }

    public void setPeriods(List<TimePeriodEntity> periods) {
        this.mPeriods = periods != null ? periods : new ArrayList<>();
        invalidate();
    }

    public void setActivePeriods(List<TimePeriodEntity> periods) {
        this.mActivePeriods = periods != null ? periods : new ArrayList<>();
        invalidate();
    }

    public void setTimelineItems(List<TimelineItem> items) {
        this.mTimelineItems = items != null ? items : new ArrayList<>();
        updateRunningState();
        invalidate();
    }

    public void setHasRunningTask(boolean hasRunningTask) {
        mHasExternalRunningTask = hasRunningTask;
        updateRunningState();
        invalidate();
    }

    private void updateRunningState() {
        boolean wasRunning = mHasRunningTask;
        mHasRunningTask = mHasExternalRunningTask;
        if (!mHasRunningTask) {
            for (TimelineItem item : this.mTimelineItems) {
                if (item.running) {
                    mHasRunningTask = true;
                    break;
                }
            }
        }
        // 有执行中任务时暂停动画，无任务时启动动画
        if (wasRunning != mHasRunningTask) {
            if (mHasRunningTask) {
                mBuoyAnimator.pause();
            } else {
                if (!mBuoyAnimator.isStarted()) mBuoyAnimator.start();
                else mBuoyAnimator.resume();
            }
        }
    }

    public void setOnTimelineItemClickListener(OnTimelineItemClickListener listener) {
        mTimelineItemClickListener = listener;
    }

    public void setPeriodStatus(TimeRemainingCalculator.PeriodStatus status) {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0 || mPeriods.isEmpty()) return;

        // 缓存 timeline_area 坐标
        if (!mAreaViewResolved) {
            mTimelineAreaView = findViewById(R.id.timeline_area);
            mAreaViewResolved = true;
        }
        int areaLeft = 0, areaRight = w, areaTop = 0, areaBottom = h;
        if (mTimelineAreaView != null) {
            mTimelineAreaView.getDrawingRect(mCachedAreaRect);
            offsetDescendantRectToMyCoords(mTimelineAreaView, mCachedAreaRect);
            areaLeft = mCachedAreaRect.left;
            areaRight = mCachedAreaRect.right;
            areaTop = mCachedAreaRect.top;
            areaBottom = mCachedAreaRect.bottom;
        }
        if (areaRight <= areaLeft || areaBottom <= areaTop) return;

        int paddingTop = areaTop + (int) (4 * mDensity);
        int paddingBottom = areaBottom - (int) (8 * mDensity);
        int drawHeight = paddingBottom - paddingTop;
        if (drawHeight <= 0) return;

        float tickLen30 = TICK_LEN_30 * mDensity;
        float tickLen15 = TICK_LEN_15 * mDensity;

        Calendar cal = Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);

        // 查找当前时段 / 下一个时段
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
        boolean isUpcoming = (currentPeriod == null);
        TimePeriodEntity displayPeriod = isUpcoming ? nextPeriod : currentPeriod;

        int rangeStart = displayPeriod.startMinute;
        int rangeEnd = displayPeriod.endMinute;
        int totalMinutes = rangeEnd - rangeStart;
        if (totalMinutes <= 0) return;
        float overflowSpace = Math.min(mOverflowSpace, Math.max(0, drawHeight * 0.25f));
        float periodHeight = drawHeight - overflowSpace;
        if (periodHeight <= 0) return;
        TimePeriodEntity activePeriod = findActivePeriodForDisplay(displayPeriod);
        int activeRangeStart = activePeriod == null ? rangeStart
            : Math.max(rangeStart, activePeriod.startMinute);
        int activeRangeEnd = activePeriod == null ? rangeEnd
            : Math.min(rangeEnd, activePeriod.endMinute);
        boolean hasActiveRange = activeRangeStart < activeRangeEnd;
        boolean isInActivePeriod = activePeriod != null
            && nowMinute >= activePeriod.startMinute && nowMinute < activePeriod.endMinute;

        // 左侧分界线
        float periodBottomY = minuteToY(rangeEnd, rangeStart, rangeEnd, paddingTop,
            periodHeight, overflowSpace);
        canvas.drawLine(areaLeft, paddingTop, areaLeft, periodBottomY, mTickPaint);

        float gap = 3 * mDensity;

        // 刻度线（15分钟短 / 30分钟长 / 60分钟加粗）
        for (int m = rangeStart; m <= rangeEnd; m += 15) {
            float y = minuteToY(m, rangeStart, rangeEnd, paddingTop, periodHeight, overflowSpace);
            float tickLen = (m % 30 == 0) ? tickLen30 : tickLen15;
            Paint p = (m % 60 == 0) ? mHourTickPaint : mTickPaint;
            canvas.drawLine(areaLeft + gap, y, areaLeft + gap + tickLen, y, p);
        }

        if (hasActiveRange) {
            drawActivePeriodMarks(canvas, areaLeft, gap, tickLen30, tickLen15,
                rangeStart, rangeEnd, paddingTop, periodHeight, overflowSpace,
                activeRangeStart, activeRangeEnd);
        }

        if (isUpcoming) {
            return;
        }

        // ---- 当前时段内 ----

        if (isInActivePeriod && !mHasRunningTask) {
            // 剩余时间液体区域
            float liquidTop = minuteToY(nowMinute, rangeStart, rangeEnd, paddingTop,
                periodHeight, overflowSpace);
            float liquidBottom = periodBottomY;
            canvas.drawRect(0, liquidTop, w, liquidBottom, mLiquidPaint);
        }

        // 任务条形图
        float barX = areaLeft + gap + 4 * mDensity;
        float barW = areaRight - barX - 4 * mDensity;

        for (TimelineItem item : mTimelineItems) {
            if (item.startMs <= 0 || item.focusMinutes <= 0) continue;

            long startMs = item.startMs;

            int startMin = minuteOfDay(startMs);
            int endMin;
            if (item.running) {
                // 执行中：按 focusMinutes 占位（表达预期占用）
                endMin = startMin + item.focusMinutes;
            } else {
                // 已完成：按实际耗时占位
                if (item.endMs <= item.startMs) continue;
                int actualMinutes = (int) ((item.endMs - item.startMs) / 60000);
                if (actualMinutes <= 0) continue;
                endMin = startMin + actualMinutes;
            }

            startMin = Math.max(startMin, rangeStart);
            endMin = Math.min(endMin, rangeEnd + OVERFLOW_MINUTES);

            if (startMin >= endMin) continue;

            float barTop = minuteToY(startMin, rangeStart, rangeEnd, paddingTop,
                periodHeight, overflowSpace);
            float barBottom = minuteToY(endMin, rangeStart, rangeEnd, paddingTop,
                periodHeight, overflowSpace);

            barBottom = Math.min(barBottom, paddingTop + periodHeight + overflowSpace);

            boolean isCompleted = !item.running;
            Paint p = isCompleted ? mBarDonePaint : mBarPaint;
            mTaskBarRect.set(barX, barTop, barX + barW, barBottom);
            canvas.drawRoundRect(mTaskBarRect, mTaskCornerRadius, mTaskCornerRadius, p);
            canvas.drawRoundRect(mTaskBarRect, mTaskCornerRadius, mTaskCornerRadius,
                isCompleted ? mBarDoneStrokePaint : mBarStrokePaint);

            if (item.running) {
                float stripRight = Math.min(mTaskBarRect.right, mTaskBarRect.left + mTaskStatusStripWidth);
                mTaskStatusStripRect.set(mTaskBarRect.left, mTaskBarRect.top,
                    stripRight, mTaskBarRect.bottom);
                canvas.save();
                canvas.clipRect(mTaskStatusStripRect);
                canvas.drawRoundRect(mTaskBarRect, mTaskCornerRadius, mTaskCornerRadius, mBarStatusStripPaint);
                canvas.restore();
            } else if (isCompleted) {
                float stripRight = Math.min(mTaskBarRect.right, mTaskBarRect.left + mTaskStatusStripWidth);
                mTaskStatusStripRect.set(mTaskBarRect.left, mTaskBarRect.top,
                    stripRight, mTaskBarRect.bottom);
                canvas.save();
                canvas.clipRect(mTaskStatusStripRect);
                canvas.drawRoundRect(mTaskBarRect, mTaskCornerRadius, mTaskCornerRadius, mBarDoneStrokePaint);
                canvas.restore();
            }

            drawTaskText(canvas, item, isCompleted, barX, barW, barTop, barBottom);
        }

        if (isInActivePeriod) {
            // 当前时间浮标最后绘制，避免被任务块遮挡。
            float nowY = minuteToY(nowMinute, rangeStart, rangeEnd, paddingTop,
                periodHeight, overflowSpace);
            float jitter = mHasRunningTask ? 0 : mBuoyJitterOffset;
            float buoyCenterX = areaLeft + jitter;
            drawNowBuoy(canvas, buoyCenterX, nowY);
        }
    }

    private void drawActivePeriodMarks(Canvas canvas, int areaLeft, float gap,
                                       float tickLen30, float tickLen15,
                                       int rangeStart, int rangeEnd, int paddingTop,
                                       float periodHeight, float overflowSpace,
                                       int activeRangeStart, int activeRangeEnd) {
        float activeTopY = minuteToY(activeRangeStart, rangeStart, rangeEnd, paddingTop,
            periodHeight, overflowSpace);
        float activeBottomY = minuteToY(activeRangeEnd, rangeStart, rangeEnd, paddingTop,
            periodHeight, overflowSpace);
        canvas.drawLine(areaLeft, activeTopY, areaLeft, activeBottomY, mActiveLinePaint);

        for (int m = rangeStart; m <= rangeEnd; m += 15) {
            if (m < activeRangeStart || m > activeRangeEnd) continue;
            float y = minuteToY(m, rangeStart, rangeEnd, paddingTop, periodHeight, overflowSpace);
            float tickLen = (m % 30 == 0) ? tickLen30 : tickLen15;
            Paint p = (m % 60 == 0) ? mActiveHourTickPaint : mActiveTickPaint;
            canvas.drawLine(areaLeft + gap, y, areaLeft + gap + tickLen, y, p);
        }

        drawActiveBoundaryTick(canvas, areaLeft, gap, tickLen30, rangeStart, rangeEnd,
            paddingTop, periodHeight, overflowSpace, activeRangeStart);
        drawActiveBoundaryTick(canvas, areaLeft, gap, tickLen30, rangeStart, rangeEnd,
            paddingTop, periodHeight, overflowSpace, activeRangeEnd);
    }

    private void drawActiveBoundaryTick(Canvas canvas, int areaLeft, float gap, float tickLen,
                                        int rangeStart, int rangeEnd, int paddingTop,
                                        float periodHeight, float overflowSpace, int minute) {
        float y = minuteToY(minute, rangeStart, rangeEnd, paddingTop, periodHeight, overflowSpace);
        canvas.drawLine(areaLeft + gap, y, areaLeft + gap + tickLen, y, mActiveHourTickPaint);
    }

    private TimePeriodEntity findActivePeriodForDisplay(TimePeriodEntity displayPeriod) {
        if (displayPeriod == null) return null;
        if (mActivePeriods.isEmpty()) return displayPeriod;
        for (TimePeriodEntity period : mActivePeriods) {
            if (period != null && displayPeriod.nameKey != null
                && displayPeriod.nameKey.equals(period.nameKey)) {
                return period;
            }
        }
        for (TimePeriodEntity period : mActivePeriods) {
            if (period != null && period.startMinute < displayPeriod.endMinute
                && period.endMinute > displayPeriod.startMinute) {
                return period;
            }
        }
        return null;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
            TimelineItem item = findRunningItemAt(event.getY());
            if (item != null && mTimelineItemClickListener != null) {
                performClick();
                mTimelineItemClickListener.onTimelineItemClick(item);
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    /** 当前时间浮标：单根扁平指南针长针，默认以小时列和刻度区分界线为中心。 */
    private void drawNowBuoy(Canvas canvas, float centerX, float centerY) {
        float tipShoulderW = mNowBuoyHalfWidth * 0.82f;
        float bodyHalfW = mNowBuoyHalfWidth * 0.14f;
        float shoulderH = mNowBuoyHalfHeight * 0.42f;

        mNowBuoyPath.reset();
        mNowBuoyPath.moveTo(centerX - mNowBuoyHalfWidth, centerY);
        mNowBuoyPath.lineTo(centerX - tipShoulderW, centerY - shoulderH);
        mNowBuoyPath.lineTo(centerX - bodyHalfW, centerY - mNowBuoyHalfHeight);
        mNowBuoyPath.lineTo(centerX + bodyHalfW, centerY - mNowBuoyHalfHeight);
        mNowBuoyPath.lineTo(centerX + tipShoulderW, centerY - shoulderH);
        mNowBuoyPath.lineTo(centerX + mNowBuoyHalfWidth, centerY);
        mNowBuoyPath.lineTo(centerX + tipShoulderW, centerY + shoulderH);
        mNowBuoyPath.lineTo(centerX + bodyHalfW, centerY + mNowBuoyHalfHeight);
        mNowBuoyPath.lineTo(centerX - bodyHalfW, centerY + mNowBuoyHalfHeight);
        mNowBuoyPath.lineTo(centerX - tipShoulderW, centerY + shoulderH);
        mNowBuoyPath.close();

        canvas.drawPath(mNowBuoyPath, mNowBuoyPaint);
        canvas.drawCircle(centerX, centerY, mNowBuoyInnerRadius, mNowBuoyInnerPaint);
    }

    private TimelineItem findRunningItemAt(float y) {
        if (mCachedAreaRect.isEmpty() || mPeriods.isEmpty()) return null;
        Calendar cal = Calendar.getInstance();
        int nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        TimePeriodEntity currentPeriod = null;
        for (TimePeriodEntity p : mPeriods) {
            if (nowMinute >= p.startMinute && nowMinute < p.endMinute) {
                currentPeriod = p;
                break;
            }
        }
        if (currentPeriod == null) return null;

        int paddingTop = mCachedAreaRect.top + (int) (4 * mDensity);
        int paddingBottom = mCachedAreaRect.bottom - (int) (8 * mDensity);
        int drawHeight = paddingBottom - paddingTop;
        float overflowSpace = Math.min(mOverflowSpace, Math.max(0, drawHeight * 0.25f));
        float periodHeight = drawHeight - overflowSpace;
        if (periodHeight <= 0) return null;

        for (TimelineItem item : mTimelineItems) {
            if (!item.running || item.focusMinutes <= 0) continue;
            int startMin = Math.max(minuteOfDay(item.startMs), currentPeriod.startMinute);
            int endMin = startMin + item.focusMinutes;
            endMin = Math.min(endMin, currentPeriod.endMinute + OVERFLOW_MINUTES);
            float top = minuteToY(startMin, currentPeriod.startMinute, currentPeriod.endMinute,
                paddingTop, periodHeight, overflowSpace);
            float bottom = minuteToY(endMin, currentPeriod.startMinute, currentPeriod.endMinute,
                paddingTop, periodHeight, overflowSpace);
            if (y >= top && y <= bottom) return item;
        }
        return null;
    }

    private static int minuteOfDay(long ms) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ms);
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private String ellipsize(String text, float maxWidth, Paint paint) {
        if (text == null || maxWidth <= 0 || paint.measureText(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String suffix = "...";
        float suffixWidth = paint.measureText(suffix);
        if (suffixWidth >= maxWidth) return suffix;
        int end = text.length();
        while (end > 0 && paint.measureText(text, 0, end) + suffixWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + suffix;
    }

    private String getTaskBarText(TimelineItem item) {
        return item.title == null ? "" : item.title;
    }

    private String getTaskMetaText(TimelineItem item) {
        if (!item.running && item.actualMinutes > 0) {
            return item.actualMinutes + getResources().getString(R.string.s_minute_unit);
        }
        return item.focusMinutes + getResources().getString(R.string.s_minute_unit);
    }

    private void drawTaskText(Canvas canvas, TimelineItem item, boolean isCompleted,
                              float barX, float barW, float barTop, float barBottom) {
        Paint textPaint = isCompleted ? mBarTextDonePaint : mBarTextPaint;
        Paint metaPaint = isCompleted ? mBarMetaTextDonePaint : mBarMetaTextPaint;

        float availableHeight = barBottom - barTop - mTaskTextPadding * 2;
        float titleHeight = textPaint.getTextSize();
        if (availableHeight < titleHeight) return;

        float textX = barX + mTaskTextPadding + (item.running ? mTaskStatusStripWidth : 0);
        float maxTextWidth = Math.max(0, barX + barW - textX - mTaskTextPadding);
        if (maxTextWidth <= 0) return;

        String title = ellipsize(getTaskBarText(item), maxTextWidth, textPaint);
        String meta = getTaskMetaText(item);
        boolean canDrawMeta = !meta.isEmpty()
            && availableHeight >= textPaint.getTextSize()
            + metaPaint.getTextSize() + mBarTextLineGap;

        if (!canDrawMeta) {
            Paint.FontMetrics titleFm = textPaint.getFontMetrics();
            float titleY = (barTop + barBottom - titleFm.ascent - titleFm.descent) / 2;
            canvas.drawText(title, textX, titleY, textPaint);
            return;
        }

        Paint.FontMetrics titleFm = textPaint.getFontMetrics();
        Paint.FontMetrics metaFm = metaPaint.getFontMetrics();
        float blockHeight = (titleFm.descent - titleFm.ascent)
            + mBarTextLineGap + (metaFm.descent - metaFm.ascent);
        float blockTop = barTop + (barBottom - barTop - blockHeight) / 2;
        float titleY = blockTop - titleFm.ascent;
        float metaY = titleY + titleFm.descent + mBarTextLineGap - metaFm.ascent;

        canvas.drawText(title, textX, titleY, textPaint);
        canvas.drawText(ellipsize(meta, maxTextWidth, metaPaint), textX, metaY, metaPaint);
    }

    private static float minuteToY(int minute, int rangeStart, int rangeEnd,
                                   int paddingTop, float periodHeight, float overflowSpace) {
        if (minute <= rangeEnd) {
            return paddingTop + (float) (minute - rangeStart) / (rangeEnd - rangeStart) * periodHeight;
        }
        int overflowMinute = Math.min(minute - rangeEnd, OVERFLOW_MINUTES);
        return paddingTop + periodHeight + (float) overflowMinute / OVERFLOW_MINUTES * overflowSpace;
    }

    public interface OnTimelineItemClickListener {
        void onTimelineItemClick(TimelineItem item);
    }
}
