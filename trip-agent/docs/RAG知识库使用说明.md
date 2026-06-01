# RAG 知识库使用说明

## 概述

RAG（Retrieval-Augmented Generation）知识库功能为 Trip Agent 提供了基于知识的增强生成能力。通过向量化检索，Agent 可以访问旅行相关知识，提供更专业、更准确的旅行规划建议。

## 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Trip Agent                              │
├─────────────────────────────────────────────────────────────┤
│  PlanningAgent ──► RagService ──► KnowledgeService           │
│       │                │                │                     │
│       ▼                ▼                ▼                     │
│  Enhanced Prompt   Vector Search   Embedding Service         │
│       │                │                │                     │
│       ▼                ▼                ▼                     │
│  ReAct Loop        PgVector       Qwen Embedding API         │
└─────────────────────────────────────────────────────────────┘
```

## 配置说明

### 1. 环境变量

在 `application.yml` 中配置千问 Embedding API：

```yaml
spring:
  ai:
    openai:
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${QWEN_API_KEY:}
      embedding:
        options:
          model: text-embedding-v4
```

### 2. 数据库配置

确保 PostgreSQL 已启用 pgvector 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

Spring AI 会自动创建 `trip_knowledge` 表。

## API 接口

### 1. 上传知识文档

```bash
curl -X POST http://localhost:8080/api/knowledge/upload \
  -F "file=@beijing-attractions.txt" \
  -F "category=景点" \
  -F "title=北京景点介绍"
```

**参数说明**：
- `file`: 文档文件（支持 txt、pdf、docx）
- `category`: 文档类别（景点、酒店、餐厅、交通、签证）
- `title`: 文档标题（可选）

### 2. 搜索相关知识

```bash
curl "http://localhost:8080/api/knowledge/search?query=北京故宫&topK=5"
```

**参数说明**：
- `query`: 查询文本
- `topK`: 返回结果数量（默认 5）

### 3. 按类别搜索

```bash
curl "http://localhost:8080/api/knowledge/search/category?query=故宫&category=景点&topK=3"
```

### 4. RAG 问答

```bash
curl "http://localhost:8080/api/knowledge/ask?question=北京有哪些著名景点"
```

### 5. 获取统计信息

```bash
curl http://localhost:8080/api/knowledge/stats
```

### 6. 删除文档

```bash
# 删除指定文档
curl -X DELETE http://localhost:8080/api/knowledge/1

# 删除某个类别的所有文档
curl -X DELETE http://localhost:8080/api/knowledge/category/景点
```

## 集成到 Agent

### 自动集成

PlanningAgent 已自动集成 RAG 功能：

1. 用户发送旅行请求
2. PlanningAgent 检索相关知识
3. 将知识注入到系统提示词
4. 执行 ReAct 循环生成旅行计划

### 手动增强

如需手动增强提示词，可使用 `RagService`：

```java
@Autowired
private RagService ragService;

String enhancedPrompt = ragService.buildEnhancedPrompt(
    systemPrompt, 
    userQuery, 
    5  // topK
);
```

## 预置知识

系统启动时会自动导入以下预置知识：

| 类别 | 数量 | 内容 |
|------|------|------|
| 景点 | 5 | 故宫、长城、西湖、九寨沟、张家界 |
| 交通 | 5 | 高铁、首都机场、浦东机场等 |
| 签证 | 5 | 过境免签、签证申请等 |
| 美食 | 5 | 北京烤鸭、四川火锅、广州早茶等 |

## 文件结构

```
trip-agent/src/main/java/com/tripagent/knowledge/
├── model/
│   └── TripKnowledge.java          # 知识文档实体
├── repository/
│   └── TripKnowledgeRepository.java # 数据仓库
├── service/
│   ├── EmbeddingService.java        # 文本向量化服务
│   ├── DocumentSplitterService.java # 文档分块服务
│   ├── KnowledgeService.java        # 知识管理服务
│   ├── RagService.java              # RAG 服务
│   └── KnowledgeImportService.java  # 预置知识导入
└── controller/
    └── KnowledgeController.java     # REST API
```

## 测试

运行测试类验证功能：

```bash
mvn test -Dtest=KnowledgeServiceTest
```

## 注意事项

1. **API Key 配置**: 确保 `QWEN_API_KEY` 环境变量已正确配置
2. **数据库**: 确保 PostgreSQL 已启用 pgvector 扩展
3. **向量维度**: 默认使用 2048 维（text-embedding-v4 标准）
4. **分块大小**: 默认 500 字符，可在 `DocumentSplitterService` 中调整

## 扩展建议

1. **添加更多知识**: 上传更多旅行相关文档
2. **优化分块策略**: 根据文档特点调整分块大小
3. **添加混合检索**: 结合关键词和向量检索
4. **实现知识更新**: 支持文档的增量更新
