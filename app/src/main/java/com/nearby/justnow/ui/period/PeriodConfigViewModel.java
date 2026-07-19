package com.nearby.justnow.ui.period;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.nearby.justnow.ui.base.BaseViewModel;
import com.nearby.justnow.JustNowApplication;
import com.nearby.justnow.R;
import com.nearby.justnow.data.store.PrefsConfig;
import com.nearby.justnow.data.entity.TimePeriodGroupEntity;
import com.nearby.justnow.data.entity.TimePeriodEntity;
import com.nearby.justnow.data.model.PeriodGroupRuleResolver;
import com.nearby.justnow.data.holiday.HolidayCacheManager;
import com.nearby.justnow.data.holiday.IcsParser;
import com.nearby.justnow.data.model.PeriodGroupType;
import com.nearby.justnow.data.model.PeriodNameKey;
import com.nearby.justnow.data.model.ScheduleProfile;
import com.nearby.justnow.data.repository.TimePeriodRepository;
import com.nearby.justnow.ui.engine.TimeRemainingCalculator;
import com.nearby.justnow.util.RegionSettings;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 时间段配置 ViewModel
 */
public class PeriodConfigViewModel extends BaseViewModel {

    private static final String KEY_SCHEDULE_PROFILE = "schedule_profile";

    private final TimePeriodRepository mRepo;
    private final SharedPreferences mPrefs;
    private final boolean mIsMainlandChina;
    private final HolidayCacheManager mHolidayCacheManager;

    public PeriodConfigViewModel(JustNowApplication app) {
        super(app);
        mRepo = app.getTimePeriodRepository();
        mPrefs = mApp.getSharedPreferences(PrefsConfig.PREFS_NAME, Context.MODE_PRIVATE);
        mIsMainlandChina = RegionSettings.isMainlandChina(mApp);
        mHolidayCacheManager = new HolidayCacheManager(mDb.holidayCacheDao());
        applyProfileDefaults(getScheduleProfile());
    }

    public String getPeriodName(String nameKey, Resources res) {
        return PeriodTextResolver.getPeriodName(res, nameKey);
    }

    public String getGroupName(String groupType, Resources res) {
        return PeriodTextResolver.getGroupName(res, groupType);
    }

    public LiveData<List<TimePeriodGroupEntity>> getAllGroups() {
        return mRepo.getAllGroups();
    }

    public LiveData<List<TimePeriodEntity>> getAllPeriods() {
        return mRepo.getAllPeriods();
    }

    public void updatePeriod(TimePeriodEntity period) {
        mRepo.update(period);
    }

    /**
     * 切换时间段组启用状态。
     * 长假组首次启用时自动设置默认日期范围（当天起 3 天），并预填常规时段。
     * 春节组开启时若无未来数据则拒绝。
     */
    public void updateGroupEnabled(TimePeriodGroupEntity group, boolean enabled) {
        if (group == null || PeriodGroupType.isRegular(group.groupType)) return;

        if (enabled && PeriodGroupType.SPRING_FESTIVAL.equals(group.groupType)) {
            // 春节组开启需检查未来数据，走后台线程
            runInBackground(() -> {
                if (!hasSpringFestivalFutureDataSync()) {
                    return; // 无未来数据，静默拒绝
                }
                runOnUiThread(() -> {
                    group.enabled = true;
                    initSpringFestivalPeriodsIfNeeded(group);
                    mRepo.updateGroup(group);
                });
            });
            return;
        }

        group.enabled = enabled;
        if (enabled) {
            if (PeriodGroupType.isVacation(group.groupType)) {
                initVacationDefaultsIfNeeded(group);
            } else if (PeriodGroupType.SPRING_FESTIVAL.equals(group.groupType)) {
                initSpringFestivalPeriodsIfNeeded(group);
            }
        }
        mRepo.updateGroup(group);
    }

    /**
     * 确保组的默认日期和时段已初始化（用于编辑对话框弹出前，需同步返回结果）。
     * @return 模板时段列表（首次打开、无 DB 数据时）；已有时段返回 null
     */
    public List<TimePeriodEntity> ensureGroupDefaults(TimePeriodGroupEntity group) {
        if (group == null) return null;
        if (PeriodGroupType.isVacation(group.groupType)) {
            return fillVacationDefaultsSync(group);
        } else if (PeriodGroupType.SPRING_FESTIVAL.equals(group.groupType)) {
            return fillSpringFestivalDefaultsSync(group);
        }
        return null;
    }

    /**
     * 填充 vacation 组的默认日期，并从 REGULAR 加载时段模板（仅内存，不持久化）。
     * @return 模板时段列表（内存副本，未写入 DB）；若已有时段则返回 null
     */
    private List<TimePeriodEntity> fillVacationDefaultsCore(TimePeriodGroupEntity group) {
        String groupType = group.groupType;
        boolean hasDates = group.startMonthDay != null && !group.startMonthDay.isEmpty()
            && group.endMonthDay != null && !group.endMonthDay.isEmpty();

        mDb.timePeriodDao().deduplicatePeriods();
        List<TimePeriodEntity> existingPeriods = mRepo.getPeriodsByGroupSync(groupType);
        boolean hasPeriods = existingPeriods != null && !existingPeriods.isEmpty();

        Calendar cal = Calendar.getInstance();
        if (!hasDates) {
            group.startMonthDay = String.format("%02d-%02d",
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            cal.add(Calendar.DAY_OF_MONTH, 3);
            group.endMonthDay = String.format("%02d-%02d",
                cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
            group.lastEditedAt = System.currentTimeMillis();
        }
        if (!hasPeriods) {
            // 从 REGULAR 加载时段作为内存模板，不写入 DB
            List<TimePeriodEntity> templates = mRepo.getPeriodsByGroupSync(PeriodGroupType.REGULAR);
            if (templates != null && !templates.isEmpty()) {
                List<TimePeriodEntity> copies = new ArrayList<>();
                for (TimePeriodEntity p : templates) {
                    TimePeriodEntity copy = new TimePeriodEntity();
                    copy.groupType = groupType;
                    copy.nameKey = p.nameKey;
                    copy.sortOrder = p.sortOrder;
                    copy.startMinute = p.startMinute;
                    copy.endMinute = p.endMinute;
                    copy.reverseQuadrant = p.reverseQuadrant;
                    copy.preferChore = p.preferChore;
                    copy.priorityEligible = p.priorityEligible;
                    copies.add(copy);
                }
                return copies;
            }
            return new ArrayList<>();
        }
        return null;
    }

    /**
     * 同步填充 vacation 组的默认日期和时段（在后台线程调用）。
     * @return 模板时段列表，或 null（已有时段时）
     */
    private List<TimePeriodEntity> fillVacationDefaultsSync(TimePeriodGroupEntity group) {
        return fillVacationDefaultsCore(group);
    }

    /**
     * 填充春节组的默认日期，并从 REGULAR 加载时段模板（仅内存，不持久化）。
     * @return 模板时段列表（内存副本，未写入 DB）；若已有时段则返回 null
     */
    private List<TimePeriodEntity> fillSpringFestivalDefaultsCore(TimePeriodGroupEntity group) {
        boolean needDates = group.startMonthDay == null || group.startMonthDay.isEmpty()
            || group.endMonthDay == null || group.endMonthDay.isEmpty();

        mDb.timePeriodDao().deduplicatePeriods();
        List<TimePeriodEntity> existingPeriods = mRepo.getPeriodsByGroupSync(PeriodGroupType.SPRING_FESTIVAL);
        boolean needPeriods = existingPeriods == null || existingPeriods.isEmpty();

        if (needDates) {
            int year = Calendar.getInstance().get(Calendar.YEAR);
            String cachedJson = mHolidayCacheManager.getSync(year);
            if (cachedJson != null) {
                LocalDate[] range = IcsParser.getFestivalRange(cachedJson, "spring_festival");
                if (range != null) {
                    group.startMonthDay = range[0].format(DateTimeFormatter.ofPattern("MM-dd"));
                    group.endMonthDay = range[1].format(DateTimeFormatter.ofPattern("MM-dd"));
                    group.lastEditedAt = System.currentTimeMillis();
                }
            }
        }
        if (needPeriods) {
            // 从 REGULAR 加载时段作为内存模板，不写入 DB
            List<TimePeriodEntity> templates = mRepo.getPeriodsByGroupSync(PeriodGroupType.REGULAR);
            if (templates != null && !templates.isEmpty()) {
                List<TimePeriodEntity> copies = new ArrayList<>();
                for (TimePeriodEntity p : templates) {
                    TimePeriodEntity copy = new TimePeriodEntity();
                    copy.groupType = PeriodGroupType.SPRING_FESTIVAL;
                    copy.nameKey = p.nameKey;
                    copy.sortOrder = p.sortOrder;
                    copy.startMinute = p.startMinute;
                    copy.endMinute = p.endMinute;
                    copy.reverseQuadrant = p.reverseQuadrant;
                    copy.preferChore = p.preferChore;
                    copy.priorityEligible = p.priorityEligible;
                    copies.add(copy);
                }
                return copies;
            }
            return new ArrayList<>();
        }
        return null;
    }

    /**
     * 同步填充春节组的默认日期和时段（在后台线程调用）。
     * @return 模板时段列表，或 null（已有时段时）
     */
    private List<TimePeriodEntity> fillSpringFestivalDefaultsSync(TimePeriodGroupEntity group) {
        return fillSpringFestivalDefaultsCore(group);
    }

    /** 从常规组复制时段到目标组。 */
    private void copyPeriodsFromTemplate(String targetGroupType) {
        List<TimePeriodEntity> templates = mRepo.getPeriodsByGroupSync(PeriodGroupType.REGULAR);
        if (templates != null && !templates.isEmpty()) {
            List<TimePeriodEntity> copies = new ArrayList<>();
            for (TimePeriodEntity p : templates) {
                TimePeriodEntity copy = new TimePeriodEntity();
                copy.groupType = targetGroupType;
                copy.nameKey = p.nameKey;
                copy.sortOrder = p.sortOrder;
                copy.startMinute = p.startMinute;
                copy.endMinute = p.endMinute;
                copy.reverseQuadrant = p.reverseQuadrant;
                copy.preferChore = p.preferChore;
                copy.priorityEligible = p.priorityEligible;
                copies.add(copy);
            }
            mDb.timePeriodDao().insertPeriods(copies);
        }
    }

    /**
     * 保存时间段组的日期范围和各时段的时间修改。
     * 首次保存时（时段 id == 0）走 insert 路径，已有记录走 update 路径。
     */
    public void updateGroupAndPeriods(TimePeriodGroupEntity group,
                                       List<TimePeriodEntity> periods) {
        if (group == null) return;
        runInBackground(() -> {
            mRepo.updateGroup(group);
            if (periods != null) {
                List<TimePeriodEntity> toInsert = new ArrayList<>();
                for (TimePeriodEntity p : periods) {
                    if (p.id == 0) {
                        toInsert.add(p);
                    } else {
                        mRepo.update(p);
                    }
                }
                if (!toInsert.isEmpty()) {
                    mDb.timePeriodDao().insertPeriods(toInsert);
                }
            }
        });
    }

    private void initVacationDefaultsIfNeeded(TimePeriodGroupEntity group) {
        runInBackground(() -> {
            // fillVacationDefaultsCore 返回非 null 表示无已有时段，需首次复制模板
            List<TimePeriodEntity> templates = fillVacationDefaultsCore(group);
            if (templates != null) {
                copyPeriodsFromTemplate(group.groupType);
            }
            mRepo.updateGroup(group);
        });
    }

    /**
     * 春节组首次启用时，从缓存读取日期，从常规组复制时段到 DB。
     */
    private void initSpringFestivalPeriodsIfNeeded(TimePeriodGroupEntity group) {
        runInBackground(() -> {
            // fillSpringFestivalDefaultsCore 返回非 null 表示无已有时段，需首次复制模板
            List<TimePeriodEntity> templates = fillSpringFestivalDefaultsCore(group);
            if (templates != null) {
                copyPeriodsFromTemplate(PeriodGroupType.SPRING_FESTIVAL);
            }
            mRepo.updateGroup(group);
        });
    }

    public String getScheduleProfile() {
        String profile = mPrefs.getString(KEY_SCHEDULE_PROFILE, ScheduleProfile.GENERAL);
        String normalizedProfile = ScheduleProfile.normalizeProfile(profile, mIsMainlandChina);
        if (!normalizedProfile.equals(profile)) {
            mPrefs.edit().putString(KEY_SCHEDULE_PROFILE, normalizedProfile).apply();
        }
        return normalizedProfile;
    }

    public void setScheduleProfile(String profile) {
        String normalizedProfile = ScheduleProfile.normalizeProfile(profile, mIsMainlandChina);
        mPrefs.edit().putString(KEY_SCHEDULE_PROFILE, normalizedProfile).apply();
        applyProfileDefaults(normalizedProfile);
    }

    public List<String> getAvailableScheduleProfiles() {
        return ScheduleProfile.getOrderedProfiles(mIsMainlandChina);
    }

    public String getScheduleProfileName(Resources res) {
        return PeriodTextResolver.getProfileName(res, getScheduleProfile());
    }

    public String getScheduleProfileDescription(Resources res) {
        return PeriodTextResolver.getProfileDescription(res, getScheduleProfile());
    }

    public boolean isGroupVisibleForCurrentProfile(String groupType) {
        return PeriodGroupType.isRegular(groupType)
            || ScheduleProfile.getVisiblePeriodGroupTypes(getScheduleProfile(), mIsMainlandChina).contains(groupType);
    }

    /** 地区有数据源且缓存中有当年数据 */
    public boolean hasHolidayData() {
        if (!RegionSettings.isHolidayDataAvailable(mApp)) return false;
        return mHolidayCacheManager.getSync(Calendar.getInstance().get(Calendar.YEAR)) != null;
    }

    public void resolveActiveGroupTypeAsync(List<TimePeriodGroupEntity> groups,
                                            java.util.function.Consumer<String> callback) {
        mApp.getPeriodGroupRuleResolver().resolveActiveGroupTypeAsync(groups, Calendar.getInstance(), callback);
    }

    public List<PeriodGroupItem> buildGroupItems(List<TimePeriodGroupEntity> groups,
                                                  List<TimePeriodEntity> periods,
                                                  Resources res,
                                                  String activeGroupType,
                                                  boolean springFestivalHasFutureData) {
        List<PeriodGroupItem> items = new ArrayList<>();
        if (groups == null) return items;
        Map<String, List<TimePeriodEntity>> periodMap = groupPeriods(periods);
        for (TimePeriodGroupEntity group : groups) {
            List<TimePeriodEntity> groupPeriods = periodMap.get(group.groupType);
            boolean isRegular = PeriodGroupType.isRegular(group.groupType);
            boolean springFestivalBlocked = PeriodGroupType.SPRING_FESTIVAL.equals(group.groupType)
                && !springFestivalHasFutureData;
            int noticeResId = springFestivalBlocked
                ? R.string.s_spring_festival_no_upcoming_data
                : getNoticeMessageResId(group);
            items.add(new PeriodGroupItem(
                group,
                isRegular
                    ? formatNamedPeriodSummary(groupPeriods, res)
                    : formatPeriodRangeSummary(groupPeriods),
                formatPeriodLabels(groupPeriods, res),
                formatPeriodRanges(groupPeriods),
                activeGroupType != null && activeGroupType.equals(group.groupType),
                noticeResId,
                springFestivalBlocked
            ));
        }
        return items;
    }

    /**
     * 切换作息类型：只关闭不可见的组，不自动开启任何组。
     * 用户自行决定启用哪些，切出时被隐藏的组强制关闭。
     * 无节假日数据的地区：工作日组不可用，寒暑假+长假直接暴露。
     */
    public void applyProfileDefaults(String profile) {
        runInBackground(() -> {
            List<TimePeriodGroupEntity> groups = new ArrayList<>();
            for (com.nearby.justnow.data.model.PeriodGroupWithPeriods detail
                : mRepo.getAllGroupDetailsSync()) {
                groups.add(detail.group);
            }
            // 检查缓存中是否已有节假日数据（而不仅是数据源是否存在）
            int year = Calendar.getInstance().get(Calendar.YEAR);
            boolean holidayCached = mHolidayCacheManager.getSync(year) != null;

            Set<String> visibleTypes = new HashSet<>(
                ScheduleProfile.getVisiblePeriodGroupTypes(profile, mIsMainlandChina));
            for (TimePeriodGroupEntity group : groups) {
                if (PeriodGroupType.isRegular(group.groupType)) continue;
                if (!visibleTypes.contains(group.groupType)) {
                    group.enabled = false;
                    mRepo.updateGroup(group);
                    continue;
                }
                // 缓存中无节假日数据时工作日组不可用
                if (!holidayCached && PeriodGroupType.WORKDAY.equals(group.groupType)) {
                    group.enabled = false;
                    mRepo.updateGroup(group);
                }
                // 可见的组保持原状，不自动开启
            }
            // 缓存有关数据时用 LEGAL_HOLIDAY，否则回退 STANDARD_WEEK
            if (holidayCached
                && (ScheduleProfile.SECURITIES.equals(profile)
                    || ScheduleProfile.GENERAL.equals(profile)
                    || ScheduleProfile.SCHOOL.equals(profile))) {
                mApp.getPeriodGroupRuleResolver().setWorkdayPolicy(PeriodGroupRuleResolver.WorkdayPolicy.LEGAL_HOLIDAY);
            } else {
                mApp.getPeriodGroupRuleResolver().setWorkdayPolicy(PeriodGroupRuleResolver.WorkdayPolicy.STANDARD_WEEK);
            }
        });
    }

    private static Map<String, List<TimePeriodEntity>> groupPeriods(List<TimePeriodEntity> periods) {
        Map<String, List<TimePeriodEntity>> map = new HashMap<>();
        if (periods == null) return map;
        for (TimePeriodEntity period : TimeRemainingCalculator.sortPeriods(periods)) {
            List<TimePeriodEntity> groupPeriods = map.get(period.groupType);
            if (groupPeriods == null) {
                groupPeriods = new ArrayList<>();
                map.put(period.groupType, groupPeriods);
            }
            groupPeriods.add(period);
        }
        return map;
    }

    private String formatNamedPeriodSummary(List<TimePeriodEntity> periods, Resources res) {
        if (periods == null || periods.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        for (TimePeriodEntity period : periods) {
            parts.add(getPeriodName(period.nameKey, res) + " "
                + minuteToTime(period.startMinute) + "-" + minuteToTime(period.endMinute));
        }
        return android.text.TextUtils.join("\n", parts);
    }

    private static String formatPeriodRangeSummary(List<TimePeriodEntity> periods) {
        if (periods == null || periods.isEmpty()) return "";
        return android.text.TextUtils.join(" / ", formatPeriodRanges(periods));
    }

    private List<String> formatPeriodLabels(List<TimePeriodEntity> periods, Resources res) {
        List<String> labels = new ArrayList<>();
        if (periods == null) return labels;
        for (TimePeriodEntity period : periods) {
            labels.add(getPeriodName(period.nameKey, res));
        }
        return labels;
    }

    private static List<String> formatPeriodRanges(List<TimePeriodEntity> periods) {
        List<String> parts = new ArrayList<>();
        if (periods == null) return parts;
        for (TimePeriodEntity period : periods) {
            parts.add(minuteToTime(period.startMinute) + "-" + minuteToTime(period.endMinute));
        }
        return parts;
    }

    private static int getNoticeMessageResId(TimePeriodGroupEntity group) {
        if (group == null || !group.enabled || !PeriodGroupType.isVacation(group.groupType)) {
            return 0;
        }
        if (group.lastEditedAt <= 0) return 0;
        String reviewKey = buildVacationReviewKey(group.groupType, Calendar.getInstance());
        if (reviewKey.equals(group.lastReviewedKey)) return 0;
        return R.string.s_period_group_vacation_review_notice;
    }

    private static String buildVacationReviewKey(String groupType, Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        if (PeriodGroupType.WINTER_VACATION.equals(groupType)) {
            return "winter-" + year + "-" + (year + 1);
        }
        return "summer-" + year;
    }

    /** 分钟数转 HH:mm 字符串 */
    public static String minuteToTime(int minute) {
        return String.format("%02d:%02d", minute / 60, minute % 60);
    }

    /**
     * 同步检查春节当年/来年缓存中是否有 start >= 今天的记录。
     * 仅限后台线程调用（涉及 Room 同步查询）。
     */
    public boolean hasSpringFestivalFutureDataSync() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int thisYear = today.getYear();

        // 检查当年
        String cachedJson = mHolidayCacheManager.getSync(thisYear);
        if (cachedJson != null) {
            java.time.LocalDate[] range = IcsParser.getFestivalRange(cachedJson, "spring_festival");
            if (range != null && !range[0].isBefore(today)) {
                return true;
            }
        }

        // 检查来年
        int nextYear = thisYear + 1;
        cachedJson = mHolidayCacheManager.getSync(nextYear);
        if (cachedJson != null) {
            java.time.LocalDate[] range = IcsParser.getFestivalRange(cachedJson, "spring_festival");
            if (range != null && !range[0].isBefore(today)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 后台检查春节是否有未来数据并回调到主线程。
     */
    public void checkSpringFestivalDataAsync(java.util.function.Consumer<Boolean> callback) {
        runInBackground(() -> {
            boolean hasFuture = hasSpringFestivalFutureDataSync();
            runOnUiThread(() -> callback.accept(hasFuture));
        });
    }

    /**
     * 后台检查节假日数据并回调到主线程（供 Fragment 使用）。
     */
    public void checkHolidayDataAsync(java.util.function.Consumer<Boolean> callback) {
        runInBackground(() -> {
            boolean hasData = hasHolidayData();
            runOnUiThread(() -> callback.accept(hasData));
        });
    }

    /**
     * 后台确保组默认值并加载时段，排序后回调到主线程（供 Fragment 展示编辑对话框使用）。
     * vacation 组首次打开时，时段来自 REGULAR 内存模板（不写入 DB），取消后不留痕迹。
     */
    public void ensureDefaultsAndLoadPeriods(TimePeriodGroupEntity group,
                                              java.util.function.Consumer<List<TimePeriodEntity>> callback) {
        runInBackground(() -> {
            // 清理历史遗留的重复时段（同 group_type + name_key 保留一条）
            mDb.timePeriodDao().deduplicatePeriods();

            List<TimePeriodEntity> templatePeriods = ensureGroupDefaults(group);
            List<TimePeriodEntity> result;
            if (templatePeriods != null) {
                // vacation 组首次打开：使用内存模板（未写入 DB）
                result = templatePeriods;
            } else {
                // 已有时段或非 vacation 组：从 DB 加载
                List<TimePeriodEntity> periods = mRepo.getPeriodsByGroupSync(group.groupType);
                result = periods != null ? new ArrayList<>(periods) : new ArrayList<>();
            }
            java.util.Collections.sort(result, (a, b) -> Integer.compare(a.startMinute, b.startMinute));
            runOnUiThread(() -> callback.accept(result));
        });
    }

    public static class PeriodGroupItem {
        public final TimePeriodGroupEntity group;
        public final String periodSummary;
        public final List<String> periodLabels;
        public final List<String> periodRanges;
        public final boolean active;
        public final int noticeMessageResId;
        public final boolean springFestivalBlocked;

        PeriodGroupItem(TimePeriodGroupEntity group, String periodSummary,
                         List<String> periodLabels, List<String> periodRanges,
                         boolean active, int noticeMessageResId,
                         boolean springFestivalBlocked) {
            this.group = group;
            this.periodSummary = periodSummary;
            this.periodLabels = periodLabels;
            this.periodRanges = periodRanges;
            this.active = active;
            this.noticeMessageResId = noticeMessageResId;
            this.springFestivalBlocked = springFestivalBlocked;
        }
    }
}
