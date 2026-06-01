package com.nearby.justnow.ui.taskinput;

import android.content.Context;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskInputViewModelTest {

    private AppDatabase mDb;
    private TaskInputViewModel mViewModel;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mViewModel = new TaskInputViewModel(new TestApplication(mDb));
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    @Test
    public void saveTask_withoutTag_savesNullTagId() throws Exception {
        mViewModel.setTitle("无标签任务");
        mViewModel.setDetail("");
        mViewModel.setTagName("");
        mViewModel.setQuadrant(0);
        mViewModel.setFocusMinutes(30);

        CountDownLatch latch = new CountDownLatch(1);
        mViewModel.saveTask(latch::countDown);

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        List<TaskEntity> tasks = mDb.taskDao().getAllActiveTasksSync();
        assertEquals(1, tasks.size());
        assertNull(tasks.get(0).tagId);
    }

    private static class TestApplication extends JustNowApplication {
        private final AppDatabase mDb;

        TestApplication(AppDatabase db) {
            mDb = db;
        }

        @Override
        public AppDatabase getDatabase() {
            return mDb;
        }
    }
}
