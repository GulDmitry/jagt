package dev.jagt.orchestrator.task.plan;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.stop.RefusalKind;
import dev.jagt.orchestrator.task.usage.AgentSpend;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * What jagt holds back and meters on the task: the plan gate, the probes, the refusals, the spend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Holds(
    PlanGate planGate,
    // What the last plan submitted declared, each with its last result once VERIFYING ran it.
    @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Probe> probes,
    // Switch name to the session's "yes — why" or "no — why"; empty until it judged one.
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> switches,
    // Null until first recorded.
    @Nullable GitTrust gitTrust,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<RefusalKind, Integer> refusals,
    // Null until the first metered call.
    @Nullable TokenUsage usage,
    // What this task's own agent sessions have burned, and how far their log has been read.
    @Nullable AgentSpend agentSpend,
    // A plan of this task was approved: the next plan waiting is a later one.
    @JsonInclude(JsonInclude.Include.NON_DEFAULT) @JsonSetter(nulls = Nulls.AS_EMPTY)
        boolean planApproved) {

  public Holds {
    planGate = planGate == null ? PlanGate.OPEN : planGate;
    probes = probes == null ? List.of() : List.copyOf(probes);
    switches = switches == null ? Map.of() : Map.copyOf(switches);
    refusals = refusals == null ? Map.of() : Map.copyOf(refusals);
  }

  public TokenUsage usageOrNone() {
    return usage == null ? TokenUsage.NONE : usage;
  }

  public AgentSpend agentSpendOrNone() {
    return agentSpend == null ? AgentSpend.NONE : agentSpend;
  }

  /** Every token this task has cost, jagt's own reads and its agent's session alike. */
  public TokenUsage totalUsage() {
    return usageOrNone().plus(agentSpendOrNone().usageOrNone());
  }

  public boolean gitStateChanged() {
    return gitTrust != null && gitTrust.changed() != null;
  }

  public Holds refused(RefusalKind kind) {
    Map<RefusalKind, Integer> counted = new EnumMap<>(RefusalKind.class);
    counted.putAll(refusals);
    counted.merge(kind, 1, Integer::sum);
    return new Holds(
        planGate, probes, switches, gitTrust, counted, usage, agentSpend, planApproved);
  }

  /**
   * What the session judged, merged over what it judged before: the last word on a switch stands.
   */
  public Holds judged(Map<String, String> judged) {
    if (judged.isEmpty()) {
      return this;
    }
    Map<String, String> merged = new TreeMap<>(switches);
    merged.putAll(judged);
    return new Holds(planGate, probes, merged, gitTrust, refusals, usage, agentSpend, planApproved);
  }

  public Holds withPlanGate(PlanGate changed) {
    return new Holds(
        changed, probes, switches, gitTrust, refusals, usage, agentSpend, planApproved);
  }

  public Holds withPlanApproved(boolean approved) {
    return new Holds(planGate, probes, switches, gitTrust, refusals, usage, agentSpend, approved);
  }

  public Holds withProbes(List<Probe> changed) {
    return new Holds(
        planGate, changed, switches, gitTrust, refusals, usage, agentSpend, planApproved);
  }

  public Holds withGitTrust(GitTrust trust) {
    return new Holds(planGate, probes, switches, trust, refusals, usage, agentSpend, planApproved);
  }

  public Holds withUsageAdded(TokenUsage added) {
    return new Holds(
        planGate,
        probes,
        switches,
        gitTrust,
        refusals,
        usageOrNone().plus(added),
        agentSpend,
        planApproved);
  }

  public Holds withAgentSpend(AgentSpend spend) {
    return new Holds(planGate, probes, switches, gitTrust, refusals, usage, spend, planApproved);
  }
}
