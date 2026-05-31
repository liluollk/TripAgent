package com.tripagent.agent;

import com.tripagent.agent.core.AgentContext;
import com.tripagent.agent.core.AgentStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("TripAgent 集成测试")
class TripAgentIntegrationTest {

    @Autowired
    private TripAgent tripAgent;

    @Nested
    @DisplayName("execute")
    class Execute {

        @Test
        @DisplayName("基本执行流程")
        void basicExecution() throws Exception {
            // Arrange
            AgentContext context = AgentContext.builder()
                    .userId("test-user")
                    .sessionId("test-session")
                    .userMessage("你好")
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AgentStep> lastStep = new AtomicReference<>();

            // Act
            tripAgent.execute(context)
                    .doOnNext(step -> {
                        System.out.println("Step: " + step.getType() + " - " + step.getContent());
                        lastStep.set(step);
                    })
                    .doOnComplete(latch::countDown)
                    .doOnError(e -> {
                        e.printStackTrace();
                        latch.countDown();
                    })
                    .subscribe();

            // Assert
            assertTrue(latch.await(60, TimeUnit.SECONDS), "执行超时");
            assertNotNull(lastStep.get(), "应该收到至少一个步骤");
        }

        @Test
        @DisplayName("中文消息处理")
        void chineseMessage() throws Exception {
            // Arrange
            AgentContext context = AgentContext.builder()
                    .userId("test-user")
                    .sessionId("test-session-chinese")
                    .userMessage("我想去南京玩3天，帮我规划一下")
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AgentStep> lastStep = new AtomicReference<>();

            // Act
            tripAgent.execute(context)
                    .doOnNext(step -> {
                        System.out.println("Step: " + step.getType() + " - " + step.getContent());
                        lastStep.set(step);
                    })
                    .doOnComplete(latch::countDown)
                    .doOnError(e -> {
                        e.printStackTrace();
                        latch.countDown();
                    })
                    .subscribe();

            // Assert
            assertTrue(latch.await(60, TimeUnit.SECONDS), "执行超时");
            assertNotNull(lastStep.get(), "应该收到至少一个步骤");
        }

        @Test
        @DisplayName("空消息处理")
        void emptyMessage() throws Exception {
            // Arrange
            AgentContext context = AgentContext.builder()
                    .userId("test-user")
                    .sessionId("test-session-empty")
                    .userMessage("")
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<AgentStep> lastStep = new AtomicReference<>();
            AtomicReference<Throwable> error = new AtomicReference<>();

            // Act
            tripAgent.execute(context)
                    .doOnNext(step -> {
                        System.out.println("Step: " + step.getType() + " - " + step.getContent());
                        lastStep.set(step);
                    })
                    .doOnComplete(latch::countDown)
                    .doOnError(e -> {
                        error.set(e);
                        latch.countDown();
                    })
                    .subscribe();

            // Assert
            assertTrue(latch.await(60, TimeUnit.SECONDS), "执行超时");
            // 空消息应该能正常处理，不应抛出异常
            assertNull(error.get(), "空消息不应抛出异常");
        }
    }

    @Nested
    @DisplayName("并发测试")
    class Concurrency {

        @Test
        @DisplayName("多用户并发执行")
        void concurrentExecution() throws Exception {
            // Arrange
            int userCount = 3;
            CountDownLatch latch = new CountDownLatch(userCount);
            AtomicReference<Throwable>[] errors = new AtomicReference[userCount];

            // Act
            for (int i = 0; i < userCount; i++) {
                final int userId = i;
                errors[userId] = new AtomicReference<>();

                AgentContext context = AgentContext.builder()
                        .userId("user-" + userId)
                        .sessionId("session-" + userId)
                        .userMessage("用户" + userId + "的消息")
                        .build();

                tripAgent.execute(context)
                        .doOnError(e -> errors[userId].set(e))
                        .doOnComplete(latch::countDown)
                        .subscribe();
            }

            // Assert
            assertTrue(latch.await(120, TimeUnit.SECONDS), "并发执行超时");
            for (int i = 0; i < userCount; i++) {
                assertNull(errors[i].get(), "用户" + i + "执行出错");
            }
        }
    }
}
