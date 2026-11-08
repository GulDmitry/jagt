package dev.jagt.orchestrator.triageeval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.eval.EvalPorts;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalStalled;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.eval.StopTriageCase;
import dev.jagt.orchestrator.eval.StopTriageCases;
import dev.jagt.orchestrator.port.UserNotifier;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.service.triage.StopTriagePanel;
import dev.jagt.orchestrator.service.triage.StopTriageFailed;
import dev.jagt.orchestrator.task.StopTriage;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.TaskStatus;
import dev.jagt.orchestrator.task.TaskStop;
import dev.jagt.orchestrator.task.TokenUsage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("triageEval")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.startup-checks=false",
      "orchestrator.open-terminal-window=false"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TriageEvalTest {

  private static final double WHO_BAR = 0.8;
  private static final int DEAD_PORT = EvalPorts.free();
  private static final int GATE_PORT = EvalPorts.free();
  private static final AtomicInteger ROUND = new AtomicInteger();
  private static final AtomicInteger ANSWERED = new AtomicInteger();
  private static final AtomicInteger WHO_RIGHT = new AtomicInteger();
  private static final AtomicInteger FAMILY_RIGHT = new AtomicInteger();
  private static final AtomicInteger FALSE_SAFE = new AtomicInteger();
  private static Path root;

  @DynamicPropertySource
  static void orchestratorLivesInAThrowawayRoot(DynamicPropertyRegistry registry)
      throws Exception {
    registry.add("server.port", () -> GATE_PORT);
    registry.add("orchestrator.mcp-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/mcp");
    registry.add(
        "orchestrator.hook-url", () -> "http://127.0.0.1:" + DEAD_PORT + "/api/agent/session");
    registry.add("orchestrator.gate-url", () -> "http://127.0.0.1:" + GATE_PORT + "/api/agent");
    registry.add("orchestrator.assistant.max-cost-usd", () -> "0.10");
    Path build = Path.of("build").toAbsolutePath();
    Files.createDirectories(build);
    root = Files.createTempDirectory(build, "triage-eval-");
    Files.writeString(root.resolve("mcp_client.js"), "// triage eval placeholder proxy\n");
    ShippedRoot.copyInto(root);
    Files.copy(
        Path.of(System.getProperty("triageEval.brief", "../roles/triage/brief.md.dist")),
        root.resolve("roles/triage/brief.md"),
        StandardCopyOption.REPLACE_EXISTING);
    Files.writeString(
        root.resolve("jagt.yml"),
        """
        orchestrator:
          projects:
            proj: { path: "%s", baseBranch: main }
          master: { mode: off }
        """
            .formatted(root.resolve("placeholder")));
    registry.add("orchestrator.root", () -> root.toString());
    registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
    registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
  }

  @MockitoBean private UserNotifier userNotifier;
  @Autowired private StopTriagePanel panel;
  @Autowired private UsageTracker usage;

  @AfterAll
  void holdsWhoLoopedOnMostCases() {
    System.out.printf(
        "triageEval answered=%d whoRight=%d familyRight=%d%n",
        ANSWERED.get(), WHO_RIGHT.get(), FAMILY_RIGHT.get());
    assertThat(FALSE_SAFE.get()).describedAs("runs advising a lift safe that is not").isZero();
    assertThat(ANSWERED.get()).describedAs("runs the reader answered").isPositive();
    assertThat(WHO_RIGHT.get() / (double) ANSWERED.get())
        .describedAs("share of answered runs naming who looped")
        .isGreaterThanOrEqualTo(WHO_BAR);
  }

  @ParameterizedTest
  @MethodSource("cases")
  void readsTheStopAsAHumanWould(StopTriageCase stopped) {
    EvalQuorum.configured().hold(stopped.name(), this::spend, () -> oneRun(stopped));
  }

  private OptionalDouble spend() {
    return OptionalDouble.of(
        usage.sessionByKind().values().stream().mapToDouble(TokenUsage::costUsd).sum());
  }

  private void oneRun(StopTriageCase stopped) throws Exception {
    Path worktree = root.resolve("repos/" + ROUND.incrementAndGet());
    Files.createDirectories(worktree);
    init(worktree);
    TaskState task =
        TaskState.builder("proj", worktree.toString(), TaskStatus.IN_PROGRESS)
            .alias("t" + ROUND.get())
            .build()
            .withStop(new TaskStop(stopped.trigger(), 0L, stopped.evidence()));
    StopTriage read;
    try {
      read =
          panel.read(
              "ABC-" + ROUND.get(),
              task,
              new StopTriagePanel.Trail(stopped.trail(), stopped.commits()),
              System.getProperty("triageEval.model", "haiku"));
    } catch (StopTriageFailed failed) {
      throw EvalStalled.because("no answer: " + failed.getMessage());
    }
    ANSWERED.incrementAndGet();
    if (read.who() == stopped.who()) {
      WHO_RIGHT.incrementAndGet();
    }
    if (read.family().equals(stopped.family())) {
      FAMILY_RIGHT.incrementAndGet();
    }
    if (read.lift() == StopTriage.Lift.SAFE && stopped.lift() == StopTriage.Lift.UNSAFE) {
      FALSE_SAFE.incrementAndGet();
    }
    assertThat(read.lift())
        .describedAs(
            "%s: expected lift %s, read said: %s",
            stopped.name(), stopped.lift().label(), read.line())
        .isEqualTo(stopped.lift());
  }

  private static void init(Path worktree) throws Exception {
    Process process =
        new ProcessBuilder("git", "init", "--initial-branch=main", ".")
            .directory(worktree.toFile())
            .redirectErrorStream(true)
            .start();
    if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
      throw new IllegalStateException(
          "git init failed in " + worktree + ": " + new String(process.getInputStream().readAllBytes()));
    }
  }

  static List<StopTriageCase> cases() {
    return StopTriageCases.all();
  }
}
