# 应用体积优化设计文档

## 问题描述

应用大小 22.44MB，偏大。

## 问题分析

**APK 内容分析**：

| 组件 | 大小 | 说明 |
|------|------|------|
| `classes.dex` | 11.5MB | 主 dex，未开启 R8 混淆 |
| `classes16.dex` | 4MB | jieba 分词相关类 |
| `dict.txt` | 5MB | jieba 词典文件 |
| `resources.arsc` | 1.3MB | 资源表 |

**两大元凶**：
1. jieba 分词库（~9MB）
2. 未开启 R8 混淆压缩

## 解决方案

### 开启 R8 混淆（APK 22MB → ~15-17MB）

**修改文件**：`app/build.gradle.kts`、`proguard-rules.pro`

**修改内容**：

`build.gradle.kts`：
```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(...)
}
```

`proguard-rules.pro`：
```proguard
# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# 反射（NumberPickerStyleHelper）
-keepclassmembers class android.widget.NumberPicker {
    private android.graphics.Paint mSelectorWheelPaint;
}

# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# jieba 分词
-keep class com.huaban.analysis.jieba.** { *; }
-dontwarn com.huaban.analysis.jieba.**
```

**预期效果**：
- `classes.dex`：11.5MB → ~6-8MB
- `classes16.dex`：4MB → ~2-3MB
- 总计：22MB → ~15-17MB

## 风险评估

### R8 混淆风险

**已识别的反射代码**：
- `NumberPickerStyleHelper.java:45`：访问 `NumberPicker` 的内部字段 `mSelectorWheelPaint`
- 这是访问 Android 框架类，R8 不会影响

**Room 注解处理**：
- Room 使用编译期注解处理器，不是运行时反射
- R8 不影响

**结论**：开启 R8 是安全的

### 性能影响

**R8 混淆**：
- 运行时性能无影响
- 编译时间可能略有增加

## 验证方式

1. 构建 release APK（`assembleRelease`）
2. 查看 APK 大小
3. 预期：从 22MB 降到 ~15-17MB

**注意**：R8 混淆仅对 release 构建生效，debug 构建不受影响。
