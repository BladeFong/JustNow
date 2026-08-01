# 儿童奖励系统升级实施计划 (通关天数自定义与工作日花芯默认填充)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 改造主界面右上角“查看内置图标”为“儿童奖励设置”菜单，允许用户设置每周通关目标天数（2~5天，默认3天）；同时实现非寒暑假期间工作日花芯默认自动填充的逻辑。

**Architecture:** 
1. `SharedPreferences` 存储 `weekly_reward_target_days`；
2. `MainFragment` 点击菜单弹出“儿童奖励设置”对话框，上半部分为目标天数单选器，下半部分为内置图标网格参考；
3. `RewardBarFragment` 读取配置的目标天数校验 `activeCount >= targetDays` 触发通关；
4. `RewardBarFragment.refreshWeeklyFlowers()` 利用 `PeriodGroupRuleResolver.isWorkdaySync()` 和 Vacation 检查，在非寒暑假工作日默认设置 `centerFilled[i] = true`。

**Tech Stack:** Java, Android Framework, MaterialAlertDialog, SharedPreferences, PeriodGroupRuleResolver, JUnit4.

## Global Constraints

- 遵照修改前方案确认原则，保持现有点亮与拍照逻辑兼容
- 仅在非寒暑假的工作日默认填充花芯，工作日判定需结合法定节假日/补班数据 (`isWorkdaySync`)
- 严格遵循多语言（zh-rCN, zh-rTW, zh-rHK, en 等）格式要求
- 提交前测试通过，更新模块文档 `modules/tablet-flower-rewards.md`

---

### Task 1: 字符串资源扩展与多语言映射

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh-rCN/strings.xml`
- Modify: `app/src/main/res/values-zh-rHK/strings.xml`
- Modify: `app/src/main/res/values-zh-rTW/strings.xml`

**Interfaces:**
- Consumes: 既有 `s_flower_progress` 等字符串
- Produces: 新增 `s_child_reward_setting`, `s_weekly_target_days_title`, `s_target_days_option` 等资源，更新 `s_flower_progress` 支持目标天数占位

- [ ] **Step 1: 在 strings.xml 中添加儿童奖励设置相关资源**

增加 `s_child_reward_setting`、`s_weekly_target_days_title` 以及格式化字符串。

---

### Task 2: 改造右上角菜单入口与“儿童奖励设置”对话框

**Files:**
- Modify: `app/src/main/res/menu/menu_main.xml`
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/MainFragment.java`

**Interfaces:**
- Consumes: `SharedPreferences`, `R.menu.menu_main`
- Produces: 升级后的右上角“儿童奖励设置”对话框（含天数选择与内置图标预览）

- [ ] **Step 1: 修改 menu_main.xml 菜单项标题**
将 `action_view_child_icons` 的 title 改为 `@string/s_child_reward_setting`。

- [ ] **Step 2: 在 MainFragment 中重构菜单点击处理方法**
构建包含“每周通关目标天数”选择（RadioGroup / Segment 2~5天，默认 3 天）与“内置图标网格”的对话框，修改选择后写入 SP `weekly_reward_target_days` 并通知 `RewardBarFragment.refreshWeeklyFlowers()`。

---

### Task 3: RewardBarFragment 通关判定与工作日花芯默认填充

**Files:**
- Modify: `app/src/main/java/com/nearby/justnow/ui/main/RewardBarFragment.java`

**Interfaces:**
- Consumes: `PeriodGroupRuleResolver`, `SharedPreferences`
- Produces: 支持动态通关目标天数与工作日花芯自动点亮

- [ ] **Step 1: 读取配置的目标天数**
在 `RewardBarFragment` 中读取 `sp.getInt("weekly_reward_target_days", 3)`，支持动态改变通关校验 `activeCount >= targetDays`。

- [ ] **Step 2: 计算非寒暑假工作日花芯填充状态**
在 `refreshWeeklyFlowers()` 中遍历当周 7 天：
```java
Calendar cal = Calendar.getInstance();
cal.setTimeInMillis(monday + i * 86400000L);
boolean isVacation = ruleResolver.isVacation(cal);
boolean isWorkday = ruleResolver.isWorkdaySync(cal);
if (!isVacation && isWorkday) {
    centerFilled[i] = true;
}
```

---

### Task 4: 编写单元测试与更新模块文档

**Files:**
- Create: `app/src/test/java/com/nearby/justnow/ui/main/ChildRewardSystemTest.java`
- Modify: `modules/tablet-flower-rewards.md`
- Modify: `modules/tablet-flower-rewards_progress.md`
- Modify: `progress.md`

- [ ] **Step 1: 编写 ChildRewardSystemTest 验证通关与花芯逻辑**
- [ ] **Step 2: 运行测试与构建确认**
- [ ] **Step 3: 更新模块进度与技术决策文档**
