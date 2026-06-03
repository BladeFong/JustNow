package com.nearby.justnow.data.repository;

import android.content.Context;

import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TagEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * TagRepository 测试 —— 缓存行为与并发安全集合验证。
 * 主要验证 mCachedTags (CopyOnWriteArrayList) 和 mCachedTagsMap (ConcurrentHashMap) 的缓存正确性。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TagRepositoryTest {

    private AppDatabase mDb;
    private TagRepository mRepo;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication().getApplicationContext();
        mDb = AppDatabase.createInMemory(context);
        mRepo = new TagRepository(mDb);
    }

    @After
    public void tearDown() {
        if (mDb != null && mDb.isOpen()) {
            mDb.close();
        }
    }

    // ---- getAllTagsSync 缓存 ----

    @Test
    public void getAllTagsSync_firstCallFromDb_empty() {
        List<TagEntity> result = mRepo.getAllTagsSync();
        assertTrue("首次调用 DB 无记录应为空", result.isEmpty());
    }

    @Test
    public void getAllTagsSync_firstCallReturnsDbData() {
        long tagId = mRepo.insertSync(tag("标签一", 0xFF0000));

        // 新 Repo 实例测试（绕过缓存）
        TagRepository freshRepo = new TagRepository(mDb);
        List<TagEntity> result = freshRepo.getAllTagsSync();
        assertEquals(1, result.size());
        assertEquals("标签一", result.get(0).name);
    }

    @Test
    public void getAllTagsSync_cacheHit_doesNotRequery() {
        mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsSync(); // 预热缓存

        // 直接往 DB 插入标签（绕过缓存更新）
        TagEntity newTag = tag("标签二", 0x00FF00);
        mDb.tagDao().insert(newTag);

        // 缓存命中 → 应返回缓存中的旧数据
        List<TagEntity> result = mRepo.getAllTagsSync();
        assertEquals("缓存命中应仅含预热时的标签", 1, result.size());
    }

    @Test
    public void getAllTagsSync_returnsDefensiveCopy() {
        mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsSync(); // 预热缓存

        List<TagEntity> result1 = mRepo.getAllTagsSync();
        assertEquals(1, result1.size());
        result1.clear(); // 修改返回列表

        // 缓存不应受影响
        List<TagEntity> result2 = mRepo.getAllTagsSync();
        assertEquals("防御性拷贝：修改返回列表不应影响缓存", 1, result2.size());
    }

    // ---- getAllTagsMapSync 缓存 ----

    @Test
    public void getAllTagsMapSync_buildsFromCache() {
        mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.insertSync(tag("标签二", 0x00FF00));

        Map<Long, TagEntity> map = mRepo.getAllTagsMapSync();
        assertEquals(2, map.size());
    }

    @Test
    public void getAllTagsMapSync_returnsCachedMap() {
        mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsMapSync();

        // 直接往 DB 插入新标签（绕过缓存）
        TagEntity newTag = tag("标签二", 0x00FF00);
        mDb.tagDao().insert(newTag);

        // 第二次调用应返回缓存的 map（不含新标签）
        Map<Long, TagEntity> map = mRepo.getAllTagsMapSync();
        assertEquals("缓存命中应仅含预热时的标签", 1, map.size());
    }

    // ---- 按 ID / 名称 / 集合查找 ----

    @Test
    public void getTagByIdSync_findsTag() {
        long tagId = mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsMapSync(); // 预热 map 缓存

        TagEntity found = mRepo.getTagByIdSync(tagId);
        assertNotNull("应能找到已插入的标签", found);
        assertEquals("标签一", found.name);
    }

    @Test
    public void getTagByIdSync_returnsNullForUnknown() {
        TagEntity found = mRepo.getTagByIdSync(99999);
        assertNull("不存在的 ID 应返回 null", found);
    }

    @Test
    public void getTagByNameSync_findsTag() {
        mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsMapSync();

        TagEntity found = mRepo.getTagByNameSync("标签一");
        assertNotNull("应能找到匹配名称的标签", found);
    }

    @Test
    public void getTagByNameSync_returnsNullForUnknown() {
        TagEntity found = mRepo.getTagByNameSync("不存在");
        assertNull("不存在的名称应返回 null", found);
    }

    @Test
    public void getTagByNameSync_nullName_returnsNull() {
        TagEntity found = mRepo.getTagByNameSync(null);
        assertNull("null 名称应返回 null", found);
    }

    @Test
    public void getTagsByIdsSync_batchFind() {
        long id1 = mRepo.insertSync(tag("标签一", 0xFF0000));
        long id2 = mRepo.insertSync(tag("标签二", 0x00FF00));
        mRepo.getAllTagsMapSync();

        Set<Long> ids = new HashSet<>(Arrays.asList(id1, id2));
        List<TagEntity> result = mRepo.getTagsByIdsSync(ids);
        assertEquals("应返回两个标签", 2, result.size());
    }

    @Test
    public void getTagsByIdsSync_emptyInput_returnsEmpty() {
        mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsMapSync();

        List<TagEntity> result = mRepo.getTagsByIdsSync(Collections.emptySet());
        assertTrue("空输入应返回空列表", result.isEmpty());
    }

    @Test
    public void getTagsByIdsSync_nullInput_returnsEmpty() {
        List<TagEntity> result = mRepo.getTagsByIdsSync(null);
        assertTrue("null 输入应返回空列表", result.isEmpty());
    }

    // ---- insertSync 缓存更新 ----

    @Test
    public void insertSync_addsToCache() {
        mRepo.getAllTagsSync(); // 预热缓存

        mRepo.insertSync(tag("新标签", 0xFF0000));

        // 不从 DB 重新查询，直接走缓存
        List<TagEntity> result = mRepo.getAllTagsSync();
        assertEquals("缓存应包含新插入的标签", 1, result.size());
        assertEquals("新标签", result.get(0).name);
    }

    @Test
    public void insertSync_addsToTagsMapCache() {
        mRepo.getAllTagsMapSync(); // 预热 map 缓存

        long tagId = mRepo.insertSync(tag("新标签", 0xFF0000));

        Map<Long, TagEntity> map = mRepo.getAllTagsMapSync();
        assertTrue("map 缓存应包含新标签 ID", map.containsKey(tagId));
    }

    // ---- setTagPrioritySync 缓存更新 ----

    @Test
    public void setTagPrioritySync_updatesCache() {
        long tagId = mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsSync(); // 预热缓存

        assertFalse("初始不应为优先", mRepo.getTagByIdSync(tagId).isPriority);

        mRepo.setTagPrioritySync(tagId, true);

        TagEntity updated = mRepo.getTagByIdSync(tagId);
        assertTrue("setTagPrioritySync 应更新缓存", updated.isPriority);
    }

    // ---- delete 异步 (runInBackground) ----

    @Test
    public void delete_removesFromCache() throws InterruptedException {
        long tagId = mRepo.insertSync(tag("标签一", 0xFF0000));
        mRepo.getAllTagsSync(); // 预热缓存
        assertEquals(1, mRepo.getAllTagsSync().size());

        mRepo.delete(tagId);

        // delete 走 runInBackground，等待 executor 完成
        Thread.sleep(300);

        assertEquals("缓存应从删除中恢复（重新加载 DB）", 0, mRepo.getAllTagsSync().size());
    }

    // ---- deleteTags 异步 ----

    @Test
    public void deleteTags_removesMultipleFromCache() throws InterruptedException {
        long id1 = mRepo.insertSync(tag("标签一", 0xFF0000));
        long id2 = mRepo.insertSync(tag("标签二", 0x00FF00));
        mRepo.getAllTagsSync();
        assertEquals(2, mRepo.getAllTagsSync().size());

        mRepo.deleteTags(Arrays.asList(id1, id2));

        Thread.sleep(300);

        assertEquals("缓存应清除被删除的全部标签", 0, mRepo.getAllTagsSync().size());
    }

    // ---- 辅助方法 ----

    private static TagEntity tag(String name, int color) {
        TagEntity tag = new TagEntity();
        tag.name = name;
        tag.color = color;
        return tag;
    }
}
