package com.nearby.justnow.util;

import android.widget.EditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MarkdownActionHandlerTest {

    private EditText mEditText;

    @Before
    public void setUp() {
        mEditText = new EditText(RuntimeEnvironment.getApplication());
    }

    @Test
    public void wrapSelection_withSelectedText_wrapsAndSelectsOrPlacesCursor() {
        mEditText.setText("Hello World");
        mEditText.setSelection(6, 11); // "World"

        MarkdownActionHandler.wrapSelection(mEditText, "**", "**");

        assertEquals("Hello **World**", mEditText.getText().toString());
        assertEquals(15, mEditText.getSelectionEnd());
    }

    @Test
    public void wrapSelection_emptySelection_insertsAndPositionsCursorInMiddle() {
        mEditText.setText("Hello ");
        mEditText.setSelection(6, 6);

        MarkdownActionHandler.wrapSelection(mEditText, "**", "**");

        assertEquals("Hello ****", mEditText.getText().toString());
        assertEquals(8, mEditText.getSelectionStart());
        assertEquals(8, mEditText.getSelectionEnd());
    }

    @Test
    public void insertLinePrefix_atStartOfLine_insertsPrefix() {
        mEditText.setText("First line\nSecond line");
        mEditText.setSelection(14); // in "Second line"

        MarkdownActionHandler.insertLinePrefix(mEditText, "- ");

        assertEquals("First line\n- Second line", mEditText.getText().toString());
        assertEquals(16, mEditText.getSelectionStart());
    }

    @Test
    public void insertLinePrefix_heading_insertsAtLineStart() {
        mEditText.setText("Title text");
        mEditText.setSelection(5);

        MarkdownActionHandler.insertLinePrefix(mEditText, "# ");

        assertEquals("# Title text", mEditText.getText().toString());
        assertEquals(7, mEditText.getSelectionStart());
    }

    @Test
    public void insertLinePrefix_toggleOff_ifAlreadyPresent() {
        mEditText.setText("- Bullet point");
        mEditText.setSelection(5);

        MarkdownActionHandler.insertLinePrefix(mEditText, "- ");

        assertEquals("Bullet point", mEditText.getText().toString());
        assertEquals(3, mEditText.getSelectionStart());
    }

    @Test
    public void insertBlock_divider_insertsWithNewlines() {
        mEditText.setText("Top text");
        mEditText.setSelection(8);

        MarkdownActionHandler.insertBlock(mEditText, "---");

        assertEquals("Top text\n\n---\n\n", mEditText.getText().toString());
    }
}
