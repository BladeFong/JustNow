package com.nearby.justnow.data.repository;

import com.nearby.justnow.data.dao.TaskAppActionDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskAppAction;

import java.util.List;

/**
 * APP 跳转列表仓库
 */
public class TaskAppActionRepository extends BaseRepository {

    private final TaskAppActionDao mDao;

    public TaskAppActionRepository(AppDatabase db) {
        super(db);
        mDao = db.taskAppActionDao();
    }

    public List<TaskAppAction> getByTaskIdSync(long taskId) {
        return mDao.getByTaskIdSync(taskId);
    }

    /** 批量替换某任务的跳转列表（先删后插） */
    public void replaceAllByTaskId(long taskId, List<TaskAppAction> actions) {
        mDb.runInBackground(() -> {
            replaceAllByTaskIdSync(taskId, actions);
        });
    }

    public void replaceAllByTaskIdSync(long taskId, List<TaskAppAction> actions) {
        mDb.runInTransaction(() -> {
            mDao.deleteByTaskId(taskId);
            if (actions != null && !actions.isEmpty()) {
                mDao.insertAll(actions);
            }
        });
    }

    public void deleteByTaskIdSync(long taskId) {
        mDao.deleteByTaskId(taskId);
    }
}
