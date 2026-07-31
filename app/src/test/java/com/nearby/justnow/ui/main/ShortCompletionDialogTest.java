package com.nearby.justnow.ui.main;

import android.content.Context;
import android.widget.Button;

import android.view.ContextThemeWrapper;

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
        Context context = new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_JustNow);
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
