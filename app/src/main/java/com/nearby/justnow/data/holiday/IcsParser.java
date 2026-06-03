package com.nearby.justnow.data.holiday;

import android.util.Log;

import com.nearby.justnow.data.entity.HolidayCacheEntity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ICS（iCalendar）解析工具。
 * 从 holiday-cn 等 ICS 订阅中提取假日日期和长假起止。
 */
public final class IcsParser {

    private static final DateTimeFormatter sDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private IcsParser() {
    }

    /**
     * 解析 ICS 文本，填充 entity 的 dataJson 和 holidayCount。
     */
    public static void fill(HolidayCacheEntity entity, String icsText, int year, String source) {
        if (entity == null || icsText == null || icsText.isEmpty()) return;
        try {
            List<IcsEvent> events = extractEvents(icsText, year);
            buildJson(year, source, events, entity);
        } catch (Exception e) {
            Log.w("IcsParser", "parse failed", e);
        }
    }

    /**
     * 一次搜索判断日期在节假日缓存中的归属，与具体时段组类型无关。
     * @return null=缓存中无此日期 / TRUE=法定假日 / FALSE=补班日
     */
    public static Boolean isOffDay(String cachedJson, LocalDate date) {
        if (cachedJson == null || cachedJson.isEmpty()) return null;
        String dateStr = date.format(sDateFormat);
        try {
            JSONObject json = new JSONObject(cachedJson);
            JSONArray holidaysArr = json.optJSONArray("holidays");
            if (holidaysArr != null) {
                for (int i = 0; i < holidaysArr.length(); i++) {
                    if (dateStr.equals(holidaysArr.optString(i))) return true;
                }
            }
            JSONArray workdaysArr = json.optJSONArray("makeupWorkdays");
            if (workdaysArr == null) workdaysArr = json.optJSONArray("workdays"); // 兼容旧 key
            if (workdaysArr != null) {
                for (int i = 0; i < workdaysArr.length(); i++) {
                    if (dateStr.equals(workdaysArr.optString(i))) return false;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 JSON 缓存中获取指定类型的节日起止日期。
     * 返回 [start, end] 或 null。
     */
    public static LocalDate[] getFestivalRange(String cachedJson, String festivalType) {
        if (cachedJson == null || cachedJson.isEmpty()) return null;
        try {
            String key = "\"type\":\"" + festivalType + "\"";
            int typeIdx = cachedJson.indexOf(key);
            if (typeIdx < 0) return null;
            String start = extractJsonString(cachedJson, "start", typeIdx);
            String end = extractJsonString(cachedJson, "end", typeIdx);
            if (start == null || end == null) return null;
            return new LocalDate[]{
                LocalDate.parse(start, sDateFormat),
                LocalDate.parse(end, sDateFormat)
            };
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 内部实现 ----

    private static List<IcsEvent> extractEvents(String icsText, int year) {
        List<IcsEvent> events = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(icsText))) {
            String line;
            IcsEvent current = null;
            while ((line = reader.readLine()) != null) {
                if ("BEGIN:VEVENT".equals(line)) {
                    current = new IcsEvent();
                } else if ("END:VEVENT".equals(line) && current != null) {
                    if (current.startDate != null && current.startDate.getYear() == year) {
                        events.add(current);
                    }
                    current = null;
                } else if (current != null) {
                    if (line.startsWith("DTSTART;VALUE=DATE:")) {
                        current.startDate = parseDate(line.substring("DTSTART;VALUE=DATE:".length()));
                    } else if (line.startsWith("DTEND;VALUE=DATE:")) {
                        current.endDate = parseDate(line.substring("DTEND;VALUE=DATE:".length()));
                    } else if (line.startsWith("SUMMARY:")) {
                        current.summary = line.substring("SUMMARY:".length());
                    }
                }
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
        return events;
    }

    private static void buildJson(int year, String source, List<IcsEvent> events,
                                   HolidayCacheEntity entity) {
        Set<String> holidays = new LinkedHashSet<>();
        List<FestivalRange> festivals = new ArrayList<>();

        for (IcsEvent event : events) {
            if (event.startDate == null) continue;
            String summaryLower = event.summary != null ? event.summary.toLowerCase() : "";

            // 填充 holidays
            LocalDate d = event.startDate;
            LocalDate end = event.endDate != null ? event.endDate : event.startDate.plusDays(1);
            while (d.isBefore(end)) {
                holidays.add(d.format(sDateFormat));
                d = d.plusDays(1);
            }

            // 识别春节：收集所有事件后合并为最大范围
            if (isSpringFestival(summaryLower)) {
                LocalDate festivalStart = event.startDate;
                LocalDate festivalEnd = event.endDate != null
                    ? event.endDate.minusDays(1)
                    : event.startDate;
                festivals.add(new FestivalRange("spring_festival", festivalStart, festivalEnd));
            }
        }

        // 合并同类型 festival：取最早开始和最晚结束
        festivals = mergeFestivals(festivals);

        try {
            JSONObject json = new JSONObject();
            json.put("year", year);
            json.put("source", source);
            json.put("holidays", new JSONArray(holidays));
            json.put("makeupWorkdays", new JSONArray());
            if (!festivals.isEmpty()) {
                JSONArray festivalsArr = new JSONArray();
                for (FestivalRange fr : festivals) {
                    JSONObject f = new JSONObject();
                    f.put("type", fr.type);
                    f.put("start", fr.start.format(sDateFormat));
                    f.put("end", fr.end.format(sDateFormat));
                    festivalsArr.put(f);
                }
                json.put("festivals", festivalsArr);
            }
            entity.dataJson = json.toString();
            entity.holidayCount = holidays.size();
        } catch (Exception e) {
            Log.w("IcsParser", "buildJson failed", e);
        }
    }

    /**
     * 同类型 festival 取区间最长的那个（排除调休补班的单天条目干扰）。
     */
    private static List<FestivalRange> mergeFestivals(List<FestivalRange> list) {
        if (list == null || list.size() <= 1) return list;
        java.util.Map<String, FestivalRange> longest = new java.util.LinkedHashMap<>();
        for (FestivalRange fr : list) {
            FestivalRange cur = longest.get(fr.type);
            long dur = fr.end.toEpochDay() - fr.start.toEpochDay();
            if (cur == null || dur > (cur.end.toEpochDay() - cur.start.toEpochDay())) {
                longest.put(fr.type, fr);
            }
        }
        return new ArrayList<>(longest.values());
    }

    private static boolean isSpringFestival(String summaryLower) {
        return summaryLower.contains("spring festival")
            || summaryLower.contains("chinese new year")
            || summaryLower.contains("春节")
            || summaryLower.contains("春節");
    }

    private static LocalDate parseDate(String dateStr) {
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractJsonString(String json, String key, int fromIndex) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey, fromIndex);
        if (start < 0) return null;
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private static class IcsEvent {
        LocalDate startDate;
        LocalDate endDate;
        String summary;
    }

    private static class FestivalRange {
        final String type;
        final LocalDate start;
        final LocalDate end;

        FestivalRange(String type, LocalDate start, LocalDate end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }
}
