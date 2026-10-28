package dev.jagt.orchestrator;

import dev.jagt.orchestrator.capability.ship.MergedGuard;
import dev.jagt.orchestrator.capability.trust.GitStateGuard;
import dev.jagt.orchestrator.flow.Capabilities;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.flow.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.TaskAction;
import dev.jagt.orchestrator.flow.TaskCapability;
import dev.jagt.orchestrator.port.TaskStore;
import dev.jagt.orchestrator.capability.StopGuard;
import dev.jagt.orchestrator.service.MergedRequests;
import dev.jagt.orchestrator.service.TaskStops;
import dev.jagt.orchestrator.service.TrustedGitState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;

@Configuration
@Slf4j
public class FlowWiring {

    @Bean
    public Capabilities capabilities(List<TaskCapability> declared, List<CapabilityInterceptor> around) {
        Capabilities capabilities = new Capabilities(declared, around);
        capabilities.takeovers().forEach(takeover -> log.atInfo().setMessage("capability takes over")
                .addKeyValue("takeover", takeover).log());
        return capabilities;
    }

    @Bean
    public CapabilityInterceptor shipGitGuard(TrustedGitState trusted) {
        return new GitStateGuard(TaskAction.SHIP, trusted);
    }

    @Bean
    public CapabilityInterceptor shipMerged(MergedRequests merged) {
        return new MergedGuard(merged);
    }

    @Bean
    public CapabilityInterceptor deployGitGuard(TrustedGitState trusted) {
        return new GitStateGuard(TaskAction.DEPLOY, trusted);
    }

    @Bean
    public CapabilityInterceptor revertGitGuard(TrustedGitState trusted) {
        return new GitStateGuard(TaskAction.REVERT, trusted);
    }

    @Bean
    public CapabilityInterceptor shipStop(TaskStops stops) {
        return new StopGuard(TaskAction.SHIP, stops);
    }

    @Bean
    public CapabilityInterceptor deployStop(TaskStops stops) {
        return new StopGuard(TaskAction.DEPLOY, stops);
    }

    @Bean
    public FlowEngine flowEngine(TaskStore tasks, Capabilities capabilities, AgentPresence agents, Clock clock) {
        return new FlowEngine(tasks, capabilities, agents, clock);
    }

    @Bean
    public FlowReports flowReports(TaskStore tasks, Clock clock) {
        return new FlowReports(tasks, clock);
    }
}
