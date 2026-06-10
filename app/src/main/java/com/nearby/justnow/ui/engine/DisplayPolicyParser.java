package com.nearby.justnow.ui.engine;

import android.content.Context;

import com.nearby.justnow.R;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 展示策略 YAML 解析和 schema 校验。
 */
public class DisplayPolicyParser {

    private final DisplayPolicyMessageProvider mMessages;

    public DisplayPolicyParser(Context context) {
        this(new AndroidDisplayPolicyMessageProvider(context));
    }

    DisplayPolicyParser(DisplayPolicyMessageProvider messages) {
        mMessages = messages;
    }

    public DisplayPolicy parse(String yamlText) throws DisplayPolicyValidationException {
        if (yamlText == null || yamlText.trim().isEmpty()) {
            throw validation(R.string.s_display_policy_empty);
        }

        Object rootObject;
        try {
            rootObject = new Yaml().load(yamlText);
        } catch (YAMLException e) {
            throw validation(e, R.string.s_display_policy_yaml_syntax_error,
                    String.valueOf(e.getMessage()));
        }

        Map<?, ?> root = requireMap(rootObject, "root");
        int version = requireInt(root, "version");
        if (version != DisplayPolicy.VERSION) {
            throw validation(R.string.s_display_policy_unsupported_version, version);
        }

        Map<?, ?> priority = requireMap(root.get("priority"), "priority");
        List<DisplayPolicy.PriorityRule> priorityOrder =
                parsePriorityOrder(requireList(priority, "order"));

        Map<?, ?> time = requireMap(root.get("time"), "time");
        int fitToleranceMinutes = requireInt(time, "fit_tolerance_minutes");
        if (fitToleranceMinutes < 0) {
            throw validation(R.string.s_display_policy_fit_tolerance_negative);
        }

        int focusMaxMinutes = requireInt(time, "focus_max_minutes");
        if (!FocusDurationOptions.isAllowedFocusMaxMinutes(focusMaxMinutes)) {
            throw validation(R.string.s_display_policy_focus_max_invalid);
        }
        if (focusMaxMinutes % FocusDurationOptions.FOCUS_SLOT_MINUTES != 0) {
            throw validation(R.string.s_display_policy_focus_max_not_slot);
        }

        String focusOrderText = requireString(time, "focus_duration_order");
        DisplayPolicy.FocusDurationOrder focusDurationOrder =
                DisplayPolicy.FocusDurationOrder.fromYamlName(focusOrderText);
        if (focusDurationOrder == null) {
            throw validation(R.string.s_display_policy_focus_order_invalid);
        }

        Map<?, ?> ratio = requireMap(root.get("ratio"), "ratio");
        int[] quadrantRatio = parseQuadrantRatio(requireList(ratio, "quadrant"));

        return new DisplayPolicy(version, priorityOrder, fitToleranceMinutes,
                focusMaxMinutes, focusDurationOrder, quadrantRatio);
    }

    private List<DisplayPolicy.PriorityRule> parsePriorityOrder(List<?> values)
            throws DisplayPolicyValidationException {
        if (values.size() != DisplayPolicy.PriorityRule.values().length) {
            throw validation(R.string.s_display_policy_priority_order_size);
        }
        Set<DisplayPolicy.PriorityRule> seen = EnumSet.noneOf(DisplayPolicy.PriorityRule.class);
        List<DisplayPolicy.PriorityRule> result = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof String)) {
                throw validation(R.string.s_display_policy_priority_order_string);
            }
            DisplayPolicy.PriorityRule rule =
                    DisplayPolicy.PriorityRule.fromYamlName((String) value);
            if (rule == null) {
                throw validation(R.string.s_display_policy_priority_unknown, value);
            }
            if (!seen.add(rule)) {
                throw validation(R.string.s_display_policy_priority_duplicate, value);
            }
            result.add(rule);
        }
        if (seen.size() != DisplayPolicy.PriorityRule.values().length) {
            throw validation(R.string.s_display_policy_priority_missing);
        }
        return result;
    }

    private int[] parseQuadrantRatio(List<?> values)
            throws DisplayPolicyValidationException {
        if (values.size() != 4) {
            throw validation(R.string.s_display_policy_ratio_size);
        }
        int[] result = new int[4];
        int sum = 0;
        for (int i = 0; i < values.size(); i++) {
            int value = asInt(values.get(i), "ratio.quadrant[" + i + "]");
            if (value < 0) {
                throw validation(R.string.s_display_policy_ratio_negative);
            }
            result[i] = value;
            sum += value;
        }
        if (sum <= 0) {
            throw validation(R.string.s_display_policy_ratio_sum_zero);
        }
        return result;
    }

    private Map<?, ?> requireMap(Object value, String field)
            throws DisplayPolicyValidationException {
        if (!(value instanceof Map)) {
            throw validation(R.string.s_display_policy_object_required, field);
        }
        return (Map<?, ?>) value;
    }

    private List<?> requireList(Map<?, ?> map, String key)
            throws DisplayPolicyValidationException {
        Object value = map.get(key);
        if (!(value instanceof List)) {
            throw validation(R.string.s_display_policy_list_required, key);
        }
        return (List<?>) value;
    }

    private int requireInt(Map<?, ?> map, String key)
            throws DisplayPolicyValidationException {
        return asInt(map.get(key), key);
    }

    private int asInt(Object value, String field)
            throws DisplayPolicyValidationException {
        if (!(value instanceof Number)) {
            throw validation(R.string.s_display_policy_int_required, field);
        }
        double doubleValue = ((Number) value).doubleValue();
        int intValue = ((Number) value).intValue();
        if (doubleValue != intValue) {
            throw validation(R.string.s_display_policy_int_required, field);
        }
        return intValue;
    }

    private String requireString(Map<?, ?> map, String key)
            throws DisplayPolicyValidationException {
        Object value = map.get(key);
        if (!(value instanceof String)) {
            throw validation(R.string.s_display_policy_string_required, key);
        }
        return (String) value;
    }

    private DisplayPolicyValidationException validation(int messageResId, Object... args) {
        return new DisplayPolicyValidationException(mMessages.get(messageResId, args));
    }

    private DisplayPolicyValidationException validation(Throwable cause, int messageResId,
                                                        Object... args) {
        return new DisplayPolicyValidationException(mMessages.get(messageResId, args), cause);
    }
}
