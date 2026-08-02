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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nearby.justnow.R;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskPhotoRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用任务拍照列表弹窗（当天已完成 + 进行中任务，每任务最多 5 张，按时间倒序）
 */
public class TaskPhotoListDialog extends Dialog {

    public interface OnTaskPhotoClickListener {
        void onCapturePhoto(TaskEntity task);
    }

    private final TaskPhotoRepository mPhotoRepository;
    private final long mTodayStartMs;
    private final long mTodayEndMs;
    private final OnTaskPhotoClickListener mListener;
    private RecyclerView mRecyclerView;
    private TaskPhotoListAdapter mAdapter;

    public TaskPhotoListDialog(@NonNull Context context,
                               @NonNull TaskPhotoRepository photoRepository,
                               long todayStartMs, long todayEndMs,
                               @NonNull OnTaskPhotoClickListener listener) {
        super(context);
        mPhotoRepository = photoRepository;
        mTodayStartMs = todayStartMs;
        mTodayEndMs = todayEndMs;
        mListener = listener;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_task_photo_list);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = WindowManager.LayoutParams.MATCH_PARENT;
            window.setAttributes(lp);
        }

        setCanceledOnTouchOutside(true);

        mRecyclerView = findViewById(R.id.rv_task_photo_list);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new TaskPhotoListAdapter();
        mRecyclerView.setAdapter(mAdapter);

        findViewById(R.id.btn_close_task_photo_list).setOnClickListener(v -> dismiss());

        loadTasks();
    }

    private void loadTasks() {
        AppDatabase.execute(() -> {
            List<TaskEntity> tasks = mPhotoRepository.getTodayTasksAvailableForPhoto(
                mTodayStartMs, mTodayEndMs);
            // isChildTask 过滤
            com.nearby.justnow.JustNowApplication app =
                (com.nearby.justnow.JustNowApplication) getContext().getApplicationContext();
            java.util.Iterator<TaskEntity> it = tasks.iterator();
            while (it.hasNext()) {
                if (!app.isChildTask(it.next())) it.remove();
            }
            mRecyclerView.post(() -> {
                mAdapter.setTasks(tasks);
                if (tasks.isEmpty()) {
                    Toast.makeText(getContext(),
                        R.string.s_no_tasks_available_for_photo,
                        Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            });
        });
    }

    private class TaskPhotoListAdapter extends RecyclerView.Adapter<TaskPhotoListViewHolder> {

        private List<TaskEntity> mTasks = new ArrayList<>();

        void setTasks(List<TaskEntity> tasks) {
            mTasks = tasks != null ? tasks : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public TaskPhotoListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(getContext())
                .inflate(R.layout.item_task_photo_list, parent, false);
            return new TaskPhotoListViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskPhotoListViewHolder holder, int position) {
            TaskEntity item = mTasks.get(position);

            // 象限色条
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
            holder.mVQuadrantBar.setBackgroundColor(accentColor);

            // 活动图标
            holder.mIvIcon.setImageDrawable(null);
            if (item.iconName != null && !item.iconName.isEmpty()) {
                String resName = "ic_activity_" + item.iconName;
                int resId = getContext().getResources().getIdentifier(
                    resName, "drawable", getContext().getPackageName());
                if (resId != 0) {
                    holder.mIvIcon.setImageResource(resId);
                }
            }

            holder.mTvTitle.setText(item.content);

            // 后台查询张数
            AppDatabase.execute(() -> {
                int count = mPhotoRepository.getPhotoCountForTaskInRange(
                    item.id, mTodayStartMs, mTodayEndMs);
                holder.mTvPhotoCount.post(() -> {
                    holder.mTvPhotoCount.setText(count + "/5");
                    if (count >= 5) {
                        holder.mTvPhotoCount.setTextColor(
                            ContextCompat.getColor(getContext(), R.color.quadrant_urgent_important));
                    } else {
                        holder.mTvPhotoCount.setTextColor(
                            ContextCompat.getColor(getContext(), R.color.text_secondary));
                    }
                });
            });

            // 点击：后台检查张数后拉起相机或 Toast
            holder.itemView.setOnClickListener(v -> {
                AppDatabase.execute(() -> {
                    boolean reached = mPhotoRepository.isPhotoLimitReached(item.id);
                    holder.itemView.post(() -> {
                        if (reached) {
                            Toast.makeText(getContext(),
                                R.string.s_photo_limit_reached,
                                Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (mListener != null) {
                            mListener.onCapturePhoto(item);
                        }
                        dismiss();
                    });
                });
            });
        }

        @Override
        public int getItemCount() {
            return mTasks.size();
        }
    }

    private static class TaskPhotoListViewHolder extends RecyclerView.ViewHolder {
        View mVQuadrantBar;
        ImageView mIvIcon;
        TextView mTvTitle;
        TextView mTvPhotoCount;

        TaskPhotoListViewHolder(@NonNull View itemView) {
            super(itemView);
            mVQuadrantBar = itemView.findViewById(R.id.v_task_photo_quadrant_bar);
            mIvIcon = itemView.findViewById(R.id.iv_task_photo_icon);
            mTvTitle = itemView.findViewById(R.id.tv_task_photo_title);
            mTvPhotoCount = itemView.findViewById(R.id.tv_photo_count);
        }
    }
}
