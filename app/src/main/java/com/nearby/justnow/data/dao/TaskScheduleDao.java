package com.nearby.justnow.data.dao;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.room.ColumnInfo;
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

    @Query("SELECT * FROM task_schedules WHERE enabled = 1")
    LiveData<List<TaskScheduleEntity>> getAllEnabledSchedulesLive();

    @Query("SELECT * FROM task_schedules WHERE task_id = :taskId AND enabled = 1 LIMIT 1")
    TaskScheduleEntity getActiveScheduleSync(long taskId);

    @Query("SELECT * FROM task_schedules WHERE task_id = :taskId LIMIT 1")
    TaskScheduleEntity getScheduleByTaskIdSync(long taskId);

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

    /** 批量 disable 今天已过期的 TYPE_ONCE 安排（与忽略走同一逻辑）。 */
    @Query("UPDATE task_schedules SET enabled = 0, updated_at = :now " +
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

    /** 获取所有 enabled 的 TYPE_ONCE 安排，关联 TaskEntity 获取 focusMinutes（用于过期检查）。 */
    @Query("SELECT s.id, s.task_id, s.schedule_value, s.scheduled_time, t.focus_minutes " +
        "FROM task_schedules s INNER JOIN tasks t ON s.task_id = t.id " +
        "WHERE s.schedule_type = 0 AND s.enabled = 1")
    List<ScheduleWithFocusMinutes> getEnabledOnceSchedulesWithFocusSync();

    /** 获取所有启用的安排，关联 TaskEntity 获取 focusMinutes。 */
    @Query("SELECT s.id, s.task_id, s.schedule_type, s.schedule_value, s.scheduled_time, " +
        "s.linked_period_group_type, s.schedule_sub_type, s.enabled, s.disable_reason, " +
        "s.created_at, s.updated_at, t.focus_minutes " +
        "FROM task_schedules s INNER JOIN tasks t ON s.task_id = t.id " +
        "WHERE s.enabled = 1")
    List<ScheduleWithFocusMinutesFull> getEnabledSchedulesWithFocusSync();

    /** 按 ID 列表批量 disable。 */
    @Query("UPDATE task_schedules SET enabled = 0, updated_at = :now WHERE id IN (:ids)")
    void disableByIds(List<Long> ids, long now);

    /** TYPE_ONCE 过期检查 POJO。 */
    class ScheduleWithFocusMinutes {
        public long id;
        @ColumnInfo(name = "task_id")
        public long taskId;
        @ColumnInfo(name = "schedule_value")
        public long scheduleValue;
        @ColumnInfo(name = "scheduled_time")
        public int scheduledTime;
        @ColumnInfo(name = "focus_minutes")
        public int focusMinutes;
    }

    /** 全量安排 + focusMinutes POJO。 */
    class ScheduleWithFocusMinutesFull {
        public long id;
        @ColumnInfo(name = "task_id")
        public long taskId;
        @ColumnInfo(name = "schedule_type")
        public int scheduleType;
        @ColumnInfo(name = "schedule_value")
        public long scheduleValue;
        @ColumnInfo(name = "scheduled_time")
        public int scheduledTime;
        @NonNull
        @ColumnInfo(name = "linked_period_group_type")
        public String linkedPeriodGroupType = "";
        @ColumnInfo(name = "schedule_sub_type")
        public int scheduleSubType;
        @ColumnInfo(name = "enabled")
        public boolean enabled;
        @ColumnInfo(name = "disable_reason")
        public String disableReason;
        @ColumnInfo(name = "created_at")
        public long createdAt;
        @ColumnInfo(name = "updated_at")
        public long updatedAt;
        @ColumnInfo(name = "focus_minutes")
        public int focusMinutes;

        public TaskScheduleEntity toEntity() {
            TaskScheduleEntity e = new TaskScheduleEntity();
            e.id = id;
            e.taskId = taskId;
            e.scheduleType = scheduleType;
            e.scheduleValue = scheduleValue;
            e.scheduledTime = scheduledTime;
            e.linkedPeriodGroupType = linkedPeriodGroupType;
            e.scheduleSubType = scheduleSubType;
            e.enabled = enabled;
            e.disableReason = disableReason;
            e.createdAt = createdAt;
            e.updatedAt = updatedAt;
            e.focusMinutes = focusMinutes;
            return e;
        }
    }
}
