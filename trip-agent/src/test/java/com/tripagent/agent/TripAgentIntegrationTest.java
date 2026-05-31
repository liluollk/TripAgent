package com.tripagent.agent;

import com.tripagent.agent.core.AgentContext;
import com.tripagent.agent.core.AgentStep;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for TripAgent.
 */
@SpringBootTest
public class TripAgentIntegrationTest {

    @Autowired
    private TripAgent tripAgent;

    @Test
    public void testTripPlanning() throws Exception {
        // Build context
        AgentContext context = AgentContext.builder()
                .userId("test-user")
                .sessionId("test-session")
                .userMessage("Plan a 3-day trip to Tokyo")
                .build();

        // Execute and collect results
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<AgentStep> lastStep = new AtomicReference<>();

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

        // Wait for completion (max 60 seconds)
        assertTrue(latch.await(60, TimeUnit.SECONDS), "Agent execution timed out");

        // Verify we got a result
        assertNotNull(lastStep.get(), "Should have received at least one step");
        assertEquals(AgentStep.StepType.RESULT, lastStep.get().getType(),
                "Last step should be RESULT");
    }
}
