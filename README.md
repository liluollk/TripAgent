# Trip Agent - 旅行规划助手

基于 Spring AI 的智能旅行规划助手，采用 **Plan-and-Execute + ReAct** 混合架构，支持多轮对话、自动工具调用、流式响应。

## 技术栈

- **后端**：Java 21 + Spring Boot 4.0.5 + Spring AI 2.0.0-M8
- **AI 模型**：DeepSeek V4 Pro / V4 Flash
- **数据库**：PostgreSQL 16
- **外部 API**：和风天气、高德地图
- **协议**：MCP（Model Context Protocol）

## 架构设计

### Agent 系统

采用 **Plan-and-Execute + ReAct** 混合架构：

- **TripAgent**：主协调器，编排规划和执行阶段
- **PlanningAgent**：使用完整 ReAct 循环生成旅行计划
- **ExecutionAgent**：使用有限 ReAct（最多 3 次迭代）执行单个计划步骤

```
用户请求
    ↓
TripAgent (协调器)
    ↓
PlanningAgent (ReAct 循环)
    ├── Think: 分析需求
    ├── Act: 调用工具获取信息
    └── Observe: 收集结果，生成计划
    ↓
ExecutionAgent (有限 ReAct)
    ├── 执行步骤 1
    ├── 执行步骤 2
    └── ...
    ↓
最终结果
```

### API

统一接口：

```
POST /api/agent/chat
Content-Type: application/json
Accept: text/event-stream

{
    "userId": "user123",
    "sessionId": "session456",  // 可选
    "message": "计划一个3天的东京之旅"
}
```

响应：SSE 流，包含以下事件类型：
- `thinking`：Agent 的推理过程
- `planning`：生成的计划
- `executing`：步骤执行状态
- `result`：最终结果
- `error`：错误信息

## 项目结构

```
Trip Agent/
├── trip-agent/                    # 主项目（MCP Client）
│   ├── src/main/java/com/tripagent/
│   │   ├── agent/                 # Agent 类
│   │   │   ├── core/              # 核心基础设施
│   │   │   │   ├── Agent.java           # Agent 接口
│   │   │   │   ├── AgentContext.java    # 执行上下文
│   │   │   │   ├── AgentStep.java       # 步骤结果
│   │   │   │   ├── ReActLoop.java       # ReAct 循环实现
│   │   │   │   ├── ToolRegistry.java    # 工具注册表
│   │   │   │   └── SseEventEmitter.java # SSE 事件发射器
│   │   │   ├── planning/          # 规划 Agent
│   │   │   │   ├── Plan.java           # 计划数据结构
│   │   │   │   ├── PlanStep.java       # 计划步骤
│   │   │   │   └── PlanningAgent.java  # 规划 Agent
│   │   │   ├── execution/         # 执行 Agent
│   │   │   │   ├── StepResult.java     # 步骤结果
│   │   │   │   └── ExecutionAgent.java # 执行 Agent
│   │   │   └── TripAgent.java     # 主协调器
│   │   ├── config/                # 配置类
│   │   ├── controller/            # 控制器
│   │   ├── exception/             # 异常体系
│   │   ├── model/                 # 数据模型
│   │   ├── repository/            # 数据访问层
│   │   ├── service/               # 服务层
│   │   │   ├── SessionManager.java      # 会话管理
│   │   │   ├── ChatMemoryService.java   # 聊天记忆接口
│   │   │   └── impl/                    # 实现类
│   │   └── utils/                 # 工具类
│   └── src/main/resources/
│       ├── application.yml
│       └── db/schema.sql
│
├── trip-tools-server/             # MCP Server（独立项目）
│   └── src/main/java/com/triptools/
│       ├── tools/                 # 工具定义
│       └── service/               # 服务实现
│
└── README.md
```

## 快速开始

### 环境要求

- Java 21+
- Maven 3.8+
- Docker（用于 PostgreSQL）

### 1. 启动数据库

```bash
docker run -d \
  --name trip-agent-postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16
```

创建数据库：
```bash
docker exec trip-agent-postgres psql -U postgres -c "CREATE DATABASE tripagent;"
```

### 2. 配置环境变量

```bash
export DEEPSEEK_API_KEY=your-deepseek-api-key
export QWEATHER_API_KEY=your-qweather-api-key
export AMAP_API_KEY=your-amap-api-key
```

或在 `application.yml` 中直接修改。

### 3. 启动 MCP Server

```bash
cd trip-tools-server
mvn spring-boot:run
```

MCP Server 运行在 `http://localhost:8081`

### 4. 启动主项目

```bash
cd trip-agent
mvn spring-boot:run
```

主项目运行在 `http://localhost:8080`

## API 接口

### 统一聊天接口（SSE 流式响应）

```bash
curl -X POST http://localhost:8080/api/agent/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"userId": "test", "message": "计划一个3天的东京之旅"}'
```

响应示例（SSE 流）：
```
event:thinking
data:Starting planning phase...

event:planning
data:{"planId":"...","cities":["Tokyo"],"steps":[...]}

event:executing
data:{"step":"Check weather","status":"executing"}

event:executing
data:{"step":"Check weather","status":"completed"}

event:result
data:Travel Plan Summary...
```

### 健康检查

```bash
curl http://localhost:8080/api/agent/health
```

### 活跃会话数

```bash
curl http://localhost:8080/api/agent/sessions
```

## 核心功能

### 1. 智能规划（Plan-and-Execute）

采用 Plan-and-Execute 模式，先生成完整计划，再逐步执行：
- 自动分析用户需求
- 调用工具获取实时信息（天气、景点、酒店、餐厅）
- 生成结构化旅行计划

### 2. ReAct 推理循环

Agent 使用 ReAct（Reasoning + Acting）循环：
- **Think**：分析当前状态，决定下一步行动
- **Act**：调用工具获取信息
- **Observe**：处理工具返回结果
- 循环直到任务完成

### 3. 多轮对话

支持上下文理解，AI 能记住之前的对话内容。

```
用户：计划一个3天的东京之旅
AI：[生成计划...]

用户：预算控制在5000元以内
AI：[调整计划，考虑预算...]
```

### 4. 自动工具调用

通过 MCP 协议自动调用外部工具：
- `getWeather` - 获取天气
- `searchAttractions` - 搜索景点
- `searchHotels` - 搜索酒店
- `searchRestaurants` - 搜索餐厅

### 5. 实时流式响应

使用 SSE 实现实时推送，用户可以观察 Agent 的推理过程：
- 规划阶段的思考过程
- 工具调用的实时状态
- 执行进度的实时更新

### 6. 多用户支持

使用 ConcurrentHashMap 管理多个用户会话，支持并发访问。

## 配置说明

### application.yml

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com
    mcp:
      client:
        type: SYNC
        streamable-http:
          connections:
            trip-tools:
              url: http://localhost:8081

trip:
  weather:
    api-key: ${QWEATHER_API_KEY}
    host: km49vk34x2.re.qweatherapi.com
    geo-host: km49vk34x2.re.qweatherapi.com
  amap:
    api-key: ${AMAP_API_KEY}
  agent:
    memory:
      window-size: 20
      keep-recent: 10
```

## 开发说明

### 构建

```bash
mvn clean package
```

### 运行测试

```bash
mvn test
```

## 许可证

MIT License
