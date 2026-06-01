package com.nearby.justnow.data.holiday;

import android.util.Log;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * holiday-cn JSON 格式解析器。
 * 从 NateScarlet/holiday-cn 的 {year}.json 提取节假日数据。
 * 输出与 IcsParser 一致的缓存 JSON 格式。
 */
public final class HolidayJsonParser {

    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private HolidayJsonParser() {
    }

    /**
     * 解析 holiday-cn JSON，填充 entity 的 dataJson 和 holidayCount。
     */
    public static void fill(HolidayCacheEntity entity, String jsonText, int year, String source) {
        if (entity == null || jsonText == null || jsonText.isEmpty()) return;
        try {
            Set<String> holidays = new LinkedHashSet<>();
            Set<String> workdays = new LinkedHashSet<>();
            LocalDate springStart = null;
            LocalDate springEnd = null;

            // 逐行解析 days 数组中的每个对象
            int idx = jsonText.indexOf("\"days\"");
            if (idx < 0) return;
            idx = jsonText.indexOf("[", idx);

            while (idx >= 0) {
                int objStart = jsonText.indexOf("{", idx);
                if (objStart < 0) break;
                int objEnd = jsonText.indexOf("}", objStart);
                if (objEnd < 0) break;
                String obj = jsonText.substring(objStart + 1, objEnd);

                String name = extractValue(obj, "name");
                String date = extractValue(obj, "date");
                String isOffDay = extractValue(obj, "isOffDay");

                if (name == null || date == null) {
                    idx = objEnd + 1;
                    continue;
                }

                boolean off = "true".equals(isOffDay);

                if (off) {
                    holidays.add(date);
                } else {
                    workdays.add(date);
                }

                // 春节区间：name="春节" 且 isOffDay=true 的连续区间
                if ("春节".equals(name) && off) {
                    LocalDate d = LocalDate.parse(date, sDateFormat);
                    if (springStart == null || d.isBefore(springStart)) springStart = d;
                    if (springEnd == null || d.isAfter(springEnd)) springEnd = d;
                }

                idx = objEnd + 1;
                if (jsonText.indexOf("{", idx) < 0) break;
            }

            entity.dataJson = buildJson(year, source, holidays, workdays, springStart, springEnd);
            entity.holidayCount = holidays.size();
        } catch (Exception e) {
            Log.w("HolidayJsonParser", "parse failed", e);
        }
    }

    private static String extractValue(String obj, String key) {
        String search = "\"" + key + "\":";
        int start = obj.indexOf(search);
        if (start < 0) return null;
        start += search.length();

        // 跳过空白
        while (start < obj.length() && obj.charAt(start) == ' ') {
            start++;
        }

        if (start >= obj.length()) return null;

        // 无引号值（布尔、数字、null）：结束于逗号或右花括号
        if (obj.charAt(start) != '"') {
            int end = obj.indexOf(",", start);
            if (end < 0) end = obj.indexOf("}", start);
            if (end < 0) end = obj.length();
            String val = obj.substring(start, end).trim();
            return val.isEmpty() ? null : val;
        }

        // 带引号的字符串值：跳过左引号，找右引号
        start++;
        int end = obj.indexOf("\"", start);
        if (end < 0) end = obj.indexOf(",", start);
        if (end < 0) end = obj.length();
        String val = obj.substring(start, end).trim();
        // 去掉尾巴上误入的引号
        if (val.endsWith("\"")) val = val.substring(0, val.length() - 1);
        return val.isEmpty() ? null : val;
    }

    private static String buildJson(int year, String source, Set<String> holidays,
                                     Set<String> workdays,
                                     LocalDate springStart, LocalDate springEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"year\":").append(year)
          .append(",\"source\":\"").append(escapeJson(source)).append("\"");

        // holidays
        sb.append(",\"holidays\":[");
        boolean first = true;
        for (String h : holidays) {
            if (!first) sb.append(",");
            sb.append("\"").append(h).append("\"");
            first = false;
        }
        sb.append("]");

        // makeupWorkdays
        sb.append(",\"makeupWorkdays\":[");
        first = true;
        for (String w : workdays) {
            if (!first) sb.append(",");
            sb.append("\"").append(w).append("\"");
            first = false;
        }
        sb.append("]");

        // festivals
        if (springStart != null && springEnd != null) {
            sb.append(",\"festivals\":[")
              .append("{\"type\":\"spring_festival\"")
              .append(",\"start\":\"").append(springStart.format(sDateFormat)).append("\"")
              .append(",\"end\":\"").append(springEnd.format(sDateFormat)).append("\"}")
              .append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
