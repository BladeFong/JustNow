package com.nearby.justnow.ui.main;

import android.view.Gravity;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.store.UserStore;

/**
 * 多用户切换管理器——封装平板端 Toolbar 用户名显示、下拉切换、用户创建对话框。
 * <p>
 * 仅平板端（is_tablet=true）生效；手机端 setup() 为空操作。
 * 首次启动无用户时弹出强制创建对话框。
 */
public class UserSwitcherManager {

    private final AppCompatActivity mActivity;
    private final UserStore mUserStore;
    @Nullable
    private TextView mTvUser;
    @Nullable
    private Runnable mOnUserChanged;

    public UserSwitcherManager(AppCompatActivity activity) {
        mActivity = activity;
        mUserStore = ((JustNowApplication) activity.getApplication()).getUserStore();
    }

    /** 设置用户变更回调（切换或创建后触发，用于重建界面刷新数据）。 */
    public void setOnUserChangedListener(@Nullable Runnable onUserChanged) {
        mOnUserChanged = onUserChanged;
    }

    /** 装配用户切换入口。仅平板端有效。首次启动无用户时弹出强制创建对话框。 */
    public void setup() {
        if (!mActivity.getResources().getBoolean(R.bool.is_tablet)) return;
        if (!mUserStore.hasUsers()) {
            showCreateUserDialog(true);
            return;
        }
        attachToToolbar();
        mTvUser.setOnClickListener(v -> showUserSwitchMenu());
    }

    // ---- 内部实现 ----

    private void attachToToolbar() {
        MaterialToolbar toolbar = mActivity.findViewById(R.id.toolbar);
        if (toolbar == null) return;

        // 已有且未分离：只刷新文字，不重复添加
        if (mTvUser != null && mTvUser.getParent() != null) {
            refreshDisplay();
            return;
        }

        mTvUser = new TextView(mActivity);
        mTvUser.setId(View.generateViewId());
        mTvUser.setTextAppearance(R.style.TextAppearance_JustNow_Caption);
        mTvUser.setTextColor(ContextCompat.getColor(mActivity, R.color.white));
        mTvUser.setPadding(0, 0, 8, 0);
        mTvUser.setClickable(true);
        mTvUser.setFocusable(true);

        MaterialToolbar.LayoutParams lp = new MaterialToolbar.LayoutParams(
            MaterialToolbar.LayoutParams.WRAP_CONTENT,
            MaterialToolbar.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        lp.setMarginEnd(8);
        toolbar.addView(mTvUser, lp);

        refreshDisplay();
    }

    private void refreshDisplay() {
        if (mTvUser == null) return;
        UserStore.UserInfo info = mUserStore.getUserInfo(mUserStore.getCurrentUserId());
        if (info != null) {
            mTvUser.setText(info.name + " ▾");
        }
    }

    private void showUserSwitchMenu() {
        if (mTvUser == null) return;
        PopupMenu popup = new PopupMenu(mActivity, mTvUser);
        java.util.List<UserStore.UserInfo> users = mUserStore.getAllUsers();
        long currentId = mUserStore.getCurrentUserId();

        // 维护 menu item ID → 用户列表索引的映射，
        // 避免 long userId → int itemId 截断导致切错用户
        final java.util.Map<Integer, Integer> menuIdToIndex = new java.util.HashMap<>();
        for (int i = 0; i < users.size(); i++) {
            UserStore.UserInfo user = users.get(i);
            String label = user.name;
            if (user.userId == currentId) {
                label += " ✓";
            }
            popup.getMenu().add(0, i, i, label);
            menuIdToIndex.put(i, i);
        }
        popup.getMenu().add(1, -1, users.size(), R.string.s_add_user);
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == -1) {
                showCreateUserDialog(false);
            } else {
                Integer index = menuIdToIndex.get(item.getItemId());
                if (index != null && index < users.size()) {
                    long userId = users.get(index).userId;
                    if (userId != currentId) {
                        switchToUser(userId);
                    }
                }
            }
            return true;
        });
        popup.show();
    }

    private void showCreateUserDialog(boolean forced) {
        int themeColor = MainFragment.getGlobalThemeColor(mActivity);

        EditText input = new EditText(mActivity);
        input.setHint(R.string.s_add_user_hint);
        input.setSingleLine(true);
        // 焦点下划线跟随主题色
        input.setBackgroundTintList(new android.content.res.ColorStateList(
            new int[][] {
                new int[] {android.R.attr.state_focused},
                new int[] {}
            },
            new int[] { themeColor, 0xFFBDBDBD }));

        // 水平 padding 收窄
        int hp = (int) (mActivity.getResources().getDisplayMetrics().density * 24);
        LinearLayout wrapper = new LinearLayout(mActivity);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(hp, 0, hp, 0);
        wrapper.addView(input);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(mActivity)
            .setTitle(R.string.s_add_user)
            .setView(wrapper);
        if (forced) {
            builder.setCancelable(false);
            builder.setPositiveButton(R.string.s_confirm, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = mActivity.getString(R.string.s_default_user_name);
                createAndSwitchUser(name);
            });
        } else {
            builder.setPositiveButton(R.string.s_confirm, (d, w) -> {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) name = mActivity.getString(R.string.s_default_user_name);
                createAndSwitchUser(name);
            });
            builder.setNegativeButton(R.string.s_cancel, null);
        }
        builder.show();
    }

    private void createAndSwitchUser(String name) {
        JustNowApplication app = (JustNowApplication) mActivity.getApplication();
        app.addUser(name, () -> mActivity.runOnUiThread(() -> {
            // 首次创建用户后重新装配 Toolbar 入口（此时 UserStore 已有数据）
            setup();
            notifyUserChanged();
        }));
    }

    private void switchToUser(long userId) {
        JustNowApplication app = (JustNowApplication) mActivity.getApplication();
        app.switchToUser(userId);
        refreshDisplay();
        notifyUserChanged();
    }

    private void notifyUserChanged() {
        if (mOnUserChanged != null) {
            mOnUserChanged.run();
        }
    }
}
