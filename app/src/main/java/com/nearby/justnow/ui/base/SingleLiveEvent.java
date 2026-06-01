package com.nearby.justnow.ui.base;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单次事件 LiveData：仅在"新值"到来时通知一次 observer，避免 observer 重新注册时
 * 重放历史值。
 *
 * 典型场景：MainFragment 跳转到详情页后再 popBackStack 回主界面，view 重建会让
 * observer 重新注册；普通 MutableLiveData 会立刻把缓存的最近一次值再次推给新
 * observer，导致页面再次执行跳转/弹窗。
 *
 * 实现参考 Google architecture-samples：用 AtomicBoolean 守门，setValue / postValue
 * 会把标志位置 true，observer 触发时通过 compareAndSet(true, false) 抢占，仅首次
 * 抢占成功者会回调 onChanged，重新注册的 observer 因抢不到标志位不会被重放。
 *
 * 注意：本类仅支持单一活跃 observer 场景；如果同时注册多个 observer，仅其中一个
 * 会收到事件。
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

    private final AtomicBoolean mPending = new AtomicBoolean(false);

    @MainThread
    @Override
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        super.observe(owner, t -> {
            if (mPending.compareAndSet(true, false)) {
                observer.onChanged(t);
            }
        });
    }

    @MainThread
    @Override
    public void setValue(@Nullable T value) {
        mPending.set(true);
        super.setValue(value);
    }

    @Override
    public void postValue(@Nullable T value) {
        mPending.set(true);
        super.postValue(value);
    }
}
