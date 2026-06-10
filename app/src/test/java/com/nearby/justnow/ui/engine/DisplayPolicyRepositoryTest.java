package com.nearby.justnow.ui.engine;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.repository.TaskRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.io.File;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class DisplayPolicyRepositoryTest {

    private Context mContext;
    private AppDatabase mDb;
    private TaskRepository mTaskRepo;
    private DisplayPolicyRepository mRepository;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.getApplication().getApplicationContext();
        deleteCustomYaml();
        mDb = AppDatabase.createInMemory(mContext);
        mTaskRepo = new TaskRepository(mDb);
        mRepository = new DisplayPolicyRepository(mContext, mTaskRepo,
                new TestDisplayPolicyMessageProvider());
    }

    @After
    public void tearDown() {
        deleteCustomYaml();
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    @Test
    public void getEffectivePolicy_noCustom_usesDefault() {
        DisplayPolicy policy = mRepository.getEffectivePolicySync();

        assertEquals(120, policy.getFocusMaxMinutes());
        assertArrayEquals(new int[]{4, 2, 2, 1}, policy.getQuadrantRatio());
    }

    @Test
    public void saveCustomYaml_thenReadEffectivePolicy() throws Exception {
        mRepository.saveCustomYamlSync(yaml(150));

        DisplayPolicy policy = mRepository.getEffectivePolicySync();

        assertEquals(150, policy.getFocusMaxMinutes());
    }

    @Test
    public void saveCustomYaml_has150ActiveTask_rejectsDowngradeTo120() throws Exception {
        mRepository.saveCustomYamlSync(yaml(150));
        mTaskRepo.insertSync(task(150, false));

        try {
            mRepository.saveCustomYamlSync(yaml(120));
            fail("Expected downgrade validation failure");
        } catch (DisplayPolicyValidationException expected) {
            assertTrue(expected.getMessage().contains("2.5h"));
            assertTrue(expected.getMessage().contains("2h"));
        }
    }

    @Test
    public void saveCustomYaml_onlyArchived150Task_allowsDowngradeTo120() throws Exception {
        mRepository.saveCustomYamlSync(yaml(150));
        mTaskRepo.insertSync(task(150, true));

        DisplayPolicy policy = mRepository.saveCustomYamlSync(yaml(120));

        assertEquals(120, policy.getFocusMaxMinutes());
    }

    @Test
    public void saveCustomYaml_active120Task_allowsUpgradeTo150() throws Exception {
        mTaskRepo.insertSync(task(120, false));

        DisplayPolicy policy = mRepository.saveCustomYamlSync(yaml(150));

        assertEquals(150, policy.getFocusMaxMinutes());
    }

    @Test
    public void saveCustomYaml_active150Task_allowsUpgradeTo150() throws Exception {
        mTaskRepo.insertSync(task(150, false));

        DisplayPolicy policy = mRepository.saveCustomYamlSync(yaml(150));

        assertEquals(150, policy.getFocusMaxMinutes());
    }

    private static TaskEntity task(int focusMinutes, boolean archived) {
        TaskEntity task = new TaskEntity();
        task.content = "task";
        task.quadrant = 0;
        task.focusMinutes = focusMinutes;
        task.isArchived = archived;
        task.createdAt = System.currentTimeMillis();
        return task;
    }

    private static String yaml(int focusMaxMinutes) {
        return "version: 1\n"
            + "priority:\n"
            + "  order:\n"
            + "    - schedule_priority\n"
            + "    - tag_priority\n"
            + "    - quadrant\n"
            + "    - focus_duration\n"
            + "time:\n"
            + "  fit_tolerance_minutes: 15\n"
            + "  focus_max_minutes: " + focusMaxMinutes + "\n"
            + "  focus_duration_order: desc\n"
            + "ratio:\n"
            + "  quadrant: [4, 2, 2, 1]\n";
    }

    private void deleteCustomYaml() {
        File file = new File(new File(mContext.getFilesDir(), "display_policy"), "current.yaml");
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static final class TestDisplayPolicyMessageProvider
            implements DisplayPolicyMessageProvider {
        @Override
        public String get(int messageResId, Object... args) {
            return messageResId + " " + Arrays.toString(args);
        }

        @Override
        public String getFocusDurationText(int focusMinutes) {
            return focusMinutes >= 60
                    ? (focusMinutes / 60.0 + "h").replace(".0h", "h")
                    : focusMinutes + "min";
        }
    }
}
