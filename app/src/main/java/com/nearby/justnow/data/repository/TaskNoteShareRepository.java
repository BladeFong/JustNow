package com.nearby.justnow.data.repository;

import com.nearby.justnow.data.dao.TaskNoteShareDao;
import com.nearby.justnow.data.db.AppDatabase;
import com.nearby.justnow.data.entity.TaskNoteShare;

import java.util.List;

/**
 * 笔记分享列表仓库
 */
public class TaskNoteShareRepository extends BaseRepository {

    private final TaskNoteShareDao mDao;

    public TaskNoteShareRepository(AppDatabase db) {
        super(db);
        mDao = db.taskNoteShareDao();
    }

    public List<TaskNoteShare> getByTaskIdSync(long taskId) {
        return mDao.getByTaskIdSync(taskId);
    }

    /** 批量替换某任务的笔记分享列表（先删后插） */
    public void replaceAllByTaskId(long taskId, List<TaskNoteShare> shares) {
        mDb.runInBackground(() -> replaceAllByTaskIdSync(taskId, shares));
    }

    public void replaceAllByTaskIdSync(long taskId, List<TaskNoteShare> shares) {
        mDb.runInTransaction(() -> {
            mDao.deleteByTaskId(taskId);
            if (shares != null && !shares.isEmpty()) {
                mDao.insertAll(shares);
            }
        });
    }

    public void deleteByTaskIdSync(long taskId) {
        mDao.deleteByTaskId(taskId);
    }
}
