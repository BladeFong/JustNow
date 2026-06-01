package com.nearby.justnow.ui.main;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.ui.engine.DisplayItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 任务列表 Adapter — 支持 DisplayItem、标签点击/色值状态、执行中状态
 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.ViewHolder> {

    private static final int[] sQuadrantColors = {
        Color.parseColor("#C62828"), Color.parseColor("#E65100"),
        Color.parseColor("#2E7D32"), Color.parseColor("#546E7A")
    };

    private static final int EXECUTING_BG_COLOR = Color.parseColor("#F0F4C3");

    private static final DiffUtil.ItemCallback<DisplayItem> sDiffCallback =
        new DiffUtil.ItemCallback<DisplayItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull DisplayItem oldItem, @NonNull DisplayItem newItem) {
                return oldItem.task.id == newItem.task.id;
            }

            @Override
            public boolean areContentsTheSame(@NonNull DisplayItem oldItem, @NonNull DisplayItem newItem) {
                return oldItem.task.quadrant == newItem.task.quadrant
                    && oldItem.task.focusMinutes == newItem.task.focusMinutes
                    && oldItem.task.isArchived == newItem.task.isArchived
                    && oldItem.sortWeight == newItem.sortWeight
                    && oldItem.task.executingStartMs == newItem.task.executingStartMs
                    && oldItem.task.executingEndMs == newItem.task.executingEndMs
                    && oldItem.task.content.equals(newItem.task.content)
                    && Objects.equals(oldItem.tag, newItem.tag);
            }
        };

    private final AsyncListDiffer<DisplayItem> mDiffer = new AsyncListDiffer<>(this, sDiffCallback);

    private OnTaskClickListener mTaskListener;
    private OnTagClickListener mTagListener;
    private OnTagLongClickListener mTagLongListener;

    /** 当前正在筛选的标签 ID，-1 表示未筛选 */
    private long mFilterTagId = -1;

    /** 多标签筛选是否激活（非空 = 多标签筛选态，单点标签无功能） */
    private boolean mMultiFilterActive = false;

    /** 当前优先标签 ID 集合 */
    private Set<Long> mPriorityTagIds = Collections.emptySet();

    public interface OnTaskClickListener {
        void onTaskClick(DisplayItem item);
    }

    public interface OnTagClickListener {
        void onTagClick(TagEntity tag);
    }

    /** 长按标签 → 触发多选标签筛选覆盖层（仅在非筛选态生效） */
    public interface OnTagLongClickListener {
        void onTagLongClick(TagEntity tag);
    }

    public void setOnTaskClickListener(OnTaskClickListener listener) {
        this.mTaskListener = listener;
    }

    public void setOnTagClickListener(OnTagClickListener listener) {
        this.mTagListener = listener;
    }

    public void setOnTagLongClickListener(OnTagLongClickListener listener) {
        this.mTagLongListener = listener;
    }

    public void setPriorityTagIds(Set<Long> priorityTagIds) {
        this.mPriorityTagIds = priorityTagIds != null ? priorityTagIds : Collections.emptySet();
        notifyDataSetChanged();
    }

    public void setItems(List<DisplayItem> items) {
        mDiffer.submitList(items != null ? items : new ArrayList<>());
    }

    public void setFilterTagId(long filterTagId) {
        this.mFilterTagId = filterTagId;
        notifyDataSetChanged();
    }

    public void setMultiFilterActive(boolean active) {
        this.mMultiFilterActive = active;
        notifyDataSetChanged();
    }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DisplayItem item = mDiffer.getCurrentList().get(position);

        // ---- 标签 ----
        if (item.tag != null) {
            holder.tagView.setVisibility(View.VISIBLE);
            boolean isPriority = mPriorityTagIds.contains(item.tag.id);
            holder.tagView.setText((isPriority ? "★" : "#") + item.tag.name);
            // 超链接交互：正常态蓝色无下划线，激活态深色+下划线；优先标签金色
            if (mFilterTagId == item.tag.id) {
                holder.tagView.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.tag_active));
                holder.tagView.setPaintFlags(
                    holder.tagView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            } else if (isPriority) {
                holder.tagView.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.tag_priority));
                holder.tagView.setPaintFlags(
                    holder.tagView.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            } else {
                holder.tagView.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.tag_normal));
                holder.tagView.setPaintFlags(
                    holder.tagView.getPaintFlags() & ~Paint.UNDERLINE_TEXT_FLAG);
            }
            holder.tagView.setOnClickListener(v -> {
                if (mMultiFilterActive) return; // 多标签筛选态，单点无功能
                if (mTagListener != null) mTagListener.onTagClick(item.tag);
            });
            holder.tagView.setOnLongClickListener(v -> {
                if (mMultiFilterActive) return true; // 多标签筛选态，长按无功能
                if (mFilterTagId < 0 && mTagLongListener != null) {
                    mTagLongListener.onTagLongClick(item.tag);
                }
                return true;
            });
        } else {
            holder.tagView.setVisibility(View.GONE);
            holder.tagView.setOnClickListener(null);
            holder.tagView.setOnLongClickListener(null);
        }

        // ---- 任务内容 ----
        holder.content.setText(item.task.content);
        holder.content.setOnClickListener(v -> {
            if (mTaskListener != null) mTaskListener.onTaskClick(item);
        });

        // ---- 专注时长标签（始终显示，无专注时长时显示"琐碎"） ----
        android.content.Context ctx = holder.itemView.getContext();
        if (item.task.focusMinutes > 0) {
            holder.focusBadge.setText(item.task.focusMinutes + ctx.getString(R.string.s_minute_unit));
        } else {
            holder.focusBadge.setText(ctx.getString(R.string.s_chore_label));
        }

        int colorIdx = Math.min(item.task.quadrant, 3);
        holder.quadrantColor.setBackgroundColor(sQuadrantColors[colorIdx]);

        // ---- 执行中状态 ----
        boolean isExecuting = item.task.executingStartMs > 0 && item.task.executingEndMs == 0;
        if (isExecuting) {
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.llTaskItem.setBackgroundColor(EXECUTING_BG_COLOR);
            startProgressAnimation(holder);
        } else {
            holder.progressBar.setVisibility(View.GONE);
            holder.llTaskItem.setBackgroundColor(Color.TRANSPARENT);
            stopProgressAnimation(holder);
        }

        // 整个条目点击也触发任务点击
        holder.itemView.setOnClickListener(v -> {
            if (mTaskListener != null) mTaskListener.onTaskClick(item);
        });
    }

    private void startProgressAnimation(ViewHolder holder) {
        if (holder.animator != null && holder.animator.isRunning()) return;

        int viewWidth = holder.progressBar.getParent() != null
            ? ((View) holder.progressBar.getParent()).getWidth() : 300;
        float maxX = viewWidth * 0.6f;

        holder.animator = ValueAnimator.ofFloat(0, maxX);
        holder.animator.setDuration(1500);
        holder.animator.setRepeatCount(ValueAnimator.INFINITE);
        holder.animator.setRepeatMode(ValueAnimator.RESTART);
        holder.animator.addUpdateListener(anim -> {
            holder.progressBar.setTranslationX((float) anim.getAnimatedValue());
            float progress = (float) anim.getAnimatedFraction();
            holder.progressBar.setAlpha(0.4f + 0.4f * progress);
        });
        holder.animator.start();
    }

    private void stopProgressAnimation(ViewHolder holder) {
        if (holder.animator != null) {
            holder.animator.cancel();
            holder.animator = null;
        }
        holder.progressBar.setTranslationX(0);
        holder.progressBar.setAlpha(1);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        stopProgressAnimation(holder);
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View quadrantColor, progressBar, llTaskItem;
        TextView tagView, content, focusBadge;
        ValueAnimator animator;

        ViewHolder(View v) {
            super(v);
            quadrantColor = v.findViewById(R.id.v_quadrant_color);
            progressBar = v.findViewById(R.id.v_progress_bar);
            tagView = v.findViewById(R.id.tv_tag);
            content = v.findViewById(R.id.tv_task_content);
            focusBadge = v.findViewById(R.id.tv_focus_badge);
            llTaskItem = v.findViewById(R.id.ll_task_item);
        }
    }
}
