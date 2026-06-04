package com.nearby.justnow.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.nearby.justnow.data.entity.TaskScheduleEntity;

import java.util.List;

/**
 * 任务安排 DAO。
 *
 * <p>当前数据层假设：每个 task 任意时刻最多一条 enabled=1 的安排记录
 * （由 task_id UNIQUE 索引 + insert 前清理 disabled 旧行共同保证）。
 * 因此 {@link #getActiveScheduleSync(long)} / {@link #getActiveScheduleLive(long)}
 * 的 LIMIT 1 是确定性的，调用方可直接以"按 taskId 取当前安排"使用。
 * 若未来放开多条 active 并存，所有 active 查询路径都需要重新审视。</p>
 */
@Dao
public interface TaskScheduleDao {

    @Query("SELECT * FROM task_schedules WHERE task_id = :taskId AND enabled = 1 LIMIT 1")
    LiveData<TaskScheduleEntity> getActiveScheduleLive(long taskId);

    @Query("SELECT * FROM task_schedules WHERE task_id = :taskId AND enabled = 1 LIMIT 1")
    TaskScheduleEntity getActiveScheduleSync(long taskId);

    @Query("SELECT * FROM task_schedules WHERE id = :scheduleId")
    TaskScheduleEntity getScheduleById(long scheduleId);

    @Insert
    long insert(TaskScheduleEntity schedule);

    @Update
    void update(TaskScheduleEntity schedule);

    @Query("UPDATE task_schedules SET enabled = 0, disable_reason = :reason, updated_at = :updatedAt WHERE id = :scheduleId")
    void disableSchedule(long scheduleId, String reason, long updatedAt);

    @Query("UPDATE task_schedules SET enabled = 0, updated_at = :updatedAt WHERE task_id = :taskId AND enabled = 1")
    void disableForTask(long taskId, long updatedAt);

    /** 清理某任务的所有 disabled 残留行，避免 task_id UNIQUE 冲突。 */
    @Query("DELETE FROM task_schedules WHERE task_id = :taskId AND enabled = 0")
    void deleteDisabledByTaskId(long taskId);

    /** 批量 disable 今天已过期的 TYPE_ONCE 安排。 */
    @Query("UPDATE task_schedules SET enabled = 0, disable_reason = 'EXPIRED', updated_at = :now " +
        "WHERE schedule_type = 0 AND enabled = 1 AND schedule_value < :todayStartMs")
    void disableExpiredOnceToday(long todayStartMs, long now);

    /** 获取今天应触发的安排（含重复规则匹配）。 */
    @Query("SELECT * FROM task_schedules WHERE enabled = 1")
    List<TaskScheduleEntity> getAllEnabledSchedulesSync();

    /** 获取所有安排（含 disabled 残留），用于安全兜底取消闹钟。 */
    @Query("SELECT * FROM task_schedules")
    List<TaskScheduleEntity> getAllSchedulesSync();

    /** 按关联时段组类型获取所有启用的安排。 */
    @Query("SELECT * FROM task_schedules WHERE linked_period_group_type = :groupType AND enabled = 1")
    List<TaskScheduleEntity> getEnabledSchedulesByLinkedGroupType(String groupType);
}
