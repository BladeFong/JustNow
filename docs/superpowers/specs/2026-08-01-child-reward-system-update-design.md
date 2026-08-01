# 儿童奖励系统升级设计文档

## 1. 概述与目标

针对现有儿童奖励系统（时光胶囊与七朵花收集栏），进行如下功能增强与入口改造：
1. **每周通关目标天数自定义**：允许设置每周达成几天算通关（可选范围 2~5 天，默认 3 天）。
2. **菜单入口改造**：将主界面右上角原“查看内置图标”菜单入口升级为“儿童奖励设置”，弹窗提供目标天数选择与内置图标预览能力。
3. **非寒暑假工作日花芯默认填充**：在非寒暑假期间的工作日（包含法定节假日调休补班，且不受工作日时间段组是否开启的影响），花芯默认自动填充，完成紧急重要任务可直接获得花瓣。

---

## 2. 界面与交互设计

### 2.1 菜单入口改造
- **菜单位置**：`app/src/main/res/menu/menu_main.xml`
- **修改项**：将 `action_view_child_icons` 菜单项标题由 `android:title="查看内置图标"` 改为 `android:title="儿童奖励设置"`。

### 2.2 “儿童奖励设置”对话框
- **触发逻辑**：在 `MainFragment.java` 点击该菜单项时，弹出 `MaterialAlertDialog`。
- **对话框布局结构**：
  1. **标题**：儿童奖励设置
  2. **目标天数设置区域**：
     - 说明文案：“每周通关目标天数”
     - 选择器：横向单选控件（如 RadioGroup 或 Segment 按钮组），包含 `2天`、`3天`、`4天`、`5天`，默认选中 `3天`。
     - 切换选中时，实时将目标天数保存至 `SharedPreferences` (`weekly_reward_target_days`)，并通知 `RewardBarFragment` 刷新。
  3. **内置图标预览区域**：
     - 说明文案：“内置儿童标签图标参考”
     - 保持现有 5 列 `GridLayoutManager` 网格展示 10 个儿童矢量图标及对应中文标签。
  4. **底部按钮**：“确定”按钮，点击关闭弹窗。

---

## 3. 核心业务逻辑设计

### 3.1 每周通关天数配置与判定
- **存储机制**：
  - 在 `SharedPreferences` 保存 `KEY_WEEKLY_TARGET_DAYS = "weekly_reward_target_days"`，默认值为 `3`。
- **通关校验与 UI 提示**：
  - `RewardBarFragment.java` 中获取 `targetDays`（缺省为 3）。
  - 通关判定条件由原固定的 `activeCount >= 5` 修改为 `activeCount >= targetDays`。
  - 点击收集栏弹出的 Toast 提示及全满祝贺对话框，均基于该 `targetDays` 计算。

### 3.2 非寒暑假工作日花芯默认填充算法
- **触发位置**：`RewardBarFragment.refreshWeeklyFlowers()` 异步数据计算逻辑中。
- **判定条件**：
  - 对当周 7 天（周一至周日）每一天进行校验：
    1. **寒暑假校验**：使用 `PeriodGroupRuleResolver` 检查当天是否命中 `PeriodGroupType.SUMMER_VACATION`（暑假 07-01 ~ 08-31）或 `PeriodGroupType.WINTER_VACATION`（寒假 01-15 ~ 02-20）。
    2. **工作日校验**：调用 `PeriodGroupRuleResolver.isWorkdaySync(Calendar cal)`。
       - 该方法已内置中国法定节假日与调休补班数据。
       - **关键保证**：该判定直接基于节假日数据及 Calendar 计算，即使用户未开启“工作日”时间段组，依然能精准识别法定工作日与补班日。
    3. **花芯初始状态设置**：
       - 若 `!isVacation && isWorkday`（非寒暑假且为工作日）：
         初始化 `centerFilled[i] = true`。
         当该天完成紧急重要任务（Q0象限）时，直接累加 3 个花瓣，无需再经历“首次仅填花芯”阶段。
       - 若为寒暑假，或属于非工作日（周末/法定节假日放假）：
         初始化 `centerFilled[i] = false`。
         保持原有规则，需通过完成当天首个紧急重要任务来填亮花芯。

---

## 4. 数据与依赖变更

1. **SharedPreferences**：
   - 包含新增键 `weekly_reward_target_days` (Int, 2~5, default: 3)。
2. **多语言与字符串 (res/values/strings.xml)**：
   - `s_child_reward_setting`: "儿童奖励设置"
   - `s_weekly_target_days_title`: "每周通关目标"
   - `s_flower_progress`: "已连续达成 %1$d/%2$d 天，继续加油！" (动态目标天数)
3. **组件与模块**：
   - `MainFragment.java`
   - `RewardBarFragment.java`
   - `menu_main.xml`
   - `modules/tablet-flower-rewards.md` / `progress.md`

---

## 5. 验证与测试用例

1. **菜单与设置交互测试**：
   - 确认右上角菜单项已更名为“儿童奖励设置”。
   - 点击打开对话框，选择目标天数（如选择 4 天），关闭后重新打开确认选中状态保持为 4 天。
2. **通关判定测试**：
   - 设置目标天数为 3 天，点亮 3 朵花时触发通关高亮与祝贺弹窗。
   - 修改目标天数为 5 天，3 朵花时显示常规进度 Toast，达到 5 朵花时才触发通关。
3. **工作日花芯默认填充测试**：
   - **非寒暑假工作日**：检查周一至周五的花芯默认是否为已填充状态；完成 Q0 任务后花瓣数直接 +3。
   - **非寒暑假周末/节假日**：检查周六、周日的花芯默认是否为未填充状态；完成 Q0 任务后首个任务填花芯。
   - **寒暑假期间**：在暑假（如7月15日）工作日，检查花芯是否默认未填充，保持原有填花芯逻辑。
