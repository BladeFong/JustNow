package com.nearby.justnow.ui.base;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.ui.main.MainViewModel;
import com.nearby.justnow.ui.period.PeriodConfigViewModel;
import com.nearby.justnow.ui.tagmanage.TagManageViewModel;
import com.nearby.justnow.ui.reminderdetail.ReminderDetailViewModel;
import com.nearby.justnow.ui.taskschedule.TaskScheduleViewModel;
import com.nearby.justnow.ui.tagmanage.UnusedTagViewModel;
import com.nearby.justnow.ui.quadrant.QuadrantTaskListViewModel;
import com.nearby.justnow.ui.taskinput.TaskInputViewModel;

/**
 * ViewModel 工厂 — 注入 Application 依赖
 */
public class ViewModelFactory implements ViewModelProvider.Factory {

    private final JustNowApplication mApp;

    public ViewModelFactory(JustNowApplication app) {
        this.mApp = app;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass == TaskInputViewModel.class) {
            return (T) new TaskInputViewModel(mApp);
        }
        if (modelClass == MainViewModel.class) {
            return (T) new MainViewModel(mApp);
        }
        if (modelClass == PeriodConfigViewModel.class) {
            return (T) new PeriodConfigViewModel(mApp);
        }
        if (modelClass == TaskScheduleViewModel.class) {
            return (T) new TaskScheduleViewModel(mApp);
        }
        if (modelClass == TagManageViewModel.class) {
            return (T) new TagManageViewModel(mApp);
        }
        if (modelClass == UnusedTagViewModel.class) {
            return (T) new UnusedTagViewModel(mApp);
        }
        if (modelClass == ReminderDetailViewModel.class) {
            return (T) new ReminderDetailViewModel(mApp);
        }
        if (modelClass == QuadrantTaskListViewModel.class) {
            return (T) new QuadrantTaskListViewModel(mApp);
        }
        throw new IllegalArgumentException("未知 ViewModel: " + modelClass.getName());
    }
}
