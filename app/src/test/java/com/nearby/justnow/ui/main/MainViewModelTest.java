package com.nearby.justnow.ui.main;

import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.util.DateUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

/**
 * MainViewModel 测试 — 重构后方法存在性验证。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MainViewModelTest {

    // ============================================================
    // 重构后方法存在性验证
    // ============================================================

    @Test
    public void recomputeSync_methodExists() throws Exception {
        Method method = MainViewModel.class.getDeclaredMethod("recomputeSync");
        assertNotNull("recomputeSync 方法应存在", method);
        assertTrue("recomputeSync 应为 private",
                Modifier.isPrivate(method.getModifiers()));
    }

    @Test
    public void computeQuadrantOverviewSync_methodExists() throws Exception {
        Method method = MainViewModel.class.getDeclaredMethod("computeQuadrantOverviewSync");
        assertNotNull("computeQuadrantOverviewSync 方法应存在",
                method);
        assertTrue("computeQuadrantOverviewSync 应为 private",
                Modifier.isPrivate(method.getModifiers()));
    }

    @Test
    public void computeSchedulePriorityIds_ignoresPostponedUntil() {
        long nowMs = System.currentTimeMillis();
        long todayStartMs = DateUtils.todayStartMs();
        int nowMinute = (int) ((nowMs - todayStartMs) / 60000L);

        TaskScheduleEntity originalWindow = newSchedule(1, Math.max(0, nowMinute - 5));
        originalWindow.postponedUntilMs = nowMs + 30 * 60000L;

        int expiredOriginalWindowMinute = nowMinute >= 60 ? nowMinute - 60 : nowMinute + 31;
        TaskScheduleEntity postponedOnlyWindow = newSchedule(2, expiredOriginalWindowMinute);
        postponedOnlyWindow.postponedUntilMs = nowMs;

        Set<Long> ids = MainViewModel.computeSchedulePriorityIds(
                Arrays.asList(originalWindow, postponedOnlyWindow));

        assertTrue("原始 30 分钟窗口内仍应优先", ids.contains(1L));
        assertFalse("推迟时间戳不应单独制造优先窗口", ids.contains(2L));
    }

    @Test
    public void computeSchedulePriorityIds_emptyInput_returnsEmptySet() {
        assertTrue(MainViewModel.computeSchedulePriorityIds(null).isEmpty());
        assertTrue(MainViewModel.computeSchedulePriorityIds(Collections.emptyList()).isEmpty());
    }

    private static TaskScheduleEntity newSchedule(long taskId, int scheduledTime) {
        TaskScheduleEntity schedule = new TaskScheduleEntity();
        schedule.id = taskId;
        schedule.taskId = taskId;
        schedule.scheduleType = TaskScheduleEntity.TYPE_ONCE;
        schedule.scheduleValue = DateUtils.todayStartMs();
        schedule.scheduledTime = scheduledTime;
        schedule.enabled = true;
        return schedule;
    }
}
