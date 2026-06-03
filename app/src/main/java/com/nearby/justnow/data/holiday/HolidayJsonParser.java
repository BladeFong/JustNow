package com.nearby.justnow.data.holiday;

import android.util.Log;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
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

            JSONObject root = new JSONObject(jsonText);
            JSONArray days = root.getJSONArray("days");
            for (int i = 0; i < days.length(); i++) {
                JSONObject day = days.getJSONObject(i);

                if (!day.has("name") || day.isNull("name")) continue;
                if (!day.has("date") || day.isNull("date")) continue;

                String name = day.getString("name");
                String date = day.getString("date");
                boolean off = day.optBoolean("isOffDay", false);

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
            }

            entity.dataJson = buildJson(year, source, holidays, workdays, springStart, springEnd);
            entity.holidayCount = holidays.size();
        } catch (Exception e) {
            Log.w("HolidayJsonParser", "parse failed", e);
        }
    }

    private static String buildJson(int year, String source, Set<String> holidays,
                                     Set<String> workdays,
                                     LocalDate springStart, LocalDate springEnd) {
        try {
            JSONObject root = new JSONObject();
            root.put("year", year);
            root.put("source", source);
            root.put("holidays", new JSONArray(holidays));
            root.put("makeupWorkdays", new JSONArray(workdays));
            if (springStart != null && springEnd != null) {
                JSONObject festival = new JSONObject();
                festival.put("type", "spring_festival");
                festival.put("start", springStart.format(sDateFormat));
                festival.put("end", springEnd.format(sDateFormat));
                JSONArray festivals = new JSONArray();
                festivals.put(festival);
                root.put("festivals", festivals);
            }
            return root.toString();
        } catch (Exception e) {
            Log.w("HolidayJsonParser", "buildJson failed", e);
            return "";
        }
    }

}
