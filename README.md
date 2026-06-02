# Trip Agent

基于 Spring AI 的智能旅行规划助手，采用 **Plan-and-Execute + ReAct** 混合架构，支持多轮对话、自动工具调用、流式响应。

## ✨ 特性

- 🤖 **智能规划**：AI 自动分析需求，生成个性化旅行计划
- 🔧 **自动工具调用**：通过 MCP 协议调用天气、景点、酒店、餐厅等工具
- 💬 **多轮对话**：支持上下文理解，记住对话历史
- 📡 **实时流式响应**：SSE 实时推送 Agent 推理过程（Think → Act → Observe）
- 🧠 **记忆系统**：短期记忆、LLM 摘要压缩、用户偏好长期记忆
- 🔄 **混合架构**：Plan-and-Execute 全局规划 + ReAct 局部执行
- 📚 **RAG 知识增强**：本地人攻略知识库，提供更接地气的建议
- 🎯 **意图识别**：智能判断用户意图，无关查询自动跳过 RAG 检索
- 🔍 **多路召回检索**：语义向量 + BM25 关键词双路召回 + RRF 融合排序
- ✂️ **递归语义分块**：按标题→段落→句子→固定字符递归切分，保留语义完整性

## 🏗️ 架构

```
用户请求 → IntentRecognizer → PlanningAgent → ExecutionAgent → MCP Tools → 结果
           (意图识别)          (制定计划)      (逐步执行)      (天气/景点/酒店/餐厅)
```

### 核心流程

```
1. 意图识别：判断是否需要 RAG 知识库检索
2. 规划阶段：PlanningAgent 调用工具收集信息，生成结构化旅行计划
3. 执行阶段：ExecutionAgent 逐步执行计划中的每个步骤
4. 结果汇总：收集所有步骤结果，生成最终旅行方案
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `TripAgent` | 主协调器，编排规划和执行阶段 |
| `PlanningAgent` | 使用 ReAct 循环 + RAG 生成旅行计划 |
| `ExecutionAgent` | 逐步执行计划中的每个步骤 |
| `IntentRecognizer` | LLM 意图分类，判断 RAG vs GENERAL |
| `ReActLoop` | ReAct（Think → Act → Observe）循环实现 |
| `LlmClient` | LLM 调用客户端，支持自动重试 |
| `KnowledgeService` | RAG 知识检索（递归分块 + 多路召回 + RRF 融合） |
| `TextSplitter` | 递归语义分块器（标题→段落→句子→固定字符） |
| `MemoryCompressionService` | LLM 摘要压缩，防止上下文溢出 |
| `LongTermMemoryService` | 从对话中提取用户偏好、事实、约束 |
| `TokenUsageTracker` | Token 用量追踪与统计 |

## 🚀 快速开始

### 环境要求

- Java 21+
- Maven 3.9+
- PostgreSQL 12+（或 Docker）

### 1. 克隆项目

```bash
git clone https://github.com/liluollk/TripAgent.git
cd TripAgent
```

### 2. 配置 API Keys

项目需要以下 API Keys：

| 服务 | 用途 | 获取方式 |
|------|------|---------|
| DeepSeek | AI 对话模型 | [DeepSeek 官网](https://platform.deepseek.com/) |
| 千问 | 文本向量化（Embedding） | [阿里云百炼](https://dashscope.console.aliyun.com/) |
| 和风天气 | 天气查询 | [和风天气开发平台](https://dev.qweather.com/) |
| 高德地图 | 景点/餐厅/酒店搜索 | [高德开放平台](https://lbs.amap.com/) |

配置方式（二选一）：

**方式 A：环境变量（推荐）**

```bash
export DEEPSEEK_API_KEY=your-key
export QWEN_API_KEY=your-key
export QWEATHER_API_KEY=your-key
export QWEATHER_HOST=your-host
export QWEATHER_GEO_HOST=your-geo-host
export AMAP_API_KEY=your-key
```

**方式 B：创建本地配置文件**

```bash
cp trip-agent/src/main/resources/application.yml.example trip-agent/src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，填入真实的 API Keys。

### 3. 启动数据库

使用 Docker（推荐）：

```bash
docker run -d \
  --name trip-agent-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16

# 创建数据库
docker exec trip-agent-postgres psql -U postgres -c "CREATE DATABASE trip_agent;"
```

或使用本地 PostgreSQL，创建 `trip_agent` 数据库。

### 4. 启动服务

本项目是多模块 Maven 项目，需要分别启动两个服务：

**① 启动 MCP Server（工具服务）**：

```bash
cd trip-tools-server
mvn spring-boot:run
```

MCP Server 运行在 `http://localhost:8081`，提供天气、景点、酒店、餐厅查询工具。

**② 启动 Trip Agent（新终端）**：

```bash
cd trip-agent
mvn spring-boot:run
```

Trip Agent 运行在 `http://localhost:8080`，自动连接 MCP Server 获取工具能力。

### 5. 测试

```bash
# 旅行规划（SSE 流式响应）
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "test", "message": "我想去南京玩3天，帮我规划一下"}'

# 健康检查
curl http://localhost:8080/api/agent/health

# 活跃会话数
curl http://localhost:8080/api/agent/sessions
```

## 📚 API 文档

启动服务后访问 Swagger UI：

```
http://localhost:8080/swagger-ui.html
```

### 主要接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/chat` | POST | 旅行规划对话（SSE 流式响应） |
| `/api/agent/health` | GET | 健康检查 |
| `/api/agent/sessions` | GET | 活跃会话数 |

### 请求示例

```json
{
  "userId": "user123",
  "sessionId": "session456",
  "message": "计划一个3天的南京之旅"
}
```

### 响应格式（SSE 流）

```
event:thinking
data:开始规划阶段...

event:planning
data:{"cities":["南京"],"steps":[...],"summary":"..."}

event:executing
data:获取南京天气信息

event:result
data:旅行计划摘要...
```

## 🛠️ 技术栈

| 技术 | 用途 |
|------|------|
| Java 21 | 编程语言 |
| Spring Boot 4.0.5 | 应用框架 |
| Spring AI 2.0.0-M8 | AI 框架（ChatClient、Tool Calling、MCP、VectorStore） |
| DeepSeek V4 Pro/Flash | AI 对话模型（规划用 Pro，执行用 Flash） |
| 千问 Embedding v4 | 文本向量化 |
| PostgreSQL 16 + pgvector | 数据库 + 向量存储 |
| MCP（Model Context Protocol） | 工具协议（Client + Server） |
| Spring Retry | LLM 调用自动重试 |
| Caffeine | 本地缓存 |
| SpringDoc OpenAPI 3.0.3 | API 文档 |

## 📁 项目结构

```
TripAgent/
├── pom.xml                          # 根 POM（多模块管理）
│
├── trip-common/                     # 公共模块（数据模型）
│   └── src/main/java/com/tripcommon/
│       └── model/vo/               # WeatherInfo、PoiInfo、HotelInfo
│
├── trip-tools-server/               # MCP Server（工具服务，端口 8081）
│   └── src/main/java/com/triptools/
│       ├── tools/TripTools.java     # @Tool 注解定义 4 个工具
│       ├── service/                 # WeatherService、PoiService、HotelService
│       └── config/                  # 配置类（AmapProperties、WeatherProperties）
│
├── trip-agent/                      # 主项目（MCP Client，端口 8080）
│   └── src/main/
│       ├── java/com/tripagent/
│       │   ├── agent/               # Agent 核心
│       │   │   ├── core/            # Agent 接口、ReActLoop、LlmClient、IntentRecognizer
│       │   │   ├── planning/        # PlanningAgent、Plan、PlanStep
│       │   │   └── execution/       # ExecutionAgent、StepResult
│       │   ├── controller/          # TripController、MetricsController
│       │   ├── service/             # SessionManager、TokenUsageTracker
│       │   │   └── memory/          # MemoryCompressionService、LongTermMemoryService
│       │   ├── knowledge/           # KnowledgeService
│       │   │   ├── chunk/           # TextSplitter（递归语义分块器）
│       │   │   └── retrieve/        # SemanticRetriever、Bm25Retriever、MultiRetriever
│       │   ├── model/               # DTO、Entity
│       │   ├── repository/          # JPA Repository
│       │   ├── config/              # @ConfigurationProperties 类
│       │   ├── exception/           # 全局异常处理
│       │   └── utils/               # JsonUtils
│       └── resources/
│           ├── application.yml      # 主配置（环境变量）
│           ├── application.yml.example
│           ├── knowledge/           # RAG 知识库文档
│           └── db/                  # 数据库迁移脚本
│
└── README.md
```

## 🔧 核心功能详解

### Plan-and-Execute + ReAct 混合架构

- **PlanningAgent**（全局规划）：分析用户需求，调用工具收集信息，生成结构化的旅行计划（JSON 格式，包含步骤列表）
- **ExecutionAgent**（局部执行）：逐步执行计划中的每个步骤，每步独立调用对应工具获取结果
- **ReActLoop**：每个 Agent 内部使用 Think → Act → Observe 循环，由 Spring AI 原生工具调用驱动

### 记忆系统

三层记忆协同工作：

- **短期记忆**：滑动窗口保存最近 20 轮对话（`SessionManager`）
- **摘要压缩**：对话超过 15 条时，LLM 自动将旧消息压缩为摘要，注入 system prompt（`MemoryCompressionService`）
- **长期记忆**：从对话中提取用户偏好（目的地、预算、风格等），持久化到 PostgreSQL（`LongTermMemoryService`）

### RAG 知识增强

完整的检索增强生成流水线：

1. **递归语义分块**（`TextSplitter`）：按标题→段落→句子→固定字符递归切分，相邻块保留重叠
2. **意图识别**（`IntentRecognizer`）：LLM 判断用户是否在问攻略相关问题，无关查询跳过 RAG
3. **多路召回**（`MultiRetriever`）：语义向量检索 + BM25 关键词检索，双路互补
4. **RRF 融合**：Reciprocal Rank Fusion 算法融合多路结果，只看排名不看绝对分数
5. **向量存储**：pgvector + 千问 Embedding，支持余弦相似度检索

### 工具集成

通过 MCP 协议集成外部服务，`trip-tools-server` 暴露 4 个工具：

| 工具 | 说明 |
|------|------|
| `getWeather` | 获取城市实时天气（和风天气 API） |
| `searchAttractions` | 搜索旅游景点（高德地图 API） |
| `searchHotels` | 搜索酒店（本地数据 + 高德 API） |
| `searchRestaurants` | 搜索餐厅（高德地图 API） |

## 📄 许可证

MIT License

## 🙏 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI 框架
- [DeepSeek](https://platform.deepseek.com/) - AI 模型
- [MCP](https://modelcontextprotocol.io/) - 工具协议
- [和风天气](https://dev.qweather.com/) - 天气 API
- [高德地图](https://lbs.amap.com/) - 地图 API
