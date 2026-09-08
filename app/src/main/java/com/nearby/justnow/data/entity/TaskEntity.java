package com.nearby.justnow.data.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 任务主表
 */
@Entity(
    tableName = "tasks",
    foreignKeys = @ForeignKey(
        entity = TagEntity.class,
        parentColumns = "id",
        childColumns = "tag_id",
        onDelete = ForeignKey.SET_NULL
    ),
    indices = @Index("tag_id")
)
public class TaskEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** 任务标题（首行） */
    public String content;

    /** 任务正文（首行之后的内容，可为空） */
    @ColumnInfo(name = "detail")
    public String detail;

    /** 标签ID，可为空 */
    @ColumnInfo(name = "tag_id")
    public Long tagId;

    /**
     * 四象限分类：
     * 0 = 紧急重要
     * 1 = 紧急不重要
     * 2 = 不紧急重要
     * 3 = 不紧急不重要
     */
    public int quadrant;

    /**
     * 专注时长（分钟）：
     * 0 = 琐碎；其他档位由展示策略按固定间隔生成。
     */
    @ColumnInfo(name = "focus_minutes")
    public int focusMinutes;

    /** 是否已归档（"不再需要"） */
    @ColumnInfo(name = "is_archived", defaultValue = "0")
    public boolean isArchived;

    /** 创建时间戳（毫秒） */
    @ColumnInfo(name = "created_at")
    public long createdAt;

    /** 执行开始时间戳（毫秒），0 = 未在执行中 */
    @ColumnInfo(name = "executing_start_ms", defaultValue = "0")
    public long executingStartMs;

    /** 执行结束时间戳（毫秒），0 = 未完成 */
    @ColumnInfo(name = "executing_end_ms", defaultValue = "0")
    public long executingEndMs;

    /** Markdown 详情内容，可为空 */
    @ColumnInfo(name = "detail_markdown", defaultValue = "")
    public String detailMarkdown;

    /** 附加模块类型：null / 'checklist' / 'app_actions' */
    @ColumnInfo(name = "detail_module_type")
    public String detailModuleType;

    /** 完成模式：0=每天 1=每周 2=每月 3=每年 */
    @ColumnInfo(name = "completion_mode", defaultValue = "0")
    public int completionMode;

    /** 配额：日模式固定1，周/月/年由用户设置（≤周期天数-1） */
    @ColumnInfo(name = "quota", defaultValue = "1")
    public int quota;

    /** 
     * 内置儿童兴趣活动图标标识，为 null 时不展示图标。
     * 值为: 'blocks', 'book', 'palette', 'music', 'ball', 'game_puzzle', 'craft', 'animation', 'study', 'chores' 
     */
    @ColumnInfo(name = "icon_name", defaultValue = "NULL")
    public String iconName;

    /** 任务是否当前正在执行中 */
    public boolean isExecuting() {
        return executingStartMs > 0 && executingEndMs == 0;
    }
}
