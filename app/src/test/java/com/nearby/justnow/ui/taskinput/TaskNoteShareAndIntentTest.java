package com.nearby.justnow.ui.taskinput;

import android.content.Context;
import android.content.Intent;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskNoteShare;
import com.nearby.justnow.data.repository.TagRepository;
import com.nearby.justnow.data.repository.TaskAppActionRepository;
import com.nearby.justnow.data.repository.TaskChecklistRepository;
import com.nearby.justnow.data.repository.TaskNoteShareRepository;
import com.nearby.justnow.data.repository.TaskRepository;
import com.nearby.justnow.ui.base.UriParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TaskNoteShareAndIntentTest {

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
    public void uriParser_parseIntentUriScheme_success() throws Exception {
        String intentUri = "intent:#Intent;action=android.intent.action.VIEW;package=com.example.app;end";
        Intent intent = UriParser.parse(intentUri);
        assertNotNull(intent);
        assertEquals("com.example.app", intent.getPackage());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
    }

    @Test
    public void uriParser_parseHttpUrl_returnsActionView() throws Exception {
        String url = "https://example.com/article/42";
        Intent intent = UriParser.parse(url);
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("https://example.com/article/42", intent.getDataString());
    }

    @Test
    public void uriParser_parseCustomScheme_returnsActionView() throws Exception {
        String schemeUri = "customapp://open/page?id=100";
        Intent intent = UriParser.parse(schemeUri);
        assertNotNull(intent);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals("customapp://open/page?id=100", intent.getDataString());
    }

    @Test
    public void saveTask_singleNoteShare_savesModuleAndItem() throws Exception {
        mViewModel.setTitle("含关联笔记的任务");
        mViewModel.setQuadrant(0);
        mViewModel.setSelectedModuleType("note_shares");

        TaskNoteShare share = new TaskNoteShare();
        share.deepLink = "https://developer.android.com";
        share.hint = "官方文档";
        mViewModel.setPendingNoteShares(Collections.singletonList(share));

        saveAndDrain();

        List<TaskEntity> tasks = mDb.taskDao().getAllActiveTasksSync();
        assertEquals(1, tasks.size());
        TaskEntity task = tasks.get(0);
        assertEquals("note_shares", task.detailModuleType);

        List<TaskNoteShare> shares = mDb.taskNoteShareDao().getByTaskIdSync(task.id);
        assertEquals(1, shares.size());
        assertEquals("https://developer.android.com", shares.get(0).deepLink);
        assertEquals("官方文档", shares.get(0).hint);
    }

    @Test
    public void saveTask_emptyNoteShare_clearsModule() throws Exception {
        mViewModel.setTitle("清空关联笔记的任务");
        mViewModel.setQuadrant(0);
        mViewModel.setSelectedModuleType("note_shares");
        mViewModel.setPendingNoteShares(Collections.emptyList());

        saveAndDrain();

        List<TaskEntity> tasks = mDb.taskDao().getAllActiveTasksSync();
        assertEquals(1, tasks.size());
        TaskEntity task = tasks.get(0);
        assertNull(task.detailModuleType);

        List<TaskNoteShare> shares = mDb.taskNoteShareDao().getByTaskIdSync(task.id);
        assertTrue(shares.isEmpty());
    }

    private void saveAndDrain() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        mViewModel.saveTask(latch::countDown);
        long deadline = System.currentTimeMillis() + 3000;
        while (latch.getCount() > 0 && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(50);
        }
        assertTrue("save 应在 3 秒内完成", latch.getCount() == 0);
    }

    private static class TestApplication extends JustNowApplication {
        private final AppDatabase mDb;
        private TaskRepository mTaskRepo;
        private TagRepository mTagRepo;
        private TaskChecklistRepository mChecklistRepo;
        private TaskAppActionRepository mAppActionRepo;
        private TaskNoteShareRepository mNoteShareRepo;

        TestApplication(AppDatabase db) {
            mDb = db;
        }

        @Override
        public AppDatabase getDatabase() {
            return mDb;
        }

        @Override
        public TaskRepository getTaskRepository() {
            if (mTaskRepo == null) {
                mTaskRepo = new TaskRepository(mDb);
            }
            return mTaskRepo;
        }

        @Override
        public TagRepository getTagRepository() {
            if (mTagRepo == null) {
                mTagRepo = new TagRepository(mDb);
            }
            return mTagRepo;
        }

        @Override
        public TaskChecklistRepository getTaskChecklistRepository() {
            if (mChecklistRepo == null) {
                mChecklistRepo = new TaskChecklistRepository(mDb);
            }
            return mChecklistRepo;
        }

        @Override
        public TaskAppActionRepository getTaskAppActionRepository() {
            if (mAppActionRepo == null) {
                mAppActionRepo = new TaskAppActionRepository(mDb);
            }
            return mAppActionRepo;
        }

        @Override
        public TaskNoteShareRepository getTaskNoteShareRepository() {
            if (mNoteShareRepo == null) {
                mNoteShareRepo = new TaskNoteShareRepository(mDb);
            }
            return mNoteShareRepo;
        }
    }
}
