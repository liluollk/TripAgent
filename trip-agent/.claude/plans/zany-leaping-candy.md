# 熔断降级机制实现计划

## Context

当前项目存在以下问题：
- LLM 调用（PlanningAgent/ExecutionAgent）无重试机制
- Embedding 调用无重试、无降级
- `spring-retry` 依赖已引入但未实际使用
- 缺乏熔断机制，服务不可用时会持续请求导致雪崩

**目标**：添加基于 Resilience4j 的重试 + 熔断 + 降级机制，提高系统可用性。

---

## 架构设计

```
请求 → [重试] → [熔断器] → [降级方案]
         │          │           │
     失败重试    达阈值断路    返回兜底结果
```

### 需要保护的组件

| 组件 | 重试 | 熔断 | 降级策略 |
|------|------|------|----------|
| **LLM 调用** | 2 次 | 连续 5 次失败 → 熔断 30s | 返回默认计划/错误提示 |
| **Embedding 调用** | 2 次 | 连续 5 次失败 → 熔断 60s | 跳过 RAG，直接回答 |
| **MCP 工具调用** | 已有 3 次 | 连续 3 次失败 → 熔断 30s | 返回友好错误 |

---

## 实现步骤

### Step 1: 添加依赖

**文件**: `pom.xml`

添加 Resilience4j + Spring AOP 依赖：
```xml
<!-- Resilience4j 熔断器 -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### Step 2: 配置熔断器参数

**文件**: `application.yml`

添加 Resilience4j 配置：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      llm-cb:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3
        register-health-indicator: true
      embedding-cb:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 60s
        permitted-number-of-calls-in-half-open-state: 2
      mcp-cb:
        sliding-window-size: 5
        failure-rate-threshold: 60
        wait-duration-in-open-state: 30s
  retry:
    instances:
      llm-retry:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
      embedding-retry:
        max-attempts: 3
        wait-duration: 500ms
```

### Step 3: 创建熔断降级服务

**新建文件**: `src/main/java/com/tripagent/config/CircuitBreakerConfig.java`

- 配置 CircuitBreakerRegistry、RetryRegistry
- 自定义异常分类（可重试 vs 不可重试）

### Step 4: 改造 LLM 调用

**文件**: `src/main/java/com/tripagent/agent/planning/PlanningAgent.java`

- 为 `callLLM()` 方法添加 `@CircuitBreaker` + `@Retryable` 注解
- 添加 `fallbackMethod` 返回默认计划

**文件**: `src/main/java/com/tripagent/agent/execution/ExecutionAgent.java`

- 为 `combineResults()` 方法添加熔断注解
- 添加降级方法返回错误提示

### Step 5: 改造 Embedding 调用

**文件**: `src/main/java/com/tripagent/knowledge/service/EmbeddingService.java`

- 为 `embed()` 和 `embedBatch()` 添加熔断注解
- 降级返回空数组

**文件**: `src/main/java/com/tripagent/knowledge/service/RagService.java`

- 检查 Embedding 结果，空数组时跳过 RAG

### Step 6: 改造 MCP 工具调用

**文件**: `src/main/java/com/tripagent/agent/core/ToolRegistry.java`

- 为 `executeTool()` 添加 `@CircuitBreaker` 注解
- 已有重试机制，只需添加熔断

### Step 7: 添加健康检查端点

**文件**: `src/main/java/com/tripagent/controller/TripController.java`

- 添加 `/api/circuit-breaker/status` 端点
- 返回各熔断器状态

---

## 关键文件清单

| 文件 | 操作 |
|------|------|
| `pom.xml` | 添加依赖 |
| `application.yml` | 添加熔断配置 |
| `config/CircuitBreakerConfig.java` | 新建 - 熔断配置类 |
| `agent/planning/PlanningAgent.java` | 修改 - 添加熔断注解 |
| `agent/execution/ExecutionAgent.java` | 修改 - 添加熔断注解 |
| `knowledge/service/EmbeddingService.java` | 修改 - 添加熔断注解 |
| `knowledge/service/RagService.java` | 修改 - 处理降级 |
| `agent/core/ToolRegistry.java` | 修改 - 添加熔断注解 |

---

## 验证方式

1. **单元测试**：模拟异常，验证重试次数
2. **集成测试**：
   - 启动服务，正常请求 → 正常响应
   - 模拟 LLM 不可用 → 返回默认计划
   - 模拟 Embedding 不可用 → 跳过 RAG 直接回答
3. **熔断测试**：
   - 连续发送失败请求 → 熔断器打开
   - 等待熔断恢复 → 半开状态 → 成功恢复
4. **健康检查**：访问 `/api/circuit-breaker/status` 查看熔断器状态
