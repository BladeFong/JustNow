package com.nearby.justnow.data.repository;

import com.nearby.justnow.data.dao.TaskChecklistItemDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskChecklistItem;

import java.util.List;

/**
 * todo 勾选清单仓库
 */
public class TaskChecklistRepository extends BaseRepository {

    private final TaskChecklistItemDao mDao;

    public TaskChecklistRepository(AppDatabase db) {
        super(db);
        mDao = db.taskChecklistItemDao();
    }

    public List<TaskChecklistItem> getByTaskIdSync(long taskId) {
        return mDao.getByTaskIdSync(taskId);
    }

    /** 是否有任何勾选或划掉状态 */
    public boolean hasAnyStateSync(long taskId) {
        return mDao.countHasStateSync(taskId) > 0;
    }

    /** 重置所有条目的勾选和划掉状态 */
    public void resetAllByTaskId(long taskId) {
        mDb.runInBackground(() -> mDao.resetAllByTaskId(taskId));
    }

    public void resetAllByTaskIdSync(long taskId) {
        mDao.resetAllByTaskId(taskId);
    }

    /** 批量替换某任务的清单条目（先删后插） */
    public void replaceAllByTaskIdSync(long taskId, List<TaskChecklistItem> items) {
        mDb.runInTransaction(() -> {
            mDao.deleteByTaskId(taskId);
            if (items != null && !items.isEmpty()) {
                mDao.insertAll(items);
            }
        });
    }

    public void deleteByTaskIdSync(long taskId) {
        mDao.deleteByTaskId(taskId);
    }

    /** 更新单条勾选/划掉状态（详情页即时写库） */
    public void updateState(TaskChecklistItem item) {
        mDb.runInBackground(() -> mDao.update(item));
    }
}
