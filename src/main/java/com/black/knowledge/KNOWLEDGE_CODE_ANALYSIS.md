## 1. 模块定位

`knowledge` 包是“视频学习内容结构化引擎”：

- 输入：视频转写文本（来自 `asr` 模块）
- 处理：AI 抽取知识点 + 向量召回对齐 + 关系构建 + 评分
- 输出：知识框架、知识点图谱、视频片段索引、视频质量评分

---

## 2. 目录速览

- `controller/`：对外 API 入口（先看）
- `service/`：核心业务逻辑（重点看）
- `po/`：数据库实体
- `repository/`：JPA 查询接口
- `dto/`：请求参数与 AI 中间结构
- `vo/`：接口返回结构
- `aspect/` + `annotation/`：所有权校验（权限关键点）
- `config/`：AI 客户端与向量库配置

推荐阅读顺序：

1. `KnowledgeController`
2. `KnowledgePointService`
3. `KnowledgePipelineService`
4. `po + repository`
5. `OwnerAspect`

---

## 3. 数据库架构

> 当前配置：`application.yml` 中 `spring.jpa.hibernate.ddl-auto=update`。实体未显式声明 `@Table`，表名按默认规则推导（驼峰转下划线）。

### 3.1 核心表

### 3.1.1 `knowledge_framework`（知识框架）

来源：`KnowledgeFramework`

- `id` 主键
- `user_id` 所属用户
- `name` 框架名
- `subject` 学科/主题
- `description` 描述
- `create_time` / `update_time`

作用：一个用户可以有多个框架，是知识资产的顶层容器。

### 3.1.2 `knowledge_point`（知识点）

来源：`KnowledgePoint`

- `id`
- `framework_id` 所属框架
- `title`
- `content`（`LONGTEXT`，存详细笔记）
- `create_time` / `update_time`

作用：知识图谱中的节点，也是向量检索的核心对象。

### 3.1.3 `knowledge_relation`（知识点关系）

来源：`KnowledgeRelation`

- `id`
- `source_point_id`
- `target_point_id`
- `relation_type`（`PREREQUISITE` / `RELATED` / `CONTAINS`）

作用：知识点之间的边，支持前端构图、构树。

### 3.1.4 `knowledge_point_video_ref`（知识点-视频片段映射）

来源：`KnowledgePointVideoRef`

- `id`
- `knowledge_point_id`
- `video_id`
- `timestamps`（JSON 文本，例：`[{"start":10,"end":30}]`）

作用：支持“点击知识点跳视频时间段”和“从视频反查知识点”。

### 3.1.5 `video_score`（视频评分）

来源：`VideoScore`

- `id`
- `video_id`
- `density_score`
- `effectiveness_score`
- `total_score`
- `knowledge_count`
- `explanation`
- `create_time`

作用：保存 AI 评分结果。

### 3.2 跨模块依赖表

- `video`（`asr.po.Video`）：视频元数据
- `video_segment`（`asr.po.VideoSegment`）：转写分段文本
- `video_task`（`task.po.VideoTask`）：异步任务状态与结果

### 3.3 关系图

- User 1-N KnowledgeFramework
- KnowledgeFramework 1-N KnowledgePoint
- KnowledgePoint N-N KnowledgePoint（通过 `knowledge_relation`）
- KnowledgePoint N-N Video（通过 `knowledge_point_video_ref`）
- Video 1-N VideoSegment


## 4. DTO / PO / VO 快速对照

## 4.1 DTO（

### 4.1.1 框架相关

- `CreateFrameworkRequest`
  - `name` 必填
  - `subject`、`description` 可选
- `UpdateFrameworkRequest`
  - 全部可选，服务层做部分更新

### 4.1.2 知识点相关

- `AddKnowledgePointRequest`
  - `frameworkId` 必填
  - `title` 必填
  - `content` 可选
  - `videoRefs` 可选（每个视频含 `segments`）
- `UpdateKnowledgePointRequest`
  - 支持更新标题/内容
  - 支持关系增量更新（`ADD` / `DELETE`）
  - 支持视频索引增量更新（`SET` / `DELETE`）

### 4.1.3 关系相关

- `AddRelationRequest`
  - `sourcePointId`、`targetPointId`、`relationType` 必填

### 4.1.4 AI 中间 DTO

位于 `dto/ai/`：

- `IndependentKnowledgePoint`：第一阶段独立提取
- `ExtractedKnowledgePoint`：对齐后结果（含 `Action`：`NEW` / `UPDATE` / `REDUNDANT`）
- `ExtractedRelation`：AI 提取关系
- `VideoScoreResult`：AI 评分返回

## 4.2 PO

- `KnowledgeFramework`
- `KnowledgePoint`
- `KnowledgeRelation`
- `KnowledgePointVideoRef`
- `VideoScore`

特点：

- 每个 PO 基本都有 `toVo()`（除引用表）
- 关系字段多用 `Long` ID，不是 JPA 关联对象

## 4.3 VO

主要 VO：

- `KnowledgeFrameworkVo`：框架详情/列表
- `KnowledgePointVo`：知识点详情（含 `videoIndexings`）
- `KnowledgeRelationVo`：关系展示（含 source/target title）
- `PointVideoIndexingVo`：知识点关联视频片段
- `VideoIndexVo`：关键词检索返回的视频命中
- `VideoScoreVo`：评分结果
- `VideoProcessResultVo`：任务完整结果（视频+知识点+评分）

一句话理解：

- DTO 是“你传进来什么”
- PO 是“数据库怎么存”
- VO 是“接口返回给前端什么”

---

## 5. 已完成服务能力（现状）

## 5.1 `KnowledgeFrameworkService`

已完成：

- 框架创建、分页查询、详情、更新、删除
- 删除时触发框架内知识点级联清理
- 提供 AOP 所有权校验支持方法

## 5.2 `KnowledgePointService`

已完成：

- 知识点手动 CRUD
- 关系/视频引用的增量更新
- 按框架查询点、按视频查询点
- 点删除时清理关系、引用、向量索引
- 自动提取链路核心逻辑（见 6.2）

## 5.3 `KnowledgeRelationService`

已完成：

- 手动新增/删除关系
- 查询点关系、框架关系
- 将 AI 关系结果落库

## 5.4 `VideoIndexService`

已完成：

- 按关键词检索知识点
- 返回对应视频时间段
- 回填该时间段覆盖的转写文本（便于前端预览）

## 5.5 `VideoScoreService`

已完成：

- 对提取结果统计（NEW/UPDATE/REDUNDANT）
- 调用 AI 计算评分
- 保存并查询视频评分

## 5.6 `KnowledgePipelineService`（异步主流程）

已完成：

- 从 `taskId` 触发全链路处理
- 状态机推进：转写 -> 提取 -> 评分 -> 完成
- 聚合结果写入 `video_task.result_json`
- 异常时标记失败

## 5.7 `AiService`

已完成：

- 独立提取（prompt1）
- 知识对齐（prompt2）
- 视频评分（prompt3）

## 5.8 `OwnerAspect` + 注解

已完成：

- `@CheckFrameworkOwner`
- `@CheckKnowledgePointOwner`
- 基于 SpEL 从参数取 ID，统一做 owner 校验

---

## 6. 两条核心业务流

## 6.1 手动维护流

1. 创建框架
2. 新增知识点（可挂视频片段）
3. 新增关系
4. 查询点和关系，前端构建图谱

## 6.2 自动处理流

1. MQ 消费 `taskId`
2. 转写视频得到文本片段
3. `KnowledgePointService.extractFromVideo()` 三阶段处理：
   - 阶段 1：独立提取候选知识点
   - 阶段 2：向量检索召回相似旧知识
   - 阶段 3：AI 对齐并决策 `NEW/UPDATE/REDUNDANT`
4. 落库知识点、关系、时间片段映射
5. 评分并保存
6. 写任务结果 JSON

---

## 7. Repository 能力

- `KnowledgeFrameworkRepository`：按用户分页/列表/计数
- `KnowledgePointRepository`：按框架查点、按标题关键词检索
- `KnowledgeRelationRepository`：按 source/target 查边，按点删边
- `KnowledgePointVideoRefRepository`：按点/视频查引用，按点视频组合查单条
- `VideoScoreRepository`：按视频查评分

---






