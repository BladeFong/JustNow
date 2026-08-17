package com.nearby.justnow.ui.main;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskExecutionEntity;
import com.nearby.justnow.data.entity.TaskScheduleEntity;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.data.repository.TaskScheduleRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TimelineBuilderTest {

    private TaskRepository mTaskRepo;
    private TaskScheduleRepository mScheduleRepo;
    private TimelineBuilder mBuilder;

    @Before
    public void setUp() {
        mTaskRepo = mock(TaskRepository.class);
        mScheduleRepo = mock(TaskScheduleRepository.class);
        when(mScheduleRepo.getAllEnabledSchedulesSync()).thenReturn(Collections.emptyList());
        mBuilder = new TimelineBuilder(mTaskRepo, mScheduleRepo);
    }

    @Test
    public void build_normalExecutionStatus0_includedInTimeline() {
        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.content = "专注任务";
        task.focusMinutes = 30;
        task.quadrant = 0;

        TaskExecutionEntity exec = new TaskExecutionEntity();
        exec.id = 100L;
        exec.taskId = 1L;
        exec.startMs = 1000L;
        exec.endMs = 2800000L;
        exec.actualMinutes = 25;
        exec.status = 0; // 正常完成

        List<TimelineItem> items = mBuilder.build(
            Collections.singletonList(task),
            Collections.singletonList(exec)
        );

        assertEquals(1, items.size());
        TimelineItem item = items.get(0);
        assertEquals(1L, item.taskId);
        assertEquals("专注任务", item.title);
        assertEquals(30, item.focusMinutes);
        assertEquals(25, item.actualMinutes);
        assertFalse(item.running);
    }

    @Test
    public void build_shortExecutionStatus3_excludedFromTimeline() {
        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.content = "短完成任务";
        task.focusMinutes = 30;
        task.quadrant = 0;

        TaskExecutionEntity exec = new TaskExecutionEntity();
        exec.id = 100L;
        exec.taskId = 1L;
        exec.startMs = 1000L;
        exec.endMs = 300000L;
        exec.actualMinutes = 5;
        exec.status = 3; // 短完成

        List<TimelineItem> items = mBuilder.build(
            Collections.singletonList(task),
            Collections.singletonList(exec)
        );

        assertTrue("status=3 的短完成记录不应出现在时间线", items.isEmpty());
        verify(mTaskRepo, never()).getTasksByIdsSync(anyList());
    }

    @Test
    public void build_otherNonZeroStatusExecutions_excludedFromTimeline() {
        TaskEntity task = new TaskEntity();
        task.id = 1L;
        task.content = "任务";
        task.focusMinutes = 30;

        TaskExecutionEntity exec1 = new TaskExecutionEntity();
        exec1.id = 101L;
        exec1.taskId = 1L;
        exec1.startMs = 1000L;
        exec1.endMs = 2000L;
        exec1.status = 1; // 延迟

        TaskExecutionEntity exec2 = new TaskExecutionEntity();
        exec2.id = 102L;
        exec2.taskId = 1L;
        exec2.startMs = 3000L;
        exec2.endMs = 4000L;
        exec2.status = 2; // 暂停

        List<TaskExecutionEntity> execs = new ArrayList<>();
        execs.add(exec1);
        execs.add(exec2);

        List<TimelineItem> items = mBuilder.build(
            Collections.singletonList(task),
            execs
        );

        assertTrue("status!=0 的记录均不应出现在时间线", items.isEmpty());
    }

    @Test
    public void build_runningFocusTask_includedInTimeline() {
        TaskEntity task = new TaskEntity();
        task.id = 2L;
        task.content = "执行中任务";
        task.focusMinutes = 45;
        task.executingStartMs = 5000L;
        task.executingEndMs = 0L;
        task.quadrant = 1;

        List<TimelineItem> items = mBuilder.build(
            Collections.singletonList(task),
            Collections.emptyList()
        );

        assertEquals(1, items.size());
        TimelineItem item = items.get(0);
        assertEquals(2L, item.taskId);
        assertTrue(item.running);
        assertEquals(45, item.focusMinutes);
    }

    @Test
    public void build_missingTaskLookedUp_status0_included() {
        TaskEntity task = new TaskEntity();
        task.id = 3L;
        task.content = "历史任务";
        task.focusMinutes = 20;
        task.quadrant = 2;

        when(mTaskRepo.getTasksByIdsSync(Collections.singletonList(3L)))
            .thenReturn(Collections.singletonList(task));

        TaskExecutionEntity exec = new TaskExecutionEntity();
        exec.id = 103L;
        exec.taskId = 3L;
        exec.startMs = 1000L;
        exec.endMs = 1200000L;
        exec.actualMinutes = 20;
        exec.status = 0;

        List<TimelineItem> items = mBuilder.build(
            Collections.emptyList(),
            Collections.singletonList(exec)
        );

        assertEquals(1, items.size());
        assertEquals(3L, items.get(0).taskId);
        verify(mTaskRepo).getTasksByIdsSync(Collections.singletonList(3L));
    }
}
