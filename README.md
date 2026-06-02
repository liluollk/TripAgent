# Trip Agent

基于 Spring AI 的智能旅行规划助手，采用 **Plan-and-Execute + ReAct** 混合架构，支持多轮对话、自动工具调用、流式响应。

## ✨ 特性

- 🤖 **智能规划**：AI 自动分析需求，生成个性化旅行计划
- 🔧 **自动工具调用**：通过 MCP 协议调用天气、景点、酒店、餐厅等工具
- 💬 **多轮对话**：支持上下文理解，记住对话历史
- 📡 **实时流式响应**：SSE 实时推送 Agent 推理过程
- 🧠 **记忆系统**：短期记忆、记忆压缩、长期记忆
- 🔄 **混合架构**：Plan-and-Execute 全局规划 + ReAct 局部执行
- 📚 **RAG 知识增强**：本地人攻略知识库，提供更实用的建议
- 🎯 **意图识别**：智能判断用户意图，无关查询自动跳过 RAG 检索
- 🔍 **多路召回检索**：语义向量 + BM25 关键词双路召回 + RRF 融合排序
- ✂️ **递归语义分块**：按标题→段落→句子→固定字符递归切分，保留语义完整性

## 🏗️ 架构

```
用户请求 → PlanningAgent → ExecutionAgent → MCP Tools → 结果
            (制定计划)      (逐步执行)      (实际工具)
```

### 核心组件

| 组件 | 职责 |
|------|------|
| `TripAgent` | 主协调器，编排规划和执行阶段 |
| `PlanningAgent` | 使用 ReAct 循环生成旅行计划 |
| `ExecutionAgent` | 使用有限 ReAct 执行单个计划步骤 |
| `IntentRecognizer` | 意图识别，判断是否需要 RAG 检索 |
| `ReActLoop` | ReAct 循环实现 |
| `MemoryCompressionService` | 对话记忆压缩 |
| `LongTermMemoryService` | 用户长期记忆提取 |
| `KnowledgeService` | RAG 知识检索（多路召回 + RRF 融合） |
| `TextSplitter` | 递归语义分块器 |

## 🚀 快速开始

### 环境要求

- Java 21+
- Maven 3.8+
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
| DeepSeek | AI 模型 | [DeepSeek 官网](https://platform.deepseek.com/) |
| 千问 | 文本向量化 | [阿里云百炼](https://dashscope.console.aliyun.com/) |
| 和风天气 | 天气查询 | [和风天气开发平台](https://dev.qweather.com/) |
| 高德地图 | 景点/地理信息 | [高德开放平台](https://lbs.amap.com/) |

复制配置模板并填入你的 API Keys：

```bash
cp trip-agent/src/main/resources/application.yml.example trip-agent/src/main/resources/application.yml
```

编辑 `application.yml`，将环境变量替换为真实的 API Keys，或创建 `application-dev.yml` 本地开发配置。

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

**启动 MCP Server**（工具服务）：
```bash
cd trip-tools-server
mvn spring-boot:run
```

MCP Server 运行在 `http://localhost:8081`

**启动 Trip Agent**（新终端）：
```bash
cd trip-agent
mvn spring-boot:run
```

Trip Agent 运行在 `http://localhost:8080`

### 5. 测试 API

```bash
# 旅行规划
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"userId": "test", "message": "我想去南京玩3天，帮我规划一下"}'

# 健康检查
curl http://localhost:8080/api/agent/health
```

## 📚 API 文档

启动服务后访问 Swagger UI：
- URL: http://localhost:8080/swagger-ui/index.html

### 主要接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/agent/chat` | POST | 旅行规划（SSE 流式响应） |
| `/api/agent/health` | GET | 健康检查 |
| `/api/agent/sessions` | GET | 活跃会话数 |

### 请求示例

```json
{
  "userId": "user123",
  "sessionId": "session456",
  "message": "计划一个3天的东京之旅"
}
```

### 响应格式（SSE 流）

```
event:thinking
data:Starting planning phase...

event:planning
data:{"planId":"...","cities":["Tokyo"],"steps":[...]}

event:executing
data:{"step":"Check weather","status":"executing"}

event:result
data:Travel Plan Summary...
```

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 编程语言 |
| Spring Boot | 4.0.5 | 应用框架 |
| Spring AI | 2.0.0-M8 | AI 框架 |
| DeepSeek | V4 Pro/Flash | AI 模型 |
| PostgreSQL | 16 | 数据库 |
| pgvector | - | 向量存储 |
| Caffeine | - | 缓存 |
| MCP | - | 工具协议 |
| SpringDoc OpenAPI | 3.0.3 | API 文档 |

## 📁 项目结构

```
TripAgent/
├── trip-agent/                    # 主项目（MCP Client）
│   ├── src/main/java/com/tripagent/
│   │   ├── agent/                 # Agent 核心
│   │   │   ├── core/             # 基础组件（ReAct、LlmClient、意图识别等）
│   │   │   ├── planning/         # 规划代理
│   │   │   └── execution/        # 执行代理
│   │   ├── controller/           # REST 控制器
│   │   ├── service/              # 业务服务
│   │   │   └── memory/           # 记忆服务
│   │   ├── knowledge/            # RAG 知识服务
│   │   │   ├── chunk/            # 文档分块（递归语义分块器）
│   │   │   └── retrieve/         # 多路召回（语义 + BM25 + RRF 融合）
│   │   ├── model/                # 数据模型
│   │   ├── repository/           # 数据访问
│   │   ├── config/               # 配置类（@ConfigurationProperties）
│   │   ├── exception/            # 异常处理
│   │   └── utils/                # 工具类
│   └── src/main/resources/
│       ├── application.yml        # 主配置（环境变量）
│       ├── application.yml.example
│       ├── knowledge/            # 知识库文档
│       └── db/migration/         # 数据库迁移
│
├── trip-tools-server/             # MCP Server（工具服务）
│   └── src/main/java/com/triptools/
│       ├── tools/                 # MCP 工具实现
│       └── service/               # 服务实现
│
└── README.md
```

## 🔧 核心功能

### 智能规划

AI 自动分析用户需求，调用工具获取实时信息，生成结构化旅行计划。

### ReAct 推理

Agent 使用 ReAct（Reasoning + Acting）循环：
- **Think**：分析当前状态，决定下一步行动
- **Act**：调用工具获取信息
- **Observe**：处理工具返回结果

### 记忆系统

- **短期记忆**：滑动窗口保存最近 20 条对话
- **记忆压缩**：对话超过阈值时自动摘要旧消息
- **长期记忆**：从对话中提取用户偏好、事实、约束

### RAG 知识增强

集成本地人攻略知识库，提供更接地气的旅行建议：
- **递归语义分块**：按标题→段落→句子→固定字符递归切分，保留语义完整性
- **多路召回**：语义向量检索 + BM25 关键词检索，双路互补
- **RRF 融合排序**：Reciprocal Rank Fusion 算法融合多路结果
- **意图识别前置**：智能判断用户意图，无关查询自动跳过 RAG，节省延迟
- 使用 pgvector + 千问 Embedding 进行向量存储

### 工具集成

通过 MCP 协议集成外部服务：
- `getWeather` - 天气查询
- `searchAttractions` - 景点搜索
- `searchHotels` - 酒店搜索
- `searchRestaurants` - 餐厅搜索
- `searchTransportation` - 交通查询

## 🤝 贡献

欢迎贡献！请遵循以下步骤：

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 🙏 致谢

- [Spring AI](https://spring.io/projects/spring-ai) - AI 框架
- [DeepSeek](https://platform.deepseek.com/) - AI 模型
- [MCP](https://modelcontextprotocol.io/) - 工具协议
- [和风天气](https://dev.qweather.com/) - 天气 API
- [高德地图](https://lbs.amap.com/) - 地图 API
