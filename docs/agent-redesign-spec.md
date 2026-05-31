# Trip Agent 架构重构设计文档

> 创建日期：2026-05-31
> 状态：设计阶段

---

## 一、背景与问题

### 现有架构的问题

1. **PlanningAgent 的规划是摆设**：生成的步骤列表没有被真正执行，ExecutionAgent 里硬编码了处理逻辑
2. **状态机是假的**：SupervisorAgent 里的 while + switch 是线性执行，没有分支、没有重试、没有动态跳转
3. **四个"Agent"其实是四个普通 Service**：每个 Agent 内部就是调一次 LLM 解析 JSON，没有工具调用、没有推理循环、没有自主决策
4. **Chat 和 Plan 完全割裂**：聊天走 processMessage，规划走 executeTripPlan，两个模式互不相通
5. **接口重复**：三个端点做同一件事（/plan、/plan/stream、/plan/simple）
6. **记忆系统没串起来**：注入了多个 Service，但实际只用了 MemoryManager

### 核心问题

这个项目表面是 Agent Pipeline，实际是四个顺序调用的 Service 方法，加了一层包装。

---

## 二、改造目标

### 核心能力（按依赖顺序实现）

1. **自主决策**：Agent 能够根据当前状态，自主决定下一步做什么
2. **动态规划**：Agent 能够根据中间结果调整计划
3. **多轮交互**：Agent 能够与用户多轮对话，逐步澄清需求
4. **记忆学习**：Agent 能够从历史对话中学习用户偏好

### 架构模式

**Plan-and-Execute + ReAct 混合模式**

- **Plan-and-Execute**：用于整体流程控制（制定计划 → 执行计划）
- **ReAct**：用于每个步骤的执行（思考 → 行动 → 观察）

### API 设计

**统一接口**：`POST /api/agent/chat`

- 支持聊天和规划
- 全部流式响应（SSE）
- 实时推送 Agent 的推理过程

---

## 三、改造范围

### 保留

- **trip-common**：VO 对象（WeatherInfo、PoiInfo、HotelInfo）
- **trip-tools-server**：MCP 工具定义和实现
- **数据库 schema**：可扩展
- **配置文件**：application.yml

### 重写

- **trip-agent**：完全重写
  - Agent 核心逻辑（推理循环、工具调用、状态管理）
  - Controller（统一接口）
  - Service（记忆系统、工具调用）
  - Config（模型配置、Agent 配置）

### 保留但可能需要调整

- API 接口路径（为了兼容性）
- 数据库表结构（为了扩展记忆系统）

---

## 四、整体架构

### 核心组件

```
┌─────────────────────────────────────────────────────────────┐
│                      TripAgent（主管）                       │
│  - 接收用户输入                                               │
│  - 协调 PlanningAgent 和 ExecutionAgent                      │
│  - 管理对话状态和记忆                                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   PlanningAgent（规划）                       │
│  - 使用 ReAct 循环制定旅行计划                                 │
│  - 调用 MCP 工具获取信息（天气、景点、酒店）                     │
│  - 生成结构化的旅行计划（Plan）                                │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                   ExecutionAgent（执行）                      │
│  - 逐步执行旅行计划                                           │
│  - 每个步骤内部使用有限的 ReAct 循环（最多 3 次迭代）            │
│  - 根据中间结果调整计划                                       │
│  - 生成最终的旅行方案                                         │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      MCP 工具层                              │
│  - getWeather（天气查询）                                     │
│  - searchAttractions（景点搜索）                              │
│  - searchHotels（酒店搜索）                                   │
│  - searchRestaurants（餐厅搜索）                              │
└─────────────────────────────────────────────────────────────┘
```

### 数据流

```
用户输入 → TripAgent → PlanningAgent → Plan → ExecutionAgent → 结果
                ↓              ↓                ↓              ↓
            记忆加载        ReAct循环         ReAct循环       SSE推送
                ↓              ↓                ↓              ↓
            状态管理        工具调用          工具调用        流式响应
```

---

## 五、关键设计决策

### 1. TripAgent 作为主管

- 接收用户输入，协调 PlanningAgent 和 ExecutionAgent
- 管理对话状态和记忆
- 处理异常和降级

### 2. PlanningAgent 使用完整的 ReAct 循环

**职责**：
- 分析用户需求
- 调用 MCP 工具获取信息（天气、景点、酒店）
- 生成结构化的旅行计划（Plan）

**ReAct 循环示例**：
```
思考：用户想去杭州玩3天，我需要先查天气
行动：getWeather("杭州")
观察：杭州明天晴，25°C
思考：天气不错，可以推荐户外景点
行动：searchAttractions("杭州")
观察：找到5个景点：西湖、灵隐寺...
思考：我已经收集了足够的信息，可以制定计划了
输出：Plan（步骤列表）
```

### 3. ExecutionAgent 使用有限的 ReAct 循环

**职责**：
- 逐步执行旅行计划
- 每个步骤内部使用有限的 ReAct 循环（最多 3 次迭代）
- 根据中间结果调整计划

**ReAct 循环示例**：
```
步骤1：查询天气
  思考：我需要查询杭州的天气
  行动：getWeather("杭州")
  观察：杭州明天晴，25°C
  结果：天气信息

步骤2：推荐景点
  思考：天气不错，可以推荐户外景点
  行动：searchAttractions("杭州")
  观察：找到5个景点：西湖、灵隐寺...
  思考：用户预算5000元，需要筛选合适的景点
  结果：推荐的景点列表

步骤3：推荐酒店
  思考：用户预算5000元，3天，每天住宿预算约1000元
  行动：searchHotels("杭州")
  观察：找到10家酒店
  思考：需要筛选价格合适的酒店
  结果：推荐的酒店列表
```

### 4. 全部流式响应

使用 SSE 推送 Agent 的推理过程：
- `thinking`：Agent 的思考过程
- `action`：Agent 调用的工具
- `observation`：工具返回的结果
- `result`：最终结果

---

## 六、API 接口设计

### 统一接口

```
POST /api/agent/chat
{
  "userId": "user123",
  "message": "我想去杭州玩3天",
  "conversationId": "conv456"  // 可选，支持多轮对话
}
```

### 响应格式（SSE 流式）

```
event: thinking
data: {"type": "thinking", "content": "用户想去杭州玩3天，我需要先查天气..."}

event: action
data: {"type": "action", "tool": "getWeather", "params": {"city": "杭州"}}

event: observation
data: {"type": "observation", "content": "杭州明天晴，25°C"}

event: thinking
data: {"type": "thinking", "content": "天气不错，可以推荐户外景点..."}

event: action
data: {"type": "action", "tool": "searchAttractions", "params": {"city": "杭州"}}

event: observation
data: {"type": "observation", "content": "找到5个景点：西湖、灵隐寺..."}

event: result
data: {"type": "result", "content": "为您规划了杭州3天游..."}
```

---

## 七、实现顺序

### 阶段一：自主决策（核心能力）

1. 实现 TripAgent 主管
2. 实现 PlanningAgent 的 ReAct 循环
3. 实现 MCP 工具调用
4. 实现基础的流式响应

### 阶段二：动态规划

1. 实现 ExecutionAgent 的 ReAct 循环
2. 实现计划调整逻辑
3. 实现异常处理和降级

### 阶段三：多轮交互

1. 实现对话状态管理
2. 实现需求澄清逻辑
3. 实现上下文记忆

### 阶段四：记忆学习

1. 实现用户偏好提取
2. 实现长期记忆存储
3. 实现记忆应用

---

## 八、技术约束

- **技术栈**：Java 21 + Spring Boot 4.0.5 + Spring AI 2.0.0-M8
- **AI 模型**：DeepSeek V4 Pro / V4 Flash
- **协议**：MCP（Streamable HTTP）
- **数据库**：PostgreSQL 16

---

## 九、已确认的设计决策

### 1. 数据库表结构：保留现有结构，扩展新字段

- **SPRING_AI_CHAT_MEMORY**：保留，Spring AI 框架自动管理
- **user_profile**：保留，扩展新字段（visited_cities、travel_style 等）
- 不需要重新设计表结构，避免数据迁移问题

### 2. 计划审查功能：先不实现，后续再添加

- 阶段一、二：实现核心 Agent 能力，不包含计划审查
- 阶段三（多轮交互）：可以考虑添加计划审查功能

### 3. 多用户并发：支持

- 使用 ConcurrentHashMap 管理每个用户的 Agent 状态
- 每个用户独立的对话上下文和记忆

---

## 十、参考资源

- [ReAct: Synergizing Reasoning and Acting in Language Models](https://arxiv.org/abs/2210.03629)
- [Plan-and-Solve Prompting](https://arxiv.org/abs/2305.04091)
- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
