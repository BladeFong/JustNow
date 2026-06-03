package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;

import com.nearby.justnow.data.dao.TagDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 标签仓库 — 封装 TagDao 操作
 */
public class TagRepository extends BaseRepository {

    private final TagDao mDao;

    // 内存缓存 —— 所有消费者共享，减少 Room 同步查询次数
    // 使用线程安全集合保证并发读写安全（volatile 只保证引用可见性，不保护集合内部状态）
    private volatile CopyOnWriteArrayList<TagEntity> mCachedTags;
    private volatile ConcurrentHashMap<Long, TagEntity> mCachedTagsMap;

    public TagRepository(AppDatabase db) {
        super(db);
        this.mDao = db.tagDao();
    }

    public LiveData<List<TagEntity>> searchTags(String keyword) {
        return mDao.searchByName(keyword);
    }

    public LiveData<List<TagEntity>> getAllTags() {
        return mDao.getAllTags();
    }

    public void insert(TagEntity tag, Runnable onComplete) {
        mDb.runInBackground(() -> {
            long id = mDao.insert(tag);
            tag.id = id;
            sIncrementTagsCache(tag);
            if (onComplete != null) onComplete.run();
        });
    }

    public long insertSync(TagEntity tag) {
        assertNotMainThread();
        long id = mDao.insert(tag);
        tag.id = id;
        sIncrementTagsCache(tag);
        return id;
    }

    public void delete(long tagId) {
        mDb.runInBackground(() -> {
            mDao.clearPriorityRulesForTag(tagId);
            mDao.delete(tagId);
            sRemoveTagFromCache(tagId);
        });
    }

    public LiveData<List<TagEntity>> getTopTags(int limit) {
        return mDao.getTopTags(limit);
    }

    /** 同步获取全部标签（供后台计算使用）。返回防御性拷贝，调用方可安全修改。 */
    public List<TagEntity> getAllTagsSync() {
        if (mCachedTags != null) {
            return new ArrayList<>(mCachedTags);
        }
        List<TagEntity> result = mDao.getAllTagsSync();
        mCachedTags = new CopyOnWriteArrayList<>(result);
        mCachedTagsMap = null;
        return new ArrayList<>(result);
    }

    /** 同步获取全部标签 Map（tagId -> TagEntity），复用缓存 */
    public Map<Long, TagEntity> getAllTagsMapSync() {
        if (mCachedTagsMap != null) {
            return mCachedTagsMap;
        }
        ensureTagsLoaded();
        if (mCachedTags == null) return new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, TagEntity> map = new ConcurrentHashMap<>();
        for (TagEntity tag : mCachedTags) {
            map.put(tag.id, tag);
        }
        mCachedTagsMap = map;
        return map;
    }

    /** 按 ID 查找标签，复用缓存 */
    public TagEntity getTagByIdSync(long tagId) {
        ensureTagsMapLoaded();
        return mCachedTagsMap != null ? mCachedTagsMap.get(tagId) : null;
    }

    /** 按名称查找标签，复用缓存 */
    public TagEntity getTagByNameSync(String name) {
        if (name == null) return null;
        ensureTagsMapLoaded();
        if (mCachedTagsMap != null) {
            for (TagEntity t : mCachedTagsMap.values()) {
                if (name.equals(t.name)) return t;
            }
        }
        return null;
    }

    /** 按 ID 集合批量获取标签，复用缓存 */
    public List<TagEntity> getTagsByIdsSync(Set<Long> tagIds) {
        List<TagEntity> result = new ArrayList<>();
        if (tagIds == null || tagIds.isEmpty()) return result;
        ensureTagsMapLoaded();
        if (mCachedTagsMap != null) {
            for (Long id : tagIds) {
                TagEntity t = mCachedTagsMap.get(id);
                if (t != null) result.add(t);
            }
        }
        return result;
    }

    private void ensureTagsLoaded() {
        if (mCachedTags == null) getAllTagsSync();
    }

    private void ensureTagsMapLoaded() {
        if (mCachedTagsMap == null) getAllTagsMapSync();
    }

    /** 同步获取未被任何任务使用且非优先的标签 */
    public List<TagEntity> getUnusedTagsSync() {
        return mDao.getUnusedTagsSync();
    }

    /** 异步批量删除标签 */
    public void deleteTags(List<Long> tagIds) {
        mDb.runInBackground(() -> {
            for (long id : tagIds) {
                mDao.clearPriorityRulesForTag(id);
                mDao.delete(id);
            }
            sRemoveTagsFromCache(tagIds);
        });
    }

    /** 获取全部优先标签 LiveData */
    public LiveData<List<TagEntity>> getPriorityTagsLive() {
        return mDao.getPriorityTagsLive();
    }

    /** 同步获取优先标签 ID 集合 */
    public List<Long> getPriorityTagIdsSync() {
        return mDao.getPriorityTagIdsSync();
    }

    /** 异步设置标签优先级 */
    public void setTagPriority(long tagId, boolean isPriority) {
        mDb.runInBackground(() -> mDao.setTagPriority(tagId, isPriority));
    }

    /** 同步设置标签优先级（供已在线程池中的场景调用） */
    public void setTagPrioritySync(long tagId, boolean isPriority) {
        mDao.setTagPriority(tagId, isPriority);
        sUpdateTagPriorityInCache(tagId, isPriority);
    }

    public LiveData<List<TagEntity>> getPriorityTagsForGroupLive(String groupType) {
        return mDao.getPriorityTagsForGroupLive(groupType);
    }

    public LiveData<List<PriorityTagRuleEntity>> getAllPriorityRulesLive() {
        return mDao.getAllPriorityRulesLive();
    }

    public List<Long> getPriorityTagIdsForGroupSync(String groupType) {
        List<Long> ids = mDao.getPriorityTagIdsForGroupSync(groupType);
        return ids != null ? ids : Collections.emptyList();
    }

    public void setPriorityTagsForGroup(String groupType, Set<Long> tagIds) {
        mDb.runInBackground(() ->
            setPriorityTagsForGroupSync(groupType, tagIds));
    }

    public void setPriorityTagsForGroupSync(String groupType, Set<Long> tagIds) {
        mDb.runInTransaction(() -> {
            mDao.clearPriorityRulesForGroup(groupType);
            if (tagIds == null) return;
            for (long tagId : tagIds) {
                PriorityTagRuleEntity rule = new PriorityTagRuleEntity();
                rule.groupType = groupType;
                rule.tagId = tagId;
                mDao.insertPriorityRule(rule);
            }
        });
    }

    // ---- 缓存辅助方法 ----

    private void sIncrementTagsCache(TagEntity tag) {
        if (mCachedTags != null) {
            mCachedTags.add(tag);
            if (mCachedTagsMap != null) mCachedTagsMap.put(tag.id, tag);
        }
    }

    private void sRemoveTagFromCache(long tagId) {
        if (mCachedTags != null) {
            mCachedTags.removeIf(t -> t.id == tagId);
            if (mCachedTagsMap != null) mCachedTagsMap.remove(tagId);
        }
    }

    private void sRemoveTagsFromCache(List<Long> tagIds) {
        if (mCachedTags != null && tagIds != null) {
            mCachedTags.removeIf(t -> tagIds.contains(t.id));
            if (mCachedTagsMap != null) mCachedTagsMap.keySet().removeAll(tagIds);
        }
    }

    private void sUpdateTagPriorityInCache(long tagId, boolean isPriority) {
        if (mCachedTags != null) {
            for (TagEntity t : mCachedTags) {
                if (t.id == tagId) {
                    t.isPriority = isPriority;
                    break;
                }
            }
        }
    }
}
