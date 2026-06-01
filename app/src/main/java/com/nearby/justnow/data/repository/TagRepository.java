package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;

import com.nearby.justnow.data.dao.TagDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 标签仓库 — 封装 TagDao 操作
 */
public class TagRepository extends BaseRepository {

    private final TagDao mDao;

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
            mDao.insert(tag);
            if (onComplete != null) onComplete.run();
        });
    }

    public long insertSync(TagEntity tag) {
        assertNotMainThread();
        return mDao.insert(tag);
    }

    public void delete(long tagId) {
        mDb.runInBackground(() -> {
            mDao.clearPriorityRulesForTag(tagId);
            mDao.delete(tagId);
        });
    }

    public LiveData<List<TagEntity>> getTopTags(int limit) {
        return mDao.getTopTags(limit);
    }

    /** 同步获取全部标签（供后台计算使用） */
    public List<TagEntity> getAllTagsSync() {
        return mDao.getAllTagsSync();
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
}
