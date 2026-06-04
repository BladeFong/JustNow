# 检索引擎模块

# 阶段规划、决策记录

## 定位和功能描述

任务和标签的检索统一使用同一接口，内部采用不同匹配策略。对应 task_plan.md M1（任务录入）中的检索部分。

## 整体规划和决策

### 接口设计

```java
public interface SearchStrategy {
    List<SearchResult> search(String keyword);
}
```

### 两种策略

**任务内容检索（部分匹配）**
- 匹配方式：SQL `LIKE '%keyword%'`
- 搜索范围：`tasks.content`，仅搜索未归档任务
- 排序：按创建时间倒序
- 用途：任务录入时查重、避免重复创建

**标签检索（全匹配）**
- 匹配方式：SQL `LIKE '%keyword%'`（标签名称前缀或包含匹配）
- 搜索范围：`tags.name`
- 排序：按名称字母排序
- 用途：标签填写时推荐已有标签

### 统一入口

```java
public class SearchEngine {
    private final TaskDao taskDao;
    private final TagDao tagDao;

    public LiveData<List<TaskEntity>> searchTasks(String keyword);
    public LiveData<List<TagEntity>> searchTags(String keyword);
}
```

### 文件结构

```
data/search/
├── SearchStrategy.java      # 检索策略接口
├── TaskSearchStrategy.java  # 任务部分匹配策略
├── TagSearchStrategy.java   # 标签全匹配策略
└── SearchEngine.java        # 统一入口，组合策略
```

### 已有类文件
```
data/dao/
├── TaskDao.java    # searchByContent(keyword) — 已实现
└── TagDao.java     # searchByName(keyword) — 已实现
```

### 依赖
- `TaskDao.searchByContent(keyword)`
- `TagDao.searchByName(keyword)`

### 变更影响
修改策略只影响本模块，不影响上层 UI 逻辑。

# 研究发现、技术决策

- 任务 DAO 模糊搜索（LIKE %keyword%）——基础实现
- 多 token 全文检索（content + detail 双列，按比例命中，子查询 + CASE WHEN 计分）
- TextTokenizer 通用多语言分词（CJK→jieba，拉丁→空格分词）+ 停用词过滤
- 标签 DAO 按名称搜索（LIKE）——基础实现
- jieba 词典 Application.onCreate 预热（避免首次输入延迟）
- 标签检索分离（任务内容匹配 vs 标签名匹配）——已实现，任务/标签分开输入和检索
