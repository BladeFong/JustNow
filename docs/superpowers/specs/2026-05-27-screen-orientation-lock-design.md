# 全局横竖屏锁定设计

> 设计日期 2026-05-27

## 概述

根据设备类型全局锁定屏幕方向：手机强制竖屏，平板强制横屏。一处配置全局生效，无需逐 Activity 设置。

## 实现方式

`Application.onCreate()` 中通过 `registerActivityLifecycleCallbacks` 统一处理：

```java
// App.java
@Override
public void onCreate() {
    super.onCreate();
    registerActivityLifecycleCallbacks(new SimpleActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            boolean isTablet = (activity.getResources().getConfiguration().screenLayout
                    & Configuration.SCREENLAYOUT_SIZE_MASK)
                    >= Configuration.SCREENLAYOUT_SIZE_LARGE;
            activity.setRequestedOrientation(isTablet
                    ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    });
}
```

## 判定标准

| 设备 | `SCREENLAYOUT_SIZE_MASK` | 方向 |
|------|--------------------------|------|
| 手机 | `< LARGE` | `PORTRAIT` |
| 平板 | `>= LARGE`（含 LARGE / XLARGE） | `LANDSCAPE` |

`SCREENLAYOUT_SIZE_LARGE` 对应最小宽度 ≥ 640dp × 480dp（Android 官方定义）。

## 改动范围

- `App.java`：新增 `onCreate` + `registerActivityLifecycleCallbacks`
- Manifest 中所有 Activity 无需逐个设置 `screenOrientation`

## 边界情况

| 场景 | 处理 |
|------|------|
| 折叠屏展开/闭合 | `onConfigurationChanged` 触发回调，方向自动切换 |
| 窗口模式（分屏/小窗） | Android 在 `PORTRAIT`/`LANDSCAPE` 约束下窗口尺寸变化不影响方向，Activity 保持在当前约束内 |
