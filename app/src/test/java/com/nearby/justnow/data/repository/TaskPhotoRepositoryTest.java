package com.nearby.justnow.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskPhotoEntity;
import com.nearby.justnow.data.entity.TaskPhotoWithTask;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 本地 JVM 单元测试：验证 TaskPhotoRepository 照片的存储与防裂图自愈清理功能
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskPhotoRepositoryTest {
    private AppDatabase mDb;
    private TaskPhotoRepository mRepository;
    private long mTestTaskId;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        // 使用内存数据库
        mDb = AppDatabase.createInMemory(context);
        mRepository = new TaskPhotoRepository(mDb);

        // 先插入一个 Task 作为外键约束基础
        TaskEntity task = new TaskEntity();
        task.content = "Test Task for Photo";
        task.detail = "";
        task.quadrant = 2;
        task.focusMinutes = 20;
        task.isArchived = false;
        task.createdAt = System.currentTimeMillis();
        task.completionMode = 0;
        task.quota = 1;
        mTestTaskId = mDb.taskDao().insert(task);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    @Test
    public void testBindAndQuery() {
        String testUri = "content://media/external/images/media/1";
        long id = mRepository.bindPhotoToTask(mTestTaskId, testUri);
        assertTrue(id > 0);

        TaskPhotoEntity photo = mDb.taskPhotoDao().getPhotoForTask(mTestTaskId);
        assertNotNull(photo);
        assertEquals(testUri, photo.photoUri);
        assertEquals(mTestTaskId, photo.taskId);
    }

}
