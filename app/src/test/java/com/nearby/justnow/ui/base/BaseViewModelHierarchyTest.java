package com.nearby.justnow.ui.base;

import static org.junit.Assert.assertTrue;

import com.nearby.justnow.ui.main.MainViewModel;
import com.nearby.justnow.ui.reminderdetail.ReminderDetailViewModel;

import org.junit.Test;

/**
 * ViewModel 继承关系回归测试（F4 重构）：
 * - BaseTaskViewModel extends BaseViewModel
 * - MainViewModel extends BaseTaskViewModel
 * - ReminderDetailViewModel extends BaseTaskViewModel
 * 防止后续重构误改继承链。
 */
public class BaseViewModelHierarchyTest {

    @Test
    public void baseTaskViewModelExtendsBaseViewModel() {
        assertTrue("BaseTaskViewModel 应继承 BaseViewModel",
            BaseViewModel.class.isAssignableFrom(BaseTaskViewModel.class));
    }

    @Test
    public void mainViewModelExtendsBaseTaskViewModel() {
        assertTrue("MainViewModel 应继承 BaseTaskViewModel",
            BaseTaskViewModel.class.isAssignableFrom(MainViewModel.class));
    }

    @Test
    public void reminderDetailViewModelExtendsBaseTaskViewModel() {
        assertTrue("ReminderDetailViewModel 应继承 BaseTaskViewModel",
            BaseTaskViewModel.class.isAssignableFrom(ReminderDetailViewModel.class));
    }
}
