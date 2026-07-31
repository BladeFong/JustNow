# 短完成对话框按钮文案优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将短完成引导对话框（`ShortCompletionDialog`）中的“直接完成”按钮文案修改为“完成本次”，并直接复用既有的 `R.string.s_complete_once` 字符串资源。

**Architecture:** 修改 `ShortCompletionDialog` 挂载 `NegativeButton` 的字符串资源引用，清理不再使用的旧资源 `s_short_completion_btn_direct_complete`，同步更新模块文档 `modules/task-execution.md`，并添加针对 `ShortCompletionDialog` 按钮文字显示逻辑的 Robolectric 单元测试。

**Tech Stack:** Java, Android Framework (AlertDialog), Robolectric, JUnit4.

## Global Constraints

- 保持既有 `ShortCompletionDialog` 按钮挂载逻辑与回调不变
- 复用既有字符串资源 `R.string.s_complete_once`
- 清理无引用的 `s_short_completion_btn_direct_complete`
- 使用简体中文对话与文档说明，保持英文代码标识符

---

### Task 1: 编写 ShortCompletionDialog 按钮文案单元测试

**Files:**
- Create: `app/src/test/java/com/nearby/justnow/ui/main/ShortCompletionDialogTest.java`

**Interfaces:**
- Consumes: `ShortCompletionDialog.show(Context, String, int, boolean, Callback)`
- Produces: 验证对话框显示的三个按钮文案正确（特别是 NegativeButton 显示“完成本次”）

- [ ] **Step 1: 编写 ShortCompletionDialogTest 测试用例**

```java
package com.nearby.justnow.ui.main;

import android.content.Context;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.nearby.justnow.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ShortCompletionDialogTest {

    @Test
    public void show_negativeButton_displaysCompleteOnce() {
        Context context = RuntimeEnvironment.getApplication();
        ShortCompletionDialog.show(context, "测试任务", ShortCompletionDialog.ENTRY_COMPLETE_ONCE, false, new ShortCompletionDialog.Callback() {
            @Override public void onCancel() {}
            @Override public void onDirectComplete() {}
            @Override public void onConvertToChore() {}
        });

        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        assertNotNull(dialog);

        Button negativeBtn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        assertNotNull(negativeBtn);
        String expectedText = context.getString(R.string.s_complete_once);
        assertEquals(expectedText, negativeBtn.getText().toString());
    }
}
```

- [ ] **Step 2: 运行测试以验证失败（当前代码仍返回旧文案）**

Run: `./gradlew testDebugUnitTest --tests com.nearby.justnow.ui.main.ShortCompletionDialogTest`
Expected: FAIL (期望“完成本次”，实际为“直接完成” / Complete as is)

---

### Task 2: 实施 ShortCompletionDialog 文案修改与资源清理

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/ShortCompletionDialog.java`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`
- Modify: `modules/task-execution.md`

**Interfaces:**
- Consumes: `R.string.s_complete_once`
- Produces: 调整后的 `ShortCompletionDialog`

- [ ] **Step 1: 修改 ShortCompletionDialog.java**

将第 83 行中的 `R.string.s_short_completion_btn_direct_complete` 替换为 `R.string.s_complete_once`：

```java
            .setNeutralButton(R.string.s_cancel, (d, w) -> callback.onCancel())
            .setNegativeButton(R.string.s_complete_once,
                (d, w) -> callback.onDirectComplete())
            .setPositiveButton(convertButtonRes,
                (d, w) -> callback.onConvertToChore())
```

- [ ] **Step 2: 从 strings.xml（及各语言版本）中删除无引用的 `s_short_completion_btn_direct_complete`**

删除各 `strings.xml` 文件中形如以下内容：
`<string name="s_short_completion_btn_direct_complete">...</string>`

- [ ] **Step 3: 更新 modules/task-execution.md 中的说明**

将文档中描述 `< 15min 完成引导` 按钮矩阵中的 `直接完成` 更新为 `完成本次`。

- [ ] **Step 4: 运行单元测试验证通过**

Run: `./gradlew testDebugUnitTest --tests com.nearby.justnow.ui.main.ShortCompletionDialogTest`
Expected: PASS

- [ ] **Step 5: 运行全量单元测试与编译检查**

Run: `./gradlew compileDebugJavaWithJavac testDebugUnitTest`
Expected: BUILD SUCCESSFUL, ALL TESTS PASSED
