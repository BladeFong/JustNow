package com.nearby.justnow.ui.engine;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class DisplayPolicyParserTest {

    private final DisplayPolicyParser mParser = new DisplayPolicyParser(
            (messageResId, args) -> messageResId + " " + Arrays.toString(args));

    private static final String VALID_YAML =
        "# 智能展示策略配置\n"
            + "version: 1\n"
            + "priority:\n"
            + "  order:\n"
            + "    - schedule_priority\n"
            + "    - tag_priority\n"
            + "    - quadrant\n"
            + "    - focus_duration\n"
            + "time:\n"
            + "  fit_tolerance_minutes: 15\n"
            + "  focus_max_minutes: 120\n"
            + "  focus_duration_order: desc\n"
            + "ratio:\n"
            + "  quadrant: [4, 2, 2, 1]\n";

    @Test
    public void parse_validYaml_success() throws Exception {
        DisplayPolicy policy = mParser.parse(VALID_YAML);

        assertEquals(1, policy.getVersion());
        assertEquals(15, policy.getFitToleranceMinutes());
        assertEquals(120, policy.getFocusMaxMinutes());
        assertEquals(DisplayPolicy.FocusDurationOrder.DESC, policy.getFocusDurationOrder());
        assertArrayEquals(new int[]{4, 2, 2, 1}, policy.getQuadrantRatio());
        assertEquals(Arrays.asList(
            DisplayPolicy.PriorityRule.SCHEDULE_PRIORITY,
            DisplayPolicy.PriorityRule.TAG_PRIORITY,
            DisplayPolicy.PriorityRule.QUADRANT,
            DisplayPolicy.PriorityRule.FOCUS_DURATION
        ), policy.getPriorityOrder());
    }

    @Test
    public void parse_duplicatePriorityRule_throws() {
        assertInvalid(VALID_YAML.replace("    - quadrant\n", "    - tag_priority\n"));
    }

    @Test
    public void parse_unknownPriorityRule_throws() {
        assertInvalid(VALID_YAML.replace("    - quadrant\n", "    - unknown\n"));
    }

    @Test
    public void parse_invalidRatioSize_throws() {
        assertInvalid(VALID_YAML.replace("[4, 2, 2, 1]", "[4, 2, 2]"));
    }

    @Test
    public void parse_negativeRatio_throws() {
        assertInvalid(VALID_YAML.replace("[4, 2, 2, 1]", "[4, -1, 2, 1]"));
    }

    @Test
    public void parse_zeroRatioSum_throws() {
        assertInvalid(VALID_YAML.replace("[4, 2, 2, 1]", "[0, 0, 0, 0]"));
    }

    @Test
    public void parse_invalidFocusMax_throws() {
        assertInvalid(VALID_YAML.replace("focus_max_minutes: 120", "focus_max_minutes: 180"));
    }

    @Test
    public void parse_invalidFocusDurationOrder_throws() {
        assertInvalid(VALID_YAML.replace("focus_duration_order: desc", "focus_duration_order: middle"));
    }

    private void assertInvalid(String yaml) {
        try {
            mParser.parse(yaml);
            fail("Expected DisplayPolicyValidationException");
        } catch (DisplayPolicyValidationException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
