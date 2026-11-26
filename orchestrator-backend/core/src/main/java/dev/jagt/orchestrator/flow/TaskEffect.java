package dev.jagt.orchestrator.flow;

import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a chart step does to a task, as data; {@link TaskReducer} is the one place that applies it.
 */
sealed interface TaskEffect {

  /** Whether applying the effect reads the clock; none reading it may be applied without one. */
  default boolean readsTime() {
    return false;
  }

  record Lands(TaskStatus to) implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record Stops(TaskStop stop) implements TaskEffect {}

  record ClearsStop() implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record Lifts() implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record Escalates() implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record GatesPlan(PlanGate gate) implements TaskEffect {}

  record Reports(AgentReport report) implements TaskEffect {}

  record OpensTurn(String step, String digest, String answered) implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record ClosesTurn() implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record RestoresTurn(Turn before) implements TaskEffect {}

  record Deploys(DeployStep step) implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record Signs(SessionSign sign) implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record RestartsSession() implements TaskEffect {}

  record TrustsGit(GitTrust trust) implements TaskEffect {}

  record ParksGit(String changed) implements TaskEffect {}

  record JoinsGit(GitTrust added) implements TaskEffect {}

  record AsksRepo(String project) implements TaskEffect {}

  record DropsRepoRequest() implements TaskEffect {}

  record AttachesRepo(TaskRepo repo) implements TaskEffect {}

  record Places(List<TaskRepo> repos, @Nullable String baseBranch) implements TaskEffect {}

  record LeavesRepo(String project) implements TaskEffect {}

  record LinksRequests(Map<String, String> byProject, String url, boolean newRound)
      implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record MergesRequest(String project, boolean merged) implements TaskEffect {}

  record MergesRequestByHost(String project) implements TaskEffect {}

  record ResetsChecks() implements TaskEffect {}

  record ResetsApproval() implements TaskEffect {}

  record ResetsInfra() implements TaskEffect {}

  record NotesMaster(MasterNote note) implements TaskEffect {}

  record OpensCheckerRead(String role, String step) implements TaskEffect {}

  record ClosesCheckerRead(String role) implements TaskEffect {}

  record PassesChecker(String role, String sha) implements TaskEffect {}

  record SendsCheckerRound(String role, String digest, String rows) implements TaskEffect {}

  record DeclinesRound(String role) implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record SeesHead(String tip) implements TaskEffect {}

  record ReadsChecks(String hostStatus, String tip) implements TaskEffect {}

  record FailsToReadChecks(dev.jagt.orchestrator.task.deploy.ChecksUnread read)
      implements TaskEffect {}

  record ReadsApproval(boolean approved) implements TaskEffect {}

  record ArmsInfra(InfraRetry retry) implements TaskEffect {}

  record ClearsInfra(boolean all) implements TaskEffect {}

  record SendsInfra(InfraRetry retry) implements TaskEffect {
    @Override
    public boolean readsTime() {
      return true;
    }
  }

  record DisarmsInfra() implements TaskEffect {}
}
