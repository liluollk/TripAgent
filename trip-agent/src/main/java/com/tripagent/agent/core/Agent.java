package com.tripagent.agent.core;

import reactor.core.publisher.Flux;

/**
 * Base interface for all agents.
 * Agents are autonomous units that can reason and act.
 */
public interface Agent {

    /**
     * Execute the agent with given context.
     *
     * @param context The execution context
     * @return Flux of AgentStep representing the reasoning and acting process
     */
    Flux<AgentStep> execute(AgentContext context);

    /**
     * Get the agent name for logging and identification.
     *
     * @return Agent name
     */
    String getName();
}
