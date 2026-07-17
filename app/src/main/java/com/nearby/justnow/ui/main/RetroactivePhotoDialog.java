package com.nearby.justnow.ui.main;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskEntity;

import java.util.List;

/**
 * 延迟补拍任务列表弹窗
 */
public class RetroactivePhotoDialog extends Dialog {

    public interface OnRetroactiveClickListener {
        void onCapturePhoto(TaskEntity task);
    }

    private final List<TaskEntity> mTasks;
    private final OnRetroactiveClickListener mListener;
    private RecyclerView mRecyclerView;
    private RetroactiveAdapter mAdapter;

    public RetroactivePhotoDialog(@NonNull Context context, List<TaskEntity> tasks, OnRetroactiveClickListener listener) {
        super(context);
        mTasks = tasks;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_retroactive_list);

        // 设置全屏/高宽属性
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(lp);
        }

        mRecyclerView = findViewById(R.id.rv_retroactive_tasks);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new RetroactiveAdapter();
        mRecyclerView.setAdapter(mAdapter);

        findViewById(R.id.btn_close_retroactive).setOnClickListener(v -> dismiss());
    }

    private class RetroactiveAdapter extends RecyclerView.Adapter<RetroactiveViewHolder> {

        @NonNull
        @Override
        public RetroactiveViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext()).inflate(R.layout.item_retroactive_task, parent, false);
            return new RetroactiveViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RetroactiveViewHolder holder, int position) {
            TaskEntity item = mTasks.get(position);

            // 1. 根据象限绑定四象限主题色竖条
            int accentColor;
            switch (item.quadrant) {
                case 0:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_important);
                    break;
                case 1:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_urgent_not_important);
                    break;
                case 2:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_important);
                    break;
                case 3:
                default:
                    accentColor = ContextCompat.getColor(getContext(), R.color.quadrant_not_urgent_not_important);
                    break;
            }
            holder.vQuadrantBar.setBackgroundColor(accentColor);

            // 2. 绑定卡通图标 (40dp x 40dp，纯透明底，无背景框，占两行高度)
            holder.ivIcon.setImageDrawable(null);
            if (item.iconName != null && !item.iconName.isEmpty()) {
                String resName = "ic_activity_" + item.iconName;
                int resId = getContext().getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
                if (resId != 0) {
                    holder.ivIcon.setImageResource(resId);
                }
            }

            // 3. 设置任务名
            holder.tvTitle.setText(item.content);

            // 4. 点击整行拉起拍照逻辑
            holder.itemView.setOnClickListener(v -> {
                if (mListener != null) {
                    mListener.onCapturePhoto(item);
                }
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }
    }

    private static class RetroactiveViewHolder extends RecyclerView.ViewHolder {
        View vQuadrantBar;
        ImageView ivIcon;
        TextView tvTitle;

        public RetroactiveViewHolder(@NonNull View itemView) {
            super(itemView);
            vQuadrantBar = itemView.findViewById(R.id.v_retroactive_quadrant_bar);
            ivIcon = itemView.findViewById(R.id.iv_retroactive_task_icon);
            tvTitle = itemView.findViewById(R.id.tv_retroactive_task_title);
        }
    }
}
