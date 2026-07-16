package com.nearby.justnow.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nearby.justnow.data.entity.PriorityTagRuleEntity;
import com.nearby.justnow.data.entity.TagEntity;

import java.util.List;

/**
 * 标签 DAO
 */
@Dao
public interface TagDao {

    /** 按名称模糊搜索已有标签 */
    @Query("SELECT * FROM tags WHERE name LIKE '%' || :keyword || '%' ORDER BY name ASC")
    LiveData<List<TagEntity>> searchByName(String keyword);

    /** 获取全部标签 */
    @Query("SELECT * FROM tags ORDER BY name ASC")
    LiveData<List<TagEntity>> getAllTags();

    /** 同步获取全部标签 */
    @Query("SELECT * FROM tags ORDER BY name ASC")
    List<TagEntity> getAllTagsSync();

    /** 前N个标签：按使用频率降序，频率相同时按最近新增降序 */
    @Query("SELECT * FROM tags t " +
           "WHERE t.name NOT IN ('玩具', '阅读', '美术', '音乐', '运动', '益智', '手工', '动画', '学习', '家务') " +
           "ORDER BY (SELECT COUNT(*) FROM tasks WHERE tag_id = t.id) DESC, t.id DESC " +
           "LIMIT :limit")
    LiveData<List<TagEntity>> getTopTags(int limit);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TagEntity tag);

    /** 未被任何任务使用且非优先标签，按 ID 降序（新增的在前） */
    @Query("SELECT * FROM tags t " +
           "WHERE (SELECT COUNT(*) FROM tasks WHERE tag_id = t.id) = 0 " +
           "AND t.is_priority = 0 " +
           "AND t.id NOT IN (SELECT tag_id FROM priority_tag_rules) " +
           "ORDER BY t.id DESC")
    List<TagEntity> getUnusedTagsSync();

    /** 获取全部优先标签 */
    @Query("SELECT * FROM tags WHERE is_priority = 1 ORDER BY name ASC")
    LiveData<List<TagEntity>> getPriorityTagsLive();

    /** 同步获取全部优先标签 ID */
    @Query("SELECT id FROM tags WHERE is_priority = 1")
    List<Long> getPriorityTagIdsSync();

    @Query("UPDATE tags SET is_priority = :isPriority WHERE id = :tagId")
    void setTagPriority(long tagId, boolean isPriority);

    @Query("SELECT t.* FROM tags t INNER JOIN priority_tag_rules r ON r.tag_id = t.id " +
           "WHERE r.group_type = :groupType ORDER BY t.name ASC")
    LiveData<List<TagEntity>> getPriorityTagsForGroupLive(String groupType);

    @Query("SELECT * FROM priority_tag_rules")
    LiveData<List<PriorityTagRuleEntity>> getAllPriorityRulesLive();

    @Query("SELECT tag_id FROM priority_tag_rules WHERE group_type = :groupType")
    List<Long> getPriorityTagIdsForGroupSync(String groupType);

    @Query("SELECT DISTINCT tag_id FROM priority_tag_rules")
    List<Long> getPriorityRuleTagIdsSync();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertPriorityRule(PriorityTagRuleEntity rule);

    @Delete
    void deletePriorityRule(PriorityTagRuleEntity rule);

    @Query("DELETE FROM priority_tag_rules WHERE group_type = :groupType")
    void clearPriorityRulesForGroup(String groupType);

    @Query("DELETE FROM priority_tag_rules WHERE tag_id = :tagId")
    void clearPriorityRulesForTag(long tagId);

    @Query("DELETE FROM tags WHERE id = :tagId")
    void delete(long tagId);
}
