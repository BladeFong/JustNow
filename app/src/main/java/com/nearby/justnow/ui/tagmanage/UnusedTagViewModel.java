package com.nearby.justnow.ui.tagmanage;

import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.data.entity.TagEntity;
import com.nearby.justnow.data.repository.TagRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * 未使用标签 ViewModel — 加载 + 删除
 */
public class UnusedTagViewModel extends BaseViewModel {

    private final TagRepository mTagRepo;

    public UnusedTagViewModel(JustNowApplication app) {
        super(app);
        mTagRepo = app.getTagRepository();
    }

    public List<TagEntity> getUnusedTagsSync() {
        List<TagEntity> tags = mTagRepo.getUnusedTagsSync();
        return tags != null ? tags : new ArrayList<>();
    }

    public void deleteTags(List<Long> tagIds) {
        mTagRepo.deleteTags(tagIds);
    }

    /** 后台加载未使用标签并回调到主线程（供 Fragment 使用）。 */
    public void loadUnusedTagsAsync(java.util.function.Consumer<List<TagEntity>> callback) {
        runInBackground(() -> {
            List<TagEntity> tags = getUnusedTagsSync();
            runOnUiThread(() -> callback.accept(tags));
        });
    }
}
