package com.nearby.justnow.ui.engine;

import android.content.Context;

import com.nearby.justnow.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 展示策略 YAML 原文存储。
 */
public class DisplayPolicyStore {

    private static final String DEFAULT_ASSET_PATH = "display_policy/default.yaml";
    private static final String DIR_NAME = "display_policy";
    private static final String FILE_NAME = "current.yaml";

    private final Context mContext;

    public DisplayPolicyStore(Context context) {
        mContext = context.getApplicationContext();
    }

    public String readDefaultYaml() throws IOException {
        try (InputStream input = mContext.getAssets().open(DEFAULT_ASSET_PATH)) {
            return readAll(input);
        }
    }

    public boolean hasCustomYaml() {
        return getCustomFile().isFile();
    }

    public String readCustomYaml() throws IOException {
        try (InputStream input = new FileInputStream(getCustomFile())) {
            return readAll(input);
        }
    }

    public void writeCustomYaml(String yamlText) throws IOException {
        File file = getCustomFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException(mContext.getString(R.string.s_display_policy_create_dir_failed));
        }
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(yamlText.getBytes(StandardCharsets.UTF_8));
        }
    }

    public void deleteCustomYaml() throws IOException {
        File file = getCustomFile();
        if (file.exists() && !file.delete()) {
            throw new IOException(
                    mContext.getString(R.string.s_display_policy_delete_custom_failed));
        }
    }

    private File getCustomFile() {
        return new File(new File(mContext.getFilesDir(), DIR_NAME), FILE_NAME);
    }

    private static String readAll(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        StringBuilder sb = new StringBuilder();
        int len;
        while ((len = input.read(buffer)) >= 0) {
            sb.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
