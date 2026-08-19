package dev.jagt.orchestrator;

import dev.jagt.orchestrator.flow.Capabilities;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.CapabilityInterceptor;
import dev.jagt.orchestrator.port.TaskCapability;
import dev.jagt.orchestrator.port.TaskStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Assembled here rather than annotated, so nothing in it depends on the framework that runs it: what is wired
 * below compiles against records and ports alone, and its tests need no container.
 */
@Configuration
@Slf4j
public class FlowWiring {

    @Bean
    public Capabilities capabilities(List<TaskCapability> declared, List<CapabilityInterceptor> around) {
        Capabilities capabilities = new Capabilities(declared, around);
        capabilities.takeovers().forEach(log::info);
        return capabilities;
    }

    @Bean
    public FlowEngine flowEngine(TaskStore tasks, Capabilities capabilities, AgentPresence agents) {
        return new FlowEngine(tasks, capabilities, agents);
    }

    @Bean
    public FlowReports flowReports(TaskStore tasks) {
        return new FlowReports(tasks);
    }
}
