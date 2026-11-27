package dev.jagt.orchestrator;

import dev.jagt.orchestrator.capability.stop.DeployLiftsStop;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.flow.FactSources;
import dev.jagt.orchestrator.flow.FlowEngine;
import dev.jagt.orchestrator.flow.FlowReports;
import dev.jagt.orchestrator.flow.FlowRules;
import dev.jagt.orchestrator.flow.ReducingStore;
import dev.jagt.orchestrator.flow.TaskChart;
import dev.jagt.orchestrator.flow.TaskReducer;
import dev.jagt.orchestrator.flow.capability.Capabilities;
import dev.jagt.orchestrator.flow.capability.CapabilityInterceptor;
import dev.jagt.orchestrator.flow.capability.TaskCapability;
import dev.jagt.orchestrator.port.AgentPresence;
import dev.jagt.orchestrator.port.CurrentOrigin;
import dev.jagt.orchestrator.port.GitDistrust;
import dev.jagt.orchestrator.port.MergedRefresh;
import dev.jagt.orchestrator.port.codehost.RequestlessShip;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.UnansweredFindings;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.stop.TaskStops;
import java.time.Clock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class FlowWiring {

  @Bean
  public TaskChart taskChart(InstalledRoles roles) {
    return new TaskChart(roles.composition());
  }

  @Bean
  public FlowRules flowRules(TaskChart chart) {
    return new FlowRules(chart);
  }

  @Bean
  public Checkers checkers(TaskChart chart) {
    return new Checkers(chart);
  }

  @Bean
  public TaskReducer taskReducer(TaskChart chart) {
    return new TaskReducer(
        chart,
        stale ->
            log.atInfo()
                .setMessage("read dropped stale")
                .addKeyValue("alias", stale.alias())
                .addKeyValue("kind", stale.kind())
                .addKeyValue("from", stale.readAt())
                .addKeyValue("to", stale.current())
                .log());
  }

  @Bean
  public Capabilities capabilities(
      List<TaskCapability> declared, List<CapabilityInterceptor> around) {
    Capabilities capabilities = new Capabilities(declared, around);
    capabilities
        .takeovers()
        .forEach(takeover ->
            log.atInfo()
                .setMessage("capability takes over")
                .addKeyValue("takeover", takeover)
                .log());
    return capabilities;
  }

  @Bean
  public CapabilityInterceptor deployLiftsStop(TaskStops stops) {
    return new DeployLiftsStop(stops);
  }

  @Bean
  public FactSources factSources(
      AgentPresence agents,
      RequestlessShip requestless,
      GitDistrust trust,
      MergedRefresh merged,
      CurrentOrigin origin,
      UnansweredFindings findings,
      ConfigService config) {
    return new FactSources(
        agents,
        requestless,
        trust,
        merged,
        origin,
        findings::awaiting,
        () -> config.load().deploy().requireApprovalOrDefault());
  }

  @Bean
  public FlowEngine flowEngine(
      ReducingStore tasks,
      Capabilities capabilities,
      FactSources sources,
      Clock clock,
      FlowRules rules) {
    return new FlowEngine(tasks, capabilities, sources, clock, rules);
  }

  @Bean
  public FlowReports flowReports(
      ReducingStore tasks, Clock clock, Checkers checkers, FlowRules rules) {
    return new FlowReports(tasks, clock, checkers, rules);
  }
}
