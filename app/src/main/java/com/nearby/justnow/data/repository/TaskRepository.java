package com.nearby.justnow.data.repository;

import androidx.lifecycle.LiveData;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.nearby.justnow.data.dao.TaskDao;
import com.nearby.justnow.data.dao.TaskQuadrantDegradeDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskEntity;
import com.nearby.justnow.data.entity.TaskQuadrantDegradeEntity;
import com.nearby.justnow.data.observer.DataChangeDispatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务仓库 — 封装 TaskDao 操作
 */
public class TaskRepository extends BaseRepository {

    private final TaskDao mDao;
    private final TaskQuadrantDegradeDao mDegradeDao;

    // 内存缓存 —— 所有消费者共享，减少 Room 同步查询次数
    // 使用 CopyOnWriteArrayList 保证并发读写安全（volatile 只保证引用可见性，不保护集合内部状态）
    private volatile CopyOnWriteArrayList<TaskQuadrantDegradeEntity> mCachedDegrades;
    private volatile CopyOnWriteArrayList<TaskEntity> mCachedActiveTasks;

    public TaskRepository(AppDatabase db) {
        super(db);
        this.mDao = db.taskDao();
        this.mDegradeDao = db.taskQuadrantDegradeDao();
    }

    public LiveData<List<TaskEntity>> getAllActiveTasks() {
        return mDao.getAllActiveTasks();
    }

    public LiveData<List<TaskEntity>> getTasksByQuadrantAndTag(int quadrant, long tagId) {
        return mDao.getTasksByQuadrantAndTag(quadrant, tagId);
    }

    public LiveData<List<TaskEntity>> getTasksByTag(long tagId) {
        return mDao.getTasksByTag(tagId);
    }

    public void archiveTaskSync(long taskId) {
        assertNotMainThread();
        mDb.runInTransaction(() -> {
            mDao.archiveTask(taskId);
            mDegradeDao.deleteByTaskId(taskId);
        });
        if (mCachedActiveTasks != null) mCachedActiveTasks.removeIf(t -> t.id == taskId);
        if (mCachedDegrades != null) mCachedDegrades.removeIf(d -> d.taskId == taskId);
        notifyTaskDataChanged();
    }

    public void insert(TaskEntity task, Runnable onComplete) {
        mDb.runInBackground(() -> {
            long id = mDao.insert(task);
            task.id = id;
            if (mCachedActiveTasks != null) mCachedActiveTasks.add(task);
            notifyTaskDataChanged();
            if (onComplete != null) onComplete.run();
        });
    }

    public long insertSync(TaskEntity task) {
        assertNotMainThread();
        long id = mDao.insert(task);
        task.id = id;
        if (mCachedActiveTasks != null) mCachedActiveTasks.add(task);
        notifyTaskDataChanged();
        return id;
    }

    public void update(TaskEntity task) {
        mCachedActiveTasks = null; // 更新字段太多，无法增量，全清
        mDb.runInBackground(() -> {
            mDao.update(task);
            notifyTaskDataChanged();
        });
    }

    public void updateSync(TaskEntity task) {
        assertNotMainThread();
        mCachedActiveTasks = null;
        mDao.update(task);
        notifyTaskDataChanged();
    }

    public void delete(long taskId) {
        mDb.runInBackground(() -> {
            mDb.runInTransaction(() -> {
                mDao.delete(taskId);
                mDegradeDao.deleteByTaskId(taskId);
            });
            if (mCachedActiveTasks != null) mCachedActiveTasks.removeIf(t -> t.id == taskId);
            if (mCachedDegrades != null) mCachedDegrades.removeIf(d -> d.taskId == taskId);
            notifyTaskDataChanged();
        });
    }

    /** 同步删除任务（供 ViewModel 在同一后台线程中调用，确保删除落盘后再 loadData） */
    public void deleteSync(long taskId) {
        assertNotMainThread();
        mDb.runInTransaction(() -> {
            mDao.delete(taskId);
            mDegradeDao.deleteByTaskId(taskId);
        });
        if (mCachedActiveTasks != null) mCachedActiveTasks.removeIf(t -> t.id == taskId);
        if (mCachedDegrades != null) mCachedDegrades.removeIf(d -> d.taskId == taskId);
        notifyTaskDataChanged();
    }

    /** 设置任务开始执行 */
    public void startExecution(long taskId) {
        mCachedActiveTasks = null;
        mDb.runInBackground(() -> {
            mDao.setExecutingStartMs(taskId, System.currentTimeMillis());
            notifyTaskDataChanged();
        });
    }

    public void startExecutionSync(long taskId, long startMs) {
        assertNotMainThread();
        mDao.setExecutingStartMs(taskId, startMs);
        mCachedActiveTasks = null;
        notifyTaskDataChanged();
    }

    /** 设置任务执行结束时间 */
    public void endExecution(long taskId, long endMs) {
        mCachedActiveTasks = null;
        mDb.runInBackground(() -> {
            mDao.setExecutingEndMs(taskId, endMs);
            notifyTaskDataChanged();
        });
    }

    /** 清除执行状态（归档时使用） */
    public void clearExecution(long taskId) {
        mCachedActiveTasks = null;
        mDb.runInBackground(() -> {
            mDao.clearExecutingState(taskId);
            notifyTaskDataChanged();
        });
    }

    public void clearExecutionSync(long taskId) {
        assertNotMainThread();
        mDao.clearExecutingState(taskId);
        mCachedActiveTasks = null;
        notifyTaskDataChanged();
    }

    /** 将任务改为琐碎：focus_minutes = 0 + 清空执行中状态。 */
    public void convertToChoreSync(long taskId) {
        assertNotMainThread();
        mDao.convertToChore(taskId);
        mCachedActiveTasks = null;
        notifyTaskDataChanged();
    }

    /** 同步获取全部未归档任务（供后台计算使用）。返回防御性拷贝，调用方可安全修改。 */
    public List<TaskEntity> getAllActiveTasksSync() {
        if (mCachedActiveTasks != null) {
            return new ArrayList<>(mCachedActiveTasks);
        }
        List<TaskEntity> result = mDao.getAllActiveTasksSync();
        mCachedActiveTasks = new CopyOnWriteArrayList<>(result);
        return new ArrayList<>(result);
    }

    public int getMaxActiveFocusMinutesSync() {
        assertNotMainThread();
        return mDao.getMaxActiveFocusMinutesSync();
    }

    /** 写入降级记录（完成时调用，覆盖已有记录） */
    public void insertDegradeSync(long taskId, int originalQuadrant, long recoverMs) {
        TaskQuadrantDegradeEntity entity = new TaskQuadrantDegradeEntity();
        entity.taskId = taskId;
        entity.originalQuadrant = originalQuadrant;
        entity.recoverMs = recoverMs;
        mDegradeDao.insert(entity);
        if (mCachedDegrades != null) mCachedDegrades.add(entity);
    }

    /** 删除降级记录（象限变更/删除/归档时调用） */
    public void deleteDegradeSync(long taskId) {
        mDegradeDao.deleteByTaskId(taskId);
        if (mCachedDegrades != null) mCachedDegrades.removeIf(d -> d.taskId == taskId);
    }

    /** 查询全部降级记录（供 recompute 使用）。返回防御性拷贝，调用方可安全修改。 */
    public List<TaskQuadrantDegradeEntity> getAllDegradesSync() {
        if (mCachedDegrades != null) {
            return new ArrayList<>(mCachedDegrades);
        }
        List<TaskQuadrantDegradeEntity> result = mDegradeDao.queryAll();
        mCachedDegrades = new CopyOnWriteArrayList<>(result);
        return new ArrayList<>(result);
    }

    /** 返回未过期的降级记录 Map（taskId -> degrade），复用缓存 */
    public Map<Long, TaskQuadrantDegradeEntity> getNonExpiredDegradeMapSync() {
        List<TaskQuadrantDegradeEntity> degrades = getAllDegradesSync();
        Map<Long, TaskQuadrantDegradeEntity> map = new HashMap<>();
        if (degrades != null) {
            long now = System.currentTimeMillis();
            for (TaskQuadrantDegradeEntity d : degrades) {
                if (now < d.recoverMs) map.put(d.taskId, d);
            }
        }
        return map;
    }

    /** 获取当前执行中的任务。 */
    public TaskEntity getRunningTaskSync() {
        return mDao.getRunningTaskSync();
    }

    /** 按 ID 查询任务（同步） */
    public TaskEntity getTaskByIdSync(long id) {
        return mDao.getTaskByIdSync(id);
    }

    /** 含 APP 跳转附加模块的未归档任务（同步），按创建时间倒序 */
    public List<TaskEntity> getTasksWithAppActionSync() {
        assertNotMainThread();
        return mDao.getTasksWithAppActionSync();
    }

    /** 批量按 ID 查询任务（同步） */
    public List<TaskEntity> getTasksByIdsSync(List<Long> ids) {
        return mDao.getTasksByIdsSync(ids);
    }

    /**
     * 多 token 全文检索（按比例命中）。
     * 至少 ceil(tokens/2) 个 token 匹配 content 或 detail 即视为命中，按得分降序。
     */
    public List<TaskEntity> searchTasksByTokens(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) return new ArrayList<>();

        int minMatch = (tokens.size() + 1) / 2; // ceil(tokenCount / 2)
        StringBuilder sql = new StringBuilder(
            "SELECT * FROM (SELECT *, (");
        Object[] args = new Object[tokens.size() * 2 + 1];

        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) sql.append(" + ");
            sql.append("CASE WHEN content LIKE '%' || ? || '%' OR detail LIKE '%' || ? || '%' THEN 1 ELSE 0 END");
            args[i * 2] = tokens.get(i);
            args[i * 2 + 1] = tokens.get(i);
        }
        // minMatch 是最后一个参数
        args[tokens.size() * 2] = minMatch;

        sql.append(") AS score FROM tasks WHERE is_archived = 0) WHERE score >= ? ORDER BY score DESC, created_at DESC");
        return mDao.searchTasksSync(new SimpleSQLiteQuery(sql.toString(), args));
    }

    private void notifyTaskDataChanged() {
        DataChangeDispatcher.notifyTaskDataChanged();
    }
}
