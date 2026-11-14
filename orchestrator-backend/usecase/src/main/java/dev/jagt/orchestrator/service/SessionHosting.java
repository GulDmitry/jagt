package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.port.agent.AgentRuntime;
import dev.jagt.orchestrator.port.terminal.SessionHost;
import org.springframework.stereotype.Component;

/** Where an agent session lives, which agent it is, and what a fresh one carries over. */
@Component
public record SessionHosting(SessionHost host, AgentRuntime runtime, CarriedBlock carried) {}
