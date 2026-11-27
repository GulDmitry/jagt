package dev.jagt.orchestrator.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.flow.deploy.DeployStep;
import dev.jagt.orchestrator.flow.session.SessionSign;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.deploy.ChecksUnread;
import dev.jagt.orchestrator.task.deploy.Deployment;
import dev.jagt.orchestrator.task.deploy.InfraRetry;
import dev.jagt.orchestrator.task.plan.PlanGate;
import dev.jagt.orchestrator.task.repo.GitTrust;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import dev.jagt.orchestrator.task.stop.TaskStop;
import dev.jagt.orchestrator.task.turn.AgentReport;
import dev.jagt.orchestrator.task.turn.RoleRecord;
import dev.jagt.orchestrator.task.turn.Turn;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TaskEffectReadsTimeTest {

  static Stream<TaskEffect> everyEffect() {
    return Stream.of(
        new TaskEffect.Lands(TaskStatus.DEPLOYED),
        new TaskEffect.Stops(new TaskStop(TaskStop.Trigger.ROUNDS, 1, "three rounds")),
        new TaskEffect.ClearsStop(),
        new TaskEffect.Lifts(),
        new TaskEffect.Escalates(),
        new TaskEffect.GatesPlan(PlanGate.REQUIRED),
        new TaskEffect.Reports(AgentReport.NO_CHANGES),
        new TaskEffect.OpensTurn("step", "digest", "answered"),
        new TaskEffect.ClosesTurn(),
        new TaskEffect.RestoresTurn(new Turn("step", 1L, "d", 0L, Map.of(), List.of())),
        new TaskEffect.Deploys(
            new DeployStep.Opened(List.of(Deployment.Target.of("proj", "main")))),
        new TaskEffect.Signs(new SessionSign.WorkStarted()),
        new TaskEffect.RestartsSession(),
        new TaskEffect.TrustsGit(new GitTrust(Map.of("a", "1"), null)),
        new TaskEffect.ParksGit("a"),
        new TaskEffect.JoinsGit(new GitTrust(Map.of("b", "2"), null)),
        new TaskEffect.AsksRepo("other"),
        new TaskEffect.DropsRepoRequest(),
        new TaskEffect.AttachesRepo(TaskRepo.of("other", "/other")),
        new TaskEffect.Places(List.of(TaskRepo.of("other", "/other")), "main"),
        new TaskEffect.LeavesRepo("proj"),
        new TaskEffect.LinksRequests(Map.of(), "https://host/r/1", false),
        new TaskEffect.MergesRequest("proj", true),
        new TaskEffect.MergesRequestByHost("proj"),
        new TaskEffect.ResetsChecks(),
        new TaskEffect.ResetsApproval(),
        new TaskEffect.ResetsInfra(),
        new TaskEffect.NotesMaster(new MasterNote.Took()),
        new TaskEffect.OpensCheckerRead("acceptance", "step"),
        new TaskEffect.ClosesCheckerRead("acceptance"),
        new TaskEffect.PassesChecker("acceptance", "abc"),
        new TaskEffect.SendsCheckerRound("acceptance", "digest", "- row"),
        new TaskEffect.DeclinesRound("acceptance"),
        new TaskEffect.SeesHead("tip"),
        new TaskEffect.ReadsChecks("failed", "tip"),
        new TaskEffect.FailsToReadChecks(new ChecksUnread(5, "timeout")),
        new TaskEffect.ReadsApproval(true),
        new TaskEffect.ArmsInfra(InfraRetry.armed(5, "proj", "https://ci/1", "build", "oom", 1, 2)),
        new TaskEffect.ClearsInfra(true),
        new TaskEffect.SendsInfra(
            InfraRetry.armed(5, "proj", "https://ci/1", "build", "oom", 1, 2)),
        new TaskEffect.DisarmsInfra());
  }

  @Test
  void listsEveryEffectKind() {
    Set<Class<?>> listed = everyEffect().map(Object::getClass).collect(Collectors.toSet());

    assertThat(listed).containsExactlyInAnyOrder(TaskEffect.class.getPermittedSubclasses());
  }

  @ParameterizedTest
  @MethodSource("everyEffect")
  void anEffectReadsTimeExactlyWhenTwoClocksGiveTwoResults(TaskEffect effect) {
    TaskState task =
        TaskState.builder("proj", "/wt", TaskStatus.IN_PROGRESS)
            .stop(new TaskStop(TaskStop.Trigger.ROUNDS, 1, "three rounds"))
            .gitTrust(new GitTrust(Map.of("a", "0"), null))
            .roles(Map.of("acceptance", RoleRecord.defaults()))
            .turn(new Turn("step", 1L, "d", 0L, Map.of(), List.of()))
            .deployment(Deployment.defaults())
            .build();

    TaskChart chart = mock(TaskChart.class);
    when(chart.effects(any(), any())).thenReturn(List.of(effect));
    TaskReducer reducer = new TaskReducer(chart, stale -> {}, gap -> {});

    TaskState early = reducer.reduce(task, new TaskMove.Lifted(), null, false, 10L);
    TaskState late = reducer.reduce(task, new TaskMove.Lifted(), null, false, 20L);

    assertThat(!early.equals(late)).isEqualTo(effect.readsTime());
  }
}
