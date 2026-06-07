package com.nearby.justnow.ui.taskinput;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.ui.base.ViewModelFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 笔记分享编辑器 BottomSheet
 */
public class TaskInputNoteShareSheet extends BottomSheetDialogFragment {

    private final Consumer<List<TaskNoteShare>> mOnSaved;
    private final List<TaskNoteShare> mShares = new ArrayList<>();
    private NoteShareAdapter mAdapter;
    private RecyclerView mRvShares;

    public TaskInputNoteShareSheet(Consumer<List<TaskNoteShare>> onSaved) {
        mOnSaved = onSaved;
    }

    public void setExistingShares(List<TaskNoteShare> shares) {
        mShares.clear();
        if (shares != null) {
            mShares.addAll(shares);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        View view = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_note_share_editor, null);
        dialog.setContentView(view);

        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        dialog.setOnShowListener(d -> {
            FrameLayout bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
                bottomSheet.requestLayout();
            }
        });

        mRvShares = view.findViewById(R.id.rv_shares);
        Button btnCancel = view.findViewById(R.id.btn_cancel);
        Button btnConfirm = view.findViewById(R.id.btn_confirm);

        mAdapter = new NoteShareAdapter(mShares,
            item -> {
                mShares.remove(item);
                mAdapter.notifyDataSetChanged();
            });
        mRvShares.setLayoutManager(new LinearLayoutManager(requireContext()));
        mRvShares.setAdapter(mAdapter);

        btnCancel.setOnClickListener(v -> dismiss());
        btnConfirm.setOnClickListener(v -> {
            if (mOnSaved != null) {
                for (int i = 0; i < mShares.size(); i++) {
                    mShares.get(i).orderIndex = i;
                }
                mOnSaved.accept(new ArrayList<>(mShares));
            }
            dismiss();
        });

        // 消费外部捕获预填项
        TaskInputViewModel vm = new ViewModelProvider(requireActivity(),
                new ViewModelFactory((JustNowApplication) requireActivity().getApplication()))
                .get(TaskInputViewModel.class);
        TaskNoteShare prefill = vm.consumePendingNoteSharePrefill();
        if (prefill != null) {
            mShares.add(0, prefill);
            mAdapter.notifyItemInserted(0);
            mRvShares.post(() -> mRvShares.scrollToPosition(0));
        }

        return dialog;
    }

    private static class NoteShareAdapter extends RecyclerView.Adapter<NoteShareAdapter.Holder> {

        private final List<TaskNoteShare> mItems;
        private final Consumer<TaskNoteShare> mOnDelete;

        NoteShareAdapter(List<TaskNoteShare> items,
                         Consumer<TaskNoteShare> onDelete) {
            mItems = items;
            mOnDelete = onDelete;
        }

        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note_share, parent, false);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int pos) {
            TaskNoteShare share = mItems.get(pos);

            String displayTitle = share.hint != null && !share.hint.isEmpty()
                ? share.hint : (share.deepLink != null ? share.deepLink : "");
            holder.tvHint.setText(displayTitle);

            String linkDisplay = share.deepLink != null ? share.deepLink : "";
            holder.tvLink.setText(linkDisplay);
            holder.tvLink.setVisibility(linkDisplay.isEmpty() ? View.GONE : View.VISIBLE);

            holder.btnDelete.setOnClickListener(v -> {
                int idx = holder.getAdapterPosition();
                if (idx != RecyclerView.NO_POSITION && mOnDelete != null) {
                    mOnDelete.accept(mItems.get(idx));
                }
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            TextView tvHint, tvLink;
            Button btnDelete;

            Holder(View v) {
                super(v);
                tvHint = v.findViewById(R.id.tv_hint);
                tvLink = v.findViewById(R.id.tv_link);
                btnDelete = v.findViewById(R.id.btn_delete);
            }
        }
    }
}
