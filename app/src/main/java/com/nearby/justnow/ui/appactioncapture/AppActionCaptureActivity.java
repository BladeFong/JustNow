package com.nearby.justnow.ui.appactioncapture;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;

import androidx.appcompat.app.AppCompatActivity;

import java.util.regex.Matcher;

/**
 * 跨应用 Intent 捕获入口 — 无 UI，分流后立即 finish。
 *
 * 入口 A：浏览器跳转 APP 的 ACTION_VIEW（仅非 http(s) 自定义 scheme）
 * 入口 B：系统文本分享 ACTION_SEND（text/plain）
 */
public class AppActionCaptureActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) { finish(); return; }
        String action = intent.getAction();
        String referrerPackage = resolveReferrerPackage();
        String referrerLabel = resolveAppLabel(referrerPackage);

        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data == null || isHttpLikeScheme(data.getScheme())) {
                finish();
                return;
            }
            // 复制一份 Intent 移除 component（避免传给 CapturePicker 后再次 resolve 出本 Activity）
            Intent captured = new Intent(intent);
            captured.setComponent(null);
            captured.setPackage(null);
            launchPicker(captured, /*shortTitle=*/ null, referrerLabel, /*allowNoteEntry=*/ false);
            return;
        }

        if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();
            if (type == null || !type.startsWith("text/")) { finish(); return; }
            String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            String titleExtra = intent.getStringExtra(Intent.EXTRA_TITLE);
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            String shortTitle = pickShorterTitle(subject, titleExtra);
            String url = extractUrl(text);
            if (url != null) {
                Intent viewIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                launchPicker(viewIntent, shortTitle, referrerLabel, /*allowNoteEntry=*/ true);
            } else {
                launchPickerForNote(text, shortTitle, referrerLabel);
            }
            return;
        }

        finish();
    }

    private void launchPicker(Intent capturedIntent, String shortTitle,
                              String referrerLabel, boolean allowNoteEntry) {
        Intent picker = new Intent(this, CapturePickerActivity.class);
        picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        picker.putExtra(CapturePickerActivity.EXTRA_MODE, CapturePickerActivity.MODE_CAPTURE);
        picker.putExtra(CapturePickerActivity.EXTRA_CAPTURED_INTENT_URI,
                capturedIntent.toUri(Intent.URI_INTENT_SCHEME));
        picker.putExtra(CapturePickerActivity.EXTRA_SHORT_TITLE, shortTitle);
        picker.putExtra(CapturePickerActivity.EXTRA_REFERRER_LABEL, referrerLabel);
        picker.putExtra(CapturePickerActivity.EXTRA_ALLOW_NOTE_ENTRY, allowNoteEntry);
        startActivity(picker);
        finish();
    }

    private void launchPickerForNote(String text, String shortTitle, String referrerLabel) {
        Intent picker = new Intent(this, CapturePickerActivity.class);
        picker.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        picker.putExtra(CapturePickerActivity.EXTRA_MODE, CapturePickerActivity.MODE_NOTE);
        picker.putExtra(CapturePickerActivity.EXTRA_NOTE_TEXT, text);
        picker.putExtra(CapturePickerActivity.EXTRA_SHORT_TITLE, shortTitle);
        picker.putExtra(CapturePickerActivity.EXTRA_REFERRER_LABEL, referrerLabel);
        startActivity(picker);
        finish();
    }

    private boolean isHttpLikeScheme(String scheme) {
        if (scheme == null) return false;
        String lower = scheme.toLowerCase();
        return "http".equals(lower) || "https".equals(lower);
    }

    static String pickShorterTitle(String a, String b) {
        boolean ea = a != null && !a.isEmpty();
        boolean eb = b != null && !b.isEmpty();
        if (ea && eb) return a.length() <= b.length() ? a : b;
        if (ea) return a;
        if (eb) return b;
        return null;
    }

    static String extractUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = Patterns.WEB_URL.matcher(text);
        if (m.find()) {
            String u = m.group();
            if (!u.contains("://")) u = "https://" + u;
            return u;
        }
        return null;
    }

    private String resolveReferrerPackage() {
        Uri referrer = getReferrer();
        if (referrer == null) return null;
        if ("android-app".equalsIgnoreCase(referrer.getScheme())) {
            return referrer.getHost();
        }
        return null;
    }

    private String resolveAppLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        try {
            PackageManager pm = getPackageManager();
            CharSequence label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            return label != null ? label.toString() : null;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
