package dev.jagt.orchestrator.adapter.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.AcceptanceRunner.FlowResult;
import dev.jagt.orchestrator.port.AcceptanceRunner.Verdict;
import dev.jagt.orchestrator.port.Processes;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class PlaywrightRunnerTest {

  private static final String STAGE = "https://alpha.test";

  private static FlowsProperties configured(String command) {
    return new FlowsProperties(
        "/flows", List.of(command), List.of("https://alpha.test/"), Duration.ofMinutes(2));
  }

  @Test
  void environmentOffTheAllowListIsRefusedBeforeAnyRun(@TempDir Path worktree) {
    Processes processes = mock(Processes.class);

    List<FlowResult> results =
        new PlaywrightRunner(processes, configured("npx"))
            .run(worktree, List.of("AC-1"), "https://prod.test");

    assertThat(results).singleElement().satisfies(r -> {
      assertThat(r.verdict()).isEqualTo(Verdict.UNPROVABLE);
      assertThat(r.reason()).isEqualTo("the environment is not on the allow-list");
    });
    verify(processes, never()).run(any(), any(), anyMap(), anyList());
  }

  @Test
  void flowsAreUnprovableWhenNoCommandIsConfigured(@TempDir Path worktree) {
    Processes processes = mock(Processes.class);
    FlowsProperties none =
        new FlowsProperties("/flows", List.of(), List.of(STAGE), Duration.ofMinutes(1));

    List<FlowResult> results =
        new PlaywrightRunner(processes, none).run(worktree, List.of("AC-1"), STAGE);

    assertThat(results.get(0).verdict()).isEqualTo(Verdict.UNPROVABLE);
    verify(processes, never()).run(any(), any(), anyMap(), anyList());
  }

  @Test
  void flowsAreUnprovableNotFailedWhenTheCommandCannotStart(@TempDir Path worktree) {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyMap(), anyList()))
        .thenThrow(new IllegalStateException("Failed to start command", new IOException("missing")));

    List<FlowResult> results =
        new PlaywrightRunner(processes, configured("no-such-tool"))
            .run(worktree, List.of("AC-1", "AC-2"), STAGE);

    assertThat(results).extracting(FlowResult::verdict)
        .containsExactly(Verdict.UNPROVABLE, Verdict.UNPROVABLE);
  }

  @Test
  void flowsAreUnprovableWhenTheCommandPrintsNoJsonReport(@TempDir Path worktree) {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyMap(), anyList()))
        .thenReturn(new Processes.Result(1, "", "browser missing"));

    List<FlowResult> results =
        new PlaywrightRunner(processes, configured("npx")).run(worktree, List.of("AC-1"), STAGE);

    assertThat(results.get(0).verdict()).isEqualTo(Verdict.UNPROVABLE);
    assertThat(results.get(0).reason()).isEqualTo("no JSON report (exit 1)");
  }

  @Test
  void runsTheConfiguredCommandInTheFlowsRepositoryAndKeepsTheReportInTheWorktree(
      @TempDir Path worktree) throws IOException {
    String json =
        "{\"suites\":[{\"specs\":[{\"title\":\"alpha\",\"tags\":[\"AC-1\"],"
            + "\"tests\":[{\"status\":\"expected\",\"results\":[]}]}]}]}";
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyMap(), anyList()))
        .thenReturn(new Processes.Result(0, json, ""));

    List<FlowResult> results =
        new PlaywrightRunner(processes, configured("npx")).run(worktree, List.of("AC-1"), STAGE);

    ArgumentCaptor<List<String>> argv = ArgumentCaptor.captor();
    verify(processes)
        .run(
            eq(Path.of("/flows")),
            eq(Duration.ofMinutes(2)),
            eq(Map.of("BASE_URL", STAGE)),
            argv.capture());
    assertThat(argv.getValue())
        .containsSequence(
            "--reporter=json",
            "--output=" + worktree.resolve("qa/artifacts"),
            "--grep",
            "AC-1");
    assertThat(results.get(0).verdict()).isEqualTo(Verdict.PASSED);
    assertThat(Files.readString(worktree.resolve("qa/report.json"))).isEqualTo(json);
  }

  @Test
  void flowIdsAreEscapedIntoOneAlternationForTheGrep(@TempDir Path worktree) {
    Processes processes = mock(Processes.class);
    when(processes.run(any(), any(), anyMap(), anyList()))
        .thenReturn(new Processes.Result(0, "{\"suites\":[]}", ""));

    new PlaywrightRunner(processes, configured("npx"))
        .run(worktree, List.of("a.b", "c(d)"), STAGE);

    ArgumentCaptor<List<String>> argv = ArgumentCaptor.captor();
    verify(processes).run(any(), any(), anyMap(), argv.capture());
    assertThat(argv.getValue()).last().isEqualTo("a\\.b|c\\(d\\)");
  }

  @Test
  void noFlowsRunNothing(@TempDir Path worktree) {
    Processes processes = mock(Processes.class);

    List<FlowResult> results =
        new PlaywrightRunner(processes, configured("npx")).run(worktree, List.of(), STAGE);

    assertThat(results).isEmpty();
  }
}
