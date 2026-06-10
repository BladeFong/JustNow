package com.nearby.justnow.ui.engine;

import android.content.Context;

import com.nearby.justnow.R;
import com.nearby.justnow.data.repository.TaskRepository;

import java.io.IOException;

/**
 * 展示策略仓库：封装 YAML 原文读取、解析和业务校验。
 */
public class DisplayPolicyRepository {

    private final DisplayPolicyStore mStore;
    private final DisplayPolicyParser mParser;
    private final TaskRepository mTaskRepo;
    private final DisplayPolicyMessageProvider mMessages;

    public DisplayPolicyRepository(Context context, TaskRepository taskRepo) {
        this(context, taskRepo, new AndroidDisplayPolicyMessageProvider(context));
    }

    DisplayPolicyRepository(Context context, TaskRepository taskRepo,
                            DisplayPolicyMessageProvider messages) {
        mStore = new DisplayPolicyStore(context);
        mMessages = messages;
        mParser = new DisplayPolicyParser(messages);
        mTaskRepo = taskRepo;
    }

    public DisplayPolicy getEffectivePolicySync() {
        if (mStore.hasCustomYaml()) {
            try {
                return mParser.parse(mStore.readCustomYaml());
            } catch (IOException | DisplayPolicyValidationException ignored) {
                // 自定义配置不可用时回退默认配置，主流程不能被阻塞。
            }
        }
        try {
            return mParser.parse(mStore.readDefaultYaml());
        } catch (IOException | DisplayPolicyValidationException ignored) {
            return DisplayPolicy.defaultPolicy();
        }
    }

    public EditableYaml getEditableYamlSync() {
        if (mStore.hasCustomYaml()) {
            try {
                String yaml = mStore.readCustomYaml();
                try {
                    mParser.parse(yaml);
                    return EditableYaml.custom(yaml);
                } catch (DisplayPolicyValidationException e) {
                    return EditableYaml.invalidCustom(yaml, e.getMessage());
                }
            } catch (IOException e) {
                return EditableYaml.defaultYaml(readDefaultYamlFallback(),
                        mMessages.get(R.string.s_display_policy_custom_unreadable));
            }
        }
        return EditableYaml.defaultYaml(readDefaultYamlFallback(), null);
    }

    public DisplayPolicy saveCustomYamlSync(String yamlText)
            throws IOException, DisplayPolicyValidationException {
        DisplayPolicy oldPolicy = getEffectivePolicySync();
        DisplayPolicy newPolicy = mParser.parse(yamlText);
        validateFocusMaxChange(oldPolicy, newPolicy);
        mStore.writeCustomYaml(yamlText);
        return newPolicy;
    }

    public DisplayPolicy resetToDefaultSync()
            throws IOException, DisplayPolicyValidationException {
        DisplayPolicy oldPolicy = getEffectivePolicySync();
        String defaultYaml = mStore.readDefaultYaml();
        DisplayPolicy defaultPolicy = mParser.parse(defaultYaml);
        validateFocusMaxChange(oldPolicy, defaultPolicy);
        mStore.deleteCustomYaml();
        return defaultPolicy;
    }

    public String readDefaultYamlSync() {
        return readDefaultYamlFallback();
    }

    private void validateFocusMaxChange(DisplayPolicy oldPolicy, DisplayPolicy newPolicy)
            throws DisplayPolicyValidationException {
        if (newPolicy.getFocusMaxMinutes() >= oldPolicy.getFocusMaxMinutes()) {
            return;
        }
        int maxActiveFocusMinutes = mTaskRepo.getMaxActiveFocusMinutesSync();
        if (maxActiveFocusMinutes > newPolicy.getFocusMaxMinutes()) {
            throw new DisplayPolicyValidationException(
                    mMessages.get(R.string.s_display_policy_focus_downgrade_blocked,
                            maxActiveFocusMinutes, newPolicy.getFocusMaxMinutes()));
        }
    }

    private String readDefaultYamlFallback() {
        try {
            return mStore.readDefaultYaml();
        } catch (IOException e) {
            return "";
        }
    }

    public static final class EditableYaml {
        public final String yamlText;
        public final boolean usingDefault;
        public final boolean customInvalid;
        public final String message;

        private EditableYaml(String yamlText, boolean usingDefault,
                             boolean customInvalid, String message) {
            this.yamlText = yamlText;
            this.usingDefault = usingDefault;
            this.customInvalid = customInvalid;
            this.message = message;
        }

        public static EditableYaml custom(String yamlText) {
            return new EditableYaml(yamlText, false, false, null);
        }

        public static EditableYaml invalidCustom(String yamlText, String message) {
            return new EditableYaml(yamlText, false, true, message);
        }

        public static EditableYaml defaultYaml(String yamlText, String message) {
            return new EditableYaml(yamlText, true, false, message);
        }
    }
}
