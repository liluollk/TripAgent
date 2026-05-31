package com.tripagent.agent.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentStep 测试")
class AgentStepTest {

    @Nested
    @DisplayName("Builder")
    class Builder {

        @Test
        @DisplayName("构建 THINK 步骤")
        void buildThinkStep() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.THINK)
                    .content("分析用户需求")
                    .build();

            assertEquals(AgentStep.StepType.THINK, step.getType());
            assertEquals("分析用户需求", step.getContent());
            assertNull(step.getToolName());
            assertNull(step.getToolInput());
            assertNull(step.getToolOutput());
        }

        @Test
        @DisplayName("构建 ACT 步骤")
        void buildActStep() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.ACT)
                    .content("调用天气工具")
                    .toolName("getWeather")
                    .toolInput("{\"city\": \"南京\"}")
                    .build();

            assertEquals(AgentStep.StepType.ACT, step.getType());
            assertEquals("调用天气工具", step.getContent());
            assertEquals("getWeather", step.getToolName());
            assertEquals("{\"city\": \"南京\"}", step.getToolInput());
            assertNull(step.getToolOutput());
        }

        @Test
        @DisplayName("构建 OBSERVE 步骤")
        void buildObserveStep() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.OBSERVE)
                    .content("天气查询结果")
                    .toolOutput("{\"temp\": \"25°C\", \"weather\": \"晴\"}")
                    .build();

            assertEquals(AgentStep.StepType.OBSERVE, step.getType());
            assertEquals("天气查询结果", step.getContent());
            assertNull(step.getToolName());
            assertNull(step.getToolInput());
            assertEquals("{\"temp\": \"25°C\", \"weather\": \"晴\"}", step.getToolOutput());
        }

        @Test
        @DisplayName("构建 RESULT 步骤")
        void buildResultStep() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.RESULT)
                    .content("旅行规划完成")
                    .build();

            assertEquals(AgentStep.StepType.RESULT, step.getType());
            assertEquals("旅行规划完成", step.getContent());
            assertNull(step.getToolName());
            assertNull(step.getToolInput());
            assertNull(step.getToolOutput());
        }

        @Test
        @DisplayName("构建 ERROR 步骤")
        void buildErrorStep() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.ERROR)
                    .content("工具调用失败")
                    .build();

            assertEquals(AgentStep.StepType.ERROR, step.getType());
            assertEquals("工具调用失败", step.getContent());
            assertNull(step.getToolName());
            assertNull(step.getToolInput());
            assertNull(step.getToolOutput());
        }
    }

    @Nested
    @DisplayName("StepType 枚举")
    class StepType {

        @Test
        @DisplayName("所有步骤类型都存在")
        void allStepTypesExist() {
            AgentStep.StepType[] types = AgentStep.StepType.values();

            assertEquals(5, types.length);
            assertNotNull(AgentStep.StepType.THINK);
            assertNotNull(AgentStep.StepType.ACT);
            assertNotNull(AgentStep.StepType.OBSERVE);
            assertNotNull(AgentStep.StepType.RESULT);
            assertNotNull(AgentStep.StepType.ERROR);
        }

        @Test
        @DisplayName("valueOf 正常工作")
        void valueOfWorks() {
            assertEquals(AgentStep.StepType.THINK, AgentStep.StepType.valueOf("THINK"));
            assertEquals(AgentStep.StepType.ACT, AgentStep.StepType.valueOf("ACT"));
            assertEquals(AgentStep.StepType.OBSERVE, AgentStep.StepType.valueOf("OBSERVE"));
            assertEquals(AgentStep.StepType.RESULT, AgentStep.StepType.valueOf("RESULT"));
            assertEquals(AgentStep.StepType.ERROR, AgentStep.StepType.valueOf("ERROR"));
        }

        @Test
        @DisplayName("valueOf 无效值抛出异常")
        void valueOfInvalidThrows() {
            assertThrows(IllegalArgumentException.class, () -> {
                AgentStep.StepType.valueOf("INVALID");
            });
        }
    }

    @Nested
    @DisplayName("Data 注解")
    class DataAnnotation {

        @Test
        @DisplayName("getter 和 setter 正常工作")
        void gettersAndSetters() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.THINK)
                    .content("初始内容")
                    .build();

            // 测试 setter
            step.setType(AgentStep.StepType.ACT);
            step.setContent("新内容");
            step.setToolName("testTool");
            step.setToolInput("{\"test\": true}");
            step.setToolOutput("{\"result\": \"ok\"}");

            // 测试 getter
            assertEquals(AgentStep.StepType.ACT, step.getType());
            assertEquals("新内容", step.getContent());
            assertEquals("testTool", step.getToolName());
            assertEquals("{\"test\": true}", step.getToolInput());
            assertEquals("{\"result\": \"ok\"}", step.getToolOutput());
        }

        @Test
        @DisplayName("equals 和 hashCode 正常工作")
        void equalsAndHashCode() {
            AgentStep step1 = AgentStep.builder()
                    .type(AgentStep.StepType.THINK)
                    .content("内容")
                    .build();

            AgentStep step2 = AgentStep.builder()
                    .type(AgentStep.StepType.THINK)
                    .content("内容")
                    .build();

            AgentStep step3 = AgentStep.builder()
                    .type(AgentStep.StepType.ACT)
                    .content("内容")
                    .build();

            assertEquals(step1, step2);
            assertNotEquals(step1, step3);
            assertEquals(step1.hashCode(), step2.hashCode());
        }

        @Test
        @DisplayName("toString 正常工作")
        void toStringWorks() {
            AgentStep step = AgentStep.builder()
                    .type(AgentStep.StepType.THINK)
                    .content("分析需求")
                    .build();

            String str = step.toString();

            assertTrue(str.contains("THINK"));
            assertTrue(str.contains("分析需求"));
        }
    }
}
