package com.nearby.justnow.util;

import android.text.Editable;
import android.widget.EditText;

/**
 * Markdown 快捷格式动作处理器 — 处理光标与选区语法包装/插入。
 */
public final class MarkdownActionHandler {

    private MarkdownActionHandler() {}

    /**
     * 包裹选区或在光标处插入对称标记（粗体、斜体、代码等）。
     */
    public static void wrapSelection(EditText editText, String prefix, String suffix) {
        if (editText == null || editText.getText() == null) return;

        Editable editable = editText.getText();
        int selStart = editText.getSelectionStart();
        int selEnd = editText.getSelectionEnd();

        if (selStart < 0) selStart = 0;
        if (selEnd < 0) selEnd = 0;
        if (selStart > selEnd) {
            int tmp = selStart;
            selStart = selEnd;
            selEnd = tmp;
        }

        if (selStart != selEnd) {
            // 有选区
            CharSequence selectedText = editable.subSequence(selStart, selEnd);
            String replacement = prefix + selectedText + suffix;
            editable.replace(selStart, selEnd, replacement);
            editText.setSelection(selStart + replacement.length());
        } else {
            // 无选区：光标处插入并在中间定位
            String insertText = prefix + suffix;
            editable.insert(selStart, insertText);
            editText.setSelection(selStart + prefix.length());
        }
    }

    /**
     * 在光标所在行行首插入或切换行首标记（标题、无序/有序列表、待办复选框、引用等）。
     */
    public static void insertLinePrefix(EditText editText, String prefix) {
        if (editText == null || editText.getText() == null || prefix == null) return;

        Editable editable = editText.getText();
        int cursor = editText.getSelectionStart();
        if (cursor < 0) cursor = 0;

        String content = editable.toString();
        // 查找行首
        int lineStart = content.lastIndexOf('\n', Math.max(0, cursor - 1));
        if (lineStart == -1) {
            lineStart = 0;
        } else {
            lineStart += 1;
        }

        // 查找行尾
        int lineEnd = content.indexOf('\n', lineStart);
        if (lineEnd == -1) {
            lineEnd = content.length();
        }

        String line = content.substring(lineStart, lineEnd);

        // 如果行首已有相同 prefix，则执行反选/删除（Toggle 行为）
        if (line.startsWith(prefix)) {
            editable.delete(lineStart, lineStart + prefix.length());
            int newCursor = Math.max(lineStart, cursor - prefix.length());
            editText.setSelection(Math.min(newCursor, editable.length()));
            return;
        }

        // 如果是标题前缀（例如已有 "# "，点了 "## "），先清理已有 heading 前缀
        if (prefix.startsWith("#") && line.startsWith("#")) {
            int oldHashEnd = 0;
            while (oldHashEnd < line.length() && (line.charAt(oldHashEnd) == '#' || line.charAt(oldHashEnd) == ' ')) {
                oldHashEnd++;
            }
            editable.replace(lineStart, lineStart + oldHashEnd, prefix);
            int newCursor = lineStart + prefix.length() + Math.max(0, cursor - (lineStart + oldHashEnd));
            editText.setSelection(Math.min(newCursor, editable.length()));
            return;
        }

        // 插入前缀
        editable.insert(lineStart, prefix);
        int newCursor = cursor + prefix.length();
        editText.setSelection(Math.min(newCursor, editable.length()));
    }

    /**
     * 插入独立块（如分割线 ---）。
     */
    public static void insertBlock(EditText editText, String blockContent) {
        if (editText == null || editText.getText() == null || blockContent == null) return;

        Editable editable = editText.getText();
        int cursor = editText.getSelectionStart();
        if (cursor < 0) cursor = 0;

        String content = editable.toString();
        StringBuilder sb = new StringBuilder();

        // 前方若非双换行则补足换行
        if (cursor > 0) {
            if (cursor == 1) {
                sb.append(content.charAt(0) == '\n' ? "\n" : "\n\n");
            } else {
                char c1 = content.charAt(cursor - 1);
                char c2 = content.charAt(cursor - 2);
                if (c1 != '\n') {
                    sb.append("\n\n");
                } else if (c2 != '\n') {
                    sb.append("\n");
                }
            }
        }

        sb.append(blockContent);
        sb.append("\n\n");

        String insertStr = sb.toString();
        editable.insert(cursor, insertStr);
        editText.setSelection(Math.min(cursor + insertStr.length(), editable.length()));
    }
}
