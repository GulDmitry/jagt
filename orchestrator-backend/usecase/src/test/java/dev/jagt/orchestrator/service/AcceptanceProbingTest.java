package dev.jagt.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.task.plan.Probe;
import dev.jagt.orchestrator.task.repo.Remote;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceProbingTest {

  private final Processes processes = mock(Processes.class);
  private final AcceptanceProbing probing =
      new AcceptanceProbing(processes, new ToolGate(List.of()), 8290);
  private final List<Probe> gradleTest =
      List.of(Probe.specified(Probe.Kind.COMMAND, List.of("./gradlew", "test"), 0));

  @Test
  void passesCommandEndingWithTheExitItsPlanExpects(@TempDir Path worktree) {
    when(processes.run(eq(worktree), any(), anyMap(), eq(List.of("./scripts/finds-nothing"))))
        .thenReturn(new Processes.Result(1, "", ""));

    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(
                    Probe.specified(Probe.Kind.COMMAND, List.of("./scripts/finds-nothing"), 1)));

    assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(true);
  }

  @Test
  void failsCommandEndingWithAnotherExitCarryingTheTailOfItsOutput(@TempDir Path worktree) {
    when(processes.run(eq(worktree), any(), anyMap(), eq(List.of("./gradlew", "test"))))
        .thenReturn(
            new Processes.Result(1, "line 1\n" + "noise\n".repeat(30) + "BUILD FAILED", ""));

    List<Probe> probed = probing.probed("ABC-42", worktree, Remote.ORIGIN, gradleTest);

    assertThat(probed)
        .singleElement()
        .satisfies(probe -> {
          assertThat(probe.failed()).isTrue();
          assertThat(probe.excerpt())
              .startsWith("exit 1")
              .endsWith("BUILD FAILED")
              .doesNotContain("line 1");
        });
  }

  @Test
  void neverRunsCommandTheSessionsOwnGuardRefuses(@TempDir Path worktree) {
    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(
                    Probe.specified(
                        Probe.Kind.COMMAND, List.of("git", "push", "origin", "main"), 0)));

    assertThat(probed)
        .singleElement()
        .satisfies(probe -> {
          assertThat(probe.failed()).isTrue();
          assertThat(probe.excerpt()).contains("jagt refuses");
        });
    verifyNoInteractions(processes);
  }

  @Test
  void runsCommandUnderTheWorktreesOwnGitHooks(@TempDir Path worktree) throws IOException {
    Files.createDirectories(worktree.resolve(".jagt/hooks"));
    when(processes.run(any(), any(), anyMap(), any())).thenReturn(new Processes.Result(0, "", ""));

    new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
        .probed(
            "ABC-42",
            worktree,
            Remote.ORIGIN,
            List.of(Probe.specified(Probe.Kind.COMMAND, List.of("./gradlew", "test"), 0)));

    verify(processes)
        .run(
            eq(worktree),
            any(),
            eq(
                Map.of(
                    "GIT_CONFIG_COUNT",
                    "1",
                    "GIT_CONFIG_KEY_0",
                    "core.hooksPath",
                    "GIT_CONFIG_VALUE_0",
                    worktree.resolve(".jagt/hooks").toString())),
            eq(List.of("./gradlew", "test")));
  }

  @Test
  void failsCommandThatRanPastItsTimeLimit(@TempDir Path worktree) {
    when(processes.run(any(), any(), anyMap(), any()))
        .thenThrow(new IllegalStateException("Command timed out after PT5M: ./gradlew test"));

    List<Probe> probed = probing.probed("ABC-42", worktree, Remote.ORIGIN, gradleTest);

    assertThat(probed)
        .singleElement()
        .satisfies(probe -> {
          assertThat(probe.failed()).isTrue();
          assertThat(probe.excerpt()).contains("timed out");
        });
  }

  @Test
  void asksGitWhetherRefExists(@TempDir Path worktree) {
    when(processes.run(
            eq(worktree),
            any(),
            anyMap(),
            eq(List.of("git", "rev-parse", "--verify", "--quiet", "refs/tags/v1"))))
        .thenReturn(new Processes.Result(0, "", ""));

    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(Probe.specified(Probe.Kind.REF, List.of("refs/tags/v1"), 0)));

    assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(true);
  }

  @Test
  void findsFileInTheWorktree(@TempDir Path worktree) throws IOException {
    Files.createDirectories(worktree.resolve("docs"));
    Files.writeString(worktree.resolve("docs/guide.md"), "x");

    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0)));

    assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(true);
    assertThat(probed).singleElement().extracting(Probe::excerpt).isEqualTo("");
  }

  @Test
  void keepsOnlyTheLastTwoThousandCharactersOfAnOutput(@TempDir Path worktree) {
    when(processes.run(eq(worktree), any(), anyMap(), eq(List.of("./gradlew", "test"))))
        .thenReturn(new Processes.Result(1, "a" + "b".repeat(2_000), ""));

    List<Probe> probed = probing.probed("ABC-42", worktree, Remote.ORIGIN, gradleTest);

    assertThat(probed)
        .singleElement()
        .extracting(Probe::excerpt)
        .isEqualTo("exit 1\n" + "b".repeat(2_000));
  }

  @Test
  void addsNoOutputToTheExitOfCommandThatPrintedNothing(@TempDir Path worktree) {
    when(processes.run(eq(worktree), any(), anyMap(), eq(List.of("./gradlew", "test"))))
        .thenReturn(new Processes.Result(1, "", ""));

    List<Probe> probed = probing.probed("ABC-42", worktree, Remote.ORIGIN, gradleTest);

    assertThat(probed).singleElement().extracting(Probe::excerpt).isEqualTo("exit 1");
  }

  @Test
  void failsFileTheWorktreeDoesNotHold(@TempDir Path worktree) {
    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(Probe.specified(Probe.Kind.ARTIFACT, List.of("docs/guide.md"), 0)));

    assertThat(probed).singleElement().extracting(Probe::excerpt).isEqualTo("no such file");
  }

  @Test
  void failsLinkLeadingOutOfTheWorktree(@TempDir Path root) throws IOException {
    Path worktree = Files.createDirectories(root.resolve("wt"));
    Files.writeString(root.resolve("outside.md"), "x");
    Files.createSymbolicLink(worktree.resolve("inside.md"), root.resolve("outside.md"));

    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(Probe.specified(Probe.Kind.ARTIFACT, List.of("inside.md"), 0)));

    assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(false);
    assertThat(probed)
        .singleElement()
        .extracting(Probe::excerpt)
        .isEqualTo("it resolves outside the worktree");
  }

  @Test
  void failsFileThatCannotBeResolvedSayingJagtCouldNotReadIt(@TempDir Path worktree)
      throws IOException {
    Files.createSymbolicLink(worktree.resolve("loop.md"), worktree.resolve("loop.md"));

    List<Probe> probed =
        new AcceptanceProbing(processes, new ToolGate(List.of()), 8290)
            .probed(
                "ABC-42",
                worktree,
                Remote.ORIGIN,
                List.of(Probe.specified(Probe.Kind.ARTIFACT, List.of("loop.md"), 0)));

    assertThat(probed).singleElement().extracting(Probe::passed).isEqualTo(false);
    assertThat(probed)
        .singleElement()
        .extracting(Probe::excerpt)
        .asString()
        .startsWith("jagt could not read it: ");
  }
}
