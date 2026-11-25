package dev.jagt.orchestrator.adapter.worktree;

import static dev.jagt.orchestrator.adapter.codehost.TestShopProjectBuilder.shopProject;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.port.reach.ReachLost;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.repo.ProjectConfig;
import dev.jagt.orchestrator.task.session.Session;
import dev.jagt.orchestrator.testing.FakeClock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CredentialChecksTest {

  private final ConfigService configService = mock(ConfigService.class);
  private final Processes processes = mock(Processes.class);
  private final ReachLedger reach = new ReachLedger();
  private final FakeClock clock = new FakeClock();
  private final CredentialChecks checks =
      new CredentialChecks(configService, processes, reach, clock);
  private final TaskState task = TaskState.builder("shop", "/wt", TaskStatus.NEW).build();
  private final ConfigFile declaringRegistryCheck =
      ConfigFile.defaults()
          .withProjects(
              Map.of(
                  "shop",
                  shopProject()
                      .withCredentialChecks(
                          List.of(
                              new ProjectConfig.CredentialCheck(
                                  "npm registry",
                                  List.of("npm", "view", "@abc/lib", "version"),
                                  "npm login --scope=@abc")))
                      .build()));

  @Test
  void headNamesProjectCheckAndRenewalWhereCheckFails() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));

    checks.runDue();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost ->
            assertThat(lost.said()).contains("shop", "npm registry", "npm login --scope=@abc"));
  }

  @Test
  void headClearsOnTheNextRunThatPasses() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList()))
        .thenReturn(new Processes.Result(1, "", ""), new Processes.Result(0, "", ""));
    checks.runDue();

    checks.runDue();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void passingCheckIsReadAgainOnlyAfterOneDay() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "", ""));
    checks.runDue();

    clock.advance(Duration.ofHours(23));
    checks.runDue();

    verify(processes, times(1)).run(any(), any(), anyList());
  }

  @Test
  void unfinishedCheckCountsAsFailing() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenThrow(new IllegalStateException("timeout"));

    checks.runDue();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> assertThat(lost.said()).contains("did not finish"));
  }

  @Test
  void vetRunsTheChecksNowAndNamesTheFailure() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));

    assertThat(checks.vet(task)).contains("npm registry", "npm login --scope=@abc");
  }

  @Test
  void vetFindsNothingWhereTheCredentialWorks() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "", ""));

    assertThat(checks.vet(task)).isNull();
  }

  @Test
  void holdsSessionlessTaskWhileItsProjectCheckFails() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
    checks.runDue();

    assertThat(checks.held(task))
        .isEqualTo(
            "project 'shop': credential check 'npm registry' fails (exit 1);"
                + " renew it: npm login --scope=@abc");
  }

  @Test
  void holdsNothingOnceTheCheckPasses() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList()))
        .thenReturn(new Processes.Result(1, "", ""), new Processes.Result(0, "", ""));
    checks.runDue();
    checks.runDue();

    assertThat(checks.held(task)).isNull();
  }

  @Test
  void passingCheckIsReadAgainOnceOneDayHasPassed() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(0, "", ""));
    checks.runDue();

    clock.advance(Duration.ofDays(1));
    checks.runDue();

    verify(processes, times(2)).run(any(), any(), anyList());
  }

  @Test
  void headClearsWhereTheCheckIsNoLongerDeclared() {
    when(configService.load()).thenReturn(declaringRegistryCheck, ConfigFile.defaults());
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
    checks.runDue();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void holdsNothingForTaskWhoseSessionIsAlreadyLiveWhileItsCheckFails() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
    checks.runDue();
    TaskState running = task.withSession(Session.defaults().withMovedAt(1));

    assertThat(checks.held(running)).isNull();
  }

  @Test
  void holdsNothingForTaskWhereAnotherDriversReachIsLostInItsProject() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    reach.lost(new ReachLost("code-host", "shop/main", List.of(), "signed out"));

    assertThat(checks.held(task)).isNull();
  }

  @Test
  void holdsNothingForTaskOfAnotherProject() {
    when(configService.load()).thenReturn(declaringRegistryCheck);
    when(processes.run(any(), any(), anyList())).thenReturn(new Processes.Result(1, "", ""));
    checks.runDue();
    TaskState other = TaskState.builder("blog", "/wt", TaskStatus.NEW).build();

    assertThat(checks.held(other)).isNull();
  }
}
