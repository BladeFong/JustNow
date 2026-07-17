package com.nearby.justnow.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskPhotoEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class TaskPhotoRepositoryTest {
    private AppDatabase mDb;
    private TaskPhotoRepository mRepository;
    private long mTestTaskId;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
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
        mDb.close();
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

    @Test
    public void testverifyAndCleanupPhotos_whenFileNotExists_shouldDelete() throws InterruptedException {
        // 绑定一个不存在的虚拟Uri
        String invalidUri = "file:///sdcard/non_existent_photo_123.jpg";
        mRepository.bindPhotoToTask(mTestTaskId, invalidUri);

        // 验证数据库里存在该记录
        assertNotNull(mDb.taskPhotoDao().getPhotoForTask(mTestTaskId));

        // 运行自愈清理
        Context context = ApplicationProvider.getApplicationContext();
        mRepository.verifyAndCleanupPhotos(context);

        // 因为 verifyAndCleanupPhotos 是异步 AppDatabase.execute() 提交到线程池的，
        // 我们需要稍微等待它执行完毕
        Thread.sleep(500);

        // 验证数据库对应关联已被删除自愈
        assertNull(mDb.taskPhotoDao().getPhotoForTask(mTestTaskId));
    }

    @Test
    public void testverifyAndCleanupPhotos_whenFileExists_shouldKeep() throws IOException, InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        // 创建一个临时真实物理文件
        File tempFile = File.createTempFile("temp_img_", ".jpg", context.getCacheDir());
        FileOutputStream fos = new FileOutputStream(tempFile);
        fos.write("dummy photo bytes".getBytes());
        fos.close();

        String validUri = Uri.fromFile(tempFile).toString();
        mRepository.bindPhotoToTask(mTestTaskId, validUri);

        // 验证存在
        assertNotNull(mDb.taskPhotoDao().getPhotoForTask(mTestTaskId));

        // 运行自愈清理
        mRepository.verifyAndCleanupPhotos(context);
        Thread.sleep(500);

        // 验证被保留（因为文件确实物理存在）
        TaskPhotoEntity photo = mDb.taskPhotoDao().getPhotoForTask(mTestTaskId);
        assertNotNull(photo);
        assertEquals(validUri, photo.photoUri);

        // 清理缓存文件
        tempFile.delete();
    }

    private void assertTrue(boolean condition) {
        org.junit.Assert.assertTrue(condition);
    }
}
