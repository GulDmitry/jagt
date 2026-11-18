package dev.jagt.orchestrator.acceptanceeval;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.config.role.RoleDefinition;
import dev.jagt.orchestrator.config.role.RoleOptions;
import dev.jagt.orchestrator.eval.AcceptanceCase;
import dev.jagt.orchestrator.eval.AcceptanceCases;
import dev.jagt.orchestrator.eval.AcceptanceFixtureEnvironment;
import dev.jagt.orchestrator.eval.AcceptanceJudgement;
import dev.jagt.orchestrator.eval.AcceptanceScore;
import dev.jagt.orchestrator.eval.AcceptanceScoreboard;
import dev.jagt.orchestrator.eval.AcceptanceWorkspaceFiles;
import dev.jagt.orchestrator.eval.EvalEndpoints;
import dev.jagt.orchestrator.eval.EvalPorts;
import dev.jagt.orchestrator.eval.EvalQuorum;
import dev.jagt.orchestrator.eval.EvalStalled;
import dev.jagt.orchestrator.eval.ShippedRoot;
import dev.jagt.orchestrator.flow.Checkers;
import dev.jagt.orchestrator.job.checker.CheckerFlows;
import dev.jagt.orchestrator.job.checker.CheckerPrompts;
import dev.jagt.orchestrator.job.checker.wiring.CheckerReaders;
import dev.jagt.orchestrator.port.assistant.CheckerReader;
import dev.jagt.orchestrator.port.channel.UserNotifier;
import dev.jagt.orchestrator.service.role.RoleKit;
import dev.jagt.orchestrator.service.role.RoleKits;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.acceptance.AcceptanceEnding;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstanceFactoryContext;
import org.junit.jupiter.api.extension.TestInstancePreConstructCallback;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Tag("acceptanceEval")
@ExtendWith(AcceptanceVerdictEvalTest.ClaimsItsShard.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = {
      "spring.config.import=",
      "orchestrator.startup-checks=false",
      "orchestrator.terminal=none"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AcceptanceVerdictEvalTest {

  private static final int DEAD_PORT = EvalPorts.free();
  private static final int GATE_PORT = EvalPorts.free();
  private final AtomicInteger runs = new AtomicInteger();
  private static final AcceptanceScoreboard SCOREBOARD = AcceptanceScoreboard.empty();

  static final class ClaimsItsShard implements TestInstancePreConstructCallback {
    @Override
    public void preConstructTestInstance(
        TestInstanceFactoryContext factory, ExtensionContext context) {
      AcceptanceEvalWorkspace.claim(factory.getTestClass());
    }
  }

  @DynamicPropertySource
  static void orchestratorLivesInTheTrustedEvalRoot(DynamicPropertyRegistry registry)
      throws Exception {
    EvalEndpoints.register(registry, GATE_PORT, DEAD_PORT);
    registry.add("orchestrator.assistant.max-cost-usd", () -> "0.10");
    AcceptanceEvalWorkspace.clean();
    Path root = AcceptanceEvalWorkspace.root();
    Files.createDirectories(root);
    Files.writeString(root.resolve("mcp_client.js"), "// acceptance eval placeholder proxy\n");
    ShippedRoot.copyInto(root);
    Files.createDirectories(root.resolve("roles/acceptance"));
    Files.copy(
        Path.of(System.getProperty("acceptanceEval.brief", "../roles/acceptance/brief.md.dist")),
        root.resolve("roles/acceptance/brief.md"),
        StandardCopyOption.REPLACE_EXISTING);
    AcceptanceEvalWorkspace.writeConfig(root.resolve("jagt.yml"), root.resolve("placeholder"));
    registry.add("orchestrator.root", () -> root.toString());
    registry.add("orchestrator.config-file", () -> root.resolve("jagt.yml").toString());
    registry.add("orchestrator.state-file", () -> root.resolve("state.json").toString());
  }

  @MockitoBean private UserNotifier userNotifier;
  private final CheckerReader reader;
  private final CheckerPrompts prompts;
  private final CheckerFlows checkerFlows;
  private final RoleDefinition acceptanceDefinition;
  private final UsageTracker usage;

  @Autowired
  AcceptanceVerdictEvalTest(
      CheckerReader reader, RoleKits kits, CheckerReaders readers, UsageTracker usage) {
    this.reader = reader;
    RoleKit acceptance = kits.find(Checkers.ACCEPTANCE).orElseThrow();
    this.prompts = new CheckerPrompts(acceptance.definition(), acceptance.files());
    this.checkerFlows = readers.flows(acceptance);
    this.acceptanceDefinition = acceptance.definition();
    this.usage = usage;
  }

  @AfterAll
  void reportsWhatTheShardScored() throws Exception {
    System.out.println(SCOREBOARD.summary());
    AcceptanceEvalWorkspace.release();
  }

  @ParameterizedTest
  @MethodSource("cases")
  void judgesTheDeployAsHumanWouldHave(AcceptanceCase deployed) {
    EvalQuorum.configured().hold(deployed.name(), this::acceptanceSpend, () -> oneRun(deployed));
  }

  private OptionalDouble acceptanceSpend() {
    return OptionalDouble.of(
        usage
            .sessionByKind()
            .getOrDefault(AssistantCallKind.ACCEPTANCE_CHECK, TokenUsage.NONE)
            .costUsd());
  }

  private void oneRun(AcceptanceCase deployed) throws Exception {
    long started = System.nanoTime();
    OptionalDouble before = acceptanceSpend();
    AcceptanceJudgement judged;
    String fixtureUrl;
    try (AcceptanceFixtureEnvironment environment =
        AcceptanceFixtureEnvironment.serve(deployed.landing().environment())) {
      fixtureUrl = environment.url();
      judged = judge(deployed, fixtureUrl);
    }
    if (judged.ending() == AcceptanceEnding.FAILED) {
      throw EvalStalled.because("the check failed: " + judged.cited());
    }
    AcceptanceScore score = AcceptanceScore.of(deployed, judged, fixtureUrl);
    SCOREBOARD.record(
        score,
        (System.nanoTime() - started) / 1e9,
        OptionalDouble.of(acceptanceSpend().orElse(0) - before.orElse(0)));
    assertThat(score.passed())
        .describedAs(
            "%s: %s; acceptance said: %s",
            deployed.name(),
            score.describe(),
            judged.cited().substring(0, Math.min(300, judged.cited().length())))
        .isTrue();
  }

  private AcceptanceJudgement judge(AcceptanceCase deployed, String url) throws Exception {
    Path worktree =
        AcceptanceEvalWorkspace.worktreeFor(
            AcceptanceEvalWorkspace.root().resolve("repos/" + runs.incrementAndGet() + "/repo"),
            deployed);
    String sha = AcceptanceEvalWorkspace.head(worktree);
    Path workspace = AcceptanceEvalWorkspace.root().resolve("repos/" + runs.get() + "/workspace");
    if (deployed.workspace().held()) {
      AcceptanceWorkspaceFiles.seed(workspace, deployed);
    }
    Path watched = deployed.workspace().held() ? workspace : worktree;
    Set<String> before = AcceptanceWorkspaceFiles.snapshot(watched);
    String landedProbe =
        deployed.expect().probe().isEmpty()
            ? ""
            : url + deployed.expect().probe().substring(deployed.expect().probe().indexOf(' ') + 1);
    RoleOptions.Flows scripted = flows(deployed);
    AcceptanceEvalWorkspace.writeRole(url, landedProbe, scripted);
    RoleOptions acceptance =
        new RoleOptions(
            true,
            System.getProperty("acceptanceEval.model", acceptanceDefinition.model()),
            List.of(new RoleOptions.Environment(url, landedProbe)),
            scripted,
            List.of(),
            deployed.workspace().held() ? workspace.toString() : "",
            false,
            List.of());
    CheckerReader.Run run =
        deployed.declines()
            ? prompts.decline(
                deployed.ticket().key(),
                worktree.toString(),
                sha,
                acceptance,
                "- "
                    + deployed.ticket().criteria().getFirst().id()
                    + " — not met: "
                    + Objects.requireNonNull(deployed.decline()).evidence(),
                deployed.decline().why())
            : prompts.accept(
                deployed.ticket().key(),
                worktree.toString(),
                sha,
                acceptance,
                AcceptanceVerdict.ACCEPT);
    CheckerReader.Run told =
        new CheckerReader.Run(
            run.shared(),
            run.prompt() + AcceptanceEvalWorkspace.ticket(deployed, url, sha),
            run.worktree(),
            run.sha(),
            run.model(),
            run.reach(),
            run.schema(),
            run.role());
    if (deployed.declines()) {
      return AcceptanceJudgement.ofDecline(
          deployed,
          reader
              .reconsider(told)
              .facts()
              .orElseThrow(() -> EvalStalled.because("no decline answer")));
    }
    AcceptanceVerdict read =
        reader.read(told).facts().orElseThrow(() -> EvalStalled.because("no verdict"));
    AcceptanceJudgement judged =
        AcceptanceJudgement.ofVerdict(deployed, checkerFlows.run(read, worktree, read.flows()));
    assertThat(AcceptanceWorkspaceFiles.problem(deployed, before, watched, url))
        .describedAs(
            "%s: the workspace; acceptance said: %s",
            deployed.name(), judged.cited().substring(0, Math.min(400, judged.cited().length())))
        .isEmpty();
    return judged;
  }

  private RoleOptions.Flows flows(AcceptanceCase deployed) throws IOException {
    if (deployed.workspace().held()) {
      return RoleOptions.Flows.defaults().withCommand(AcceptanceWorkspaceFiles.FLOWS_COMMAND);
    }
    if (!deployed.flows().given()) {
      return RoleOptions.Flows.defaults();
    }
    if (!deployed.flows().path().isEmpty()) {
      return RoleOptions.Flows.defaults()
          .withDriver("playwright")
          .withPath(deployed.flows().path())
          .withCommand(deployed.flows().command());
    }
    Path directory = AcceptanceEvalWorkspace.root().resolve("repos/" + runs.get() + "/flows");
    Files.createDirectories(directory);
    for (Map.Entry<String, String> file : deployed.flows().files().entrySet()) {
      Files.writeString(directory.resolve(file.getKey()), file.getValue());
    }
    return RoleOptions.Flows.defaults().withDriver("playwright").withPath(directory.toString());
  }

  static List<AcceptanceCase> cases() {
    List<AcceptanceCase> all = AcceptanceCases.all();
    return IntStream.range(0, all.size())
        .filter(
            index -> index % AcceptanceEvalWorkspace.shards() == AcceptanceEvalWorkspace.shard())
        .mapToObj(all::get)
        .toList();
  }
}
