package com.nearby.justnow.ui.quadrant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.R;
import com.nearby.justnow.ui.engine.DisplayItem;
import com.nearby.justnow.ui.engine.FocusDurationOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * 单象限任务列表 Adapter — 支持多选模式。
 * 列对齐由 GridLayoutManager + 布局固定 dp 列宽保证，不在此处做两遍测量。
 */
public class QuadrantTaskListAdapter extends RecyclerView.Adapter<QuadrantTaskListAdapter.ViewHolder> {

    private final List<DisplayItem> mItems = new ArrayList<>();
    private boolean mSelectionMode = false;
    private QuadrantTaskListViewModel mViewModel;

    /** 单击回调（正常模式） */
    public interface OnItemClickListener {
        void onItemClick(DisplayItem item);
    }

    /** 长按回调 */
    public interface OnItemLongClickListener {
        void onItemLongClick(DisplayItem item);
    }

    private OnItemClickListener mOnItemClickListener;
    private OnItemLongClickListener mOnItemLongClickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        mOnItemLongClickListener = listener;
    }

    public void setViewModel(QuadrantTaskListViewModel viewModel) {
        mViewModel = viewModel;
    }

    public void setItems(List<DisplayItem> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean selectionMode) {
        if (mSelectionMode != selectionMode) {
            mSelectionMode = selectionMode;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quadrant_task, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DisplayItem item = mItems.get(position);

        // 标签名 — 使用固定色值，与主界面 TaskAdapter 保持一致
        if (item.tag != null) {
            holder.mTvTagName.setVisibility(View.VISIBLE);
            holder.mTvTagName.setText(holder.itemView.getContext().getString(
                    R.string.s_tag_name_format, item.tag.name));
            holder.mTvTagName.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(), R.color.tag_normal));
        } else {
            holder.mTvTagName.setVisibility(View.GONE);
        }

        // 标题
        holder.mTvContent.setText(item.task.content != null ? item.task.content : "");
        holder.mTvContent.setTextAppearance(R.style.TextAppearance_JustNow_Body);

        // 专注时长
        int fm = item.task.focusMinutes;
        if (fm > 0) {
            holder.mTvDuration.setVisibility(View.VISIBLE);
            holder.mTvDuration.setText(FocusDurationOptions.format(
                    holder.itemView.getContext().getResources(), fm));
        } else {
            holder.mTvDuration.setVisibility(View.GONE);
        }

        // CheckBox 状态
        holder.mCbSelect.setVisibility(mSelectionMode ? View.VISIBLE : View.GONE);
        if (mViewModel != null && mSelectionMode) {
            holder.mCbSelect.setChecked(mViewModel.isSelected(item.task.id));
        }

        // 点击
        holder.itemView.setOnClickListener(v -> {
            if (mSelectionMode && mViewModel != null) {
                mViewModel.toggleSelection(item.task.id);
            } else if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(item);
            }
        });

        // 长按
        holder.itemView.setOnLongClickListener(v -> {
            if (mOnItemLongClickListener != null) {
                mOnItemLongClickListener.onItemLongClick(item);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox mCbSelect;
        TextView mTvTagName;
        TextView mTvContent;
        TextView mTvDuration;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mCbSelect = itemView.findViewById(R.id.cb_select);
            mTvTagName = itemView.findViewById(R.id.tv_tag_name);
            mTvContent = itemView.findViewById(R.id.tv_content);
            mTvDuration = itemView.findViewById(R.id.tv_duration);
        }
    }
}
