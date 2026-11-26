package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestCheckerRunBuilder.checkerRun;
import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static dev.jagt.orchestrator.adapter.assistant.TestOpenCodeGateBuilder.openCodeGate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.adapter.agent.opencode.OpenCodeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.config.role.RoleDefinitions;
import dev.jagt.orchestrator.port.agent.McpHealth;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.gate.ReadScopes;
import dev.jagt.orchestrator.service.role.InstalledRoles;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.acceptance.AcceptanceVerdict;
import dev.jagt.orchestrator.testing.FakeHost;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChosenCheckerReaderWiringTest {

  static Stream<Arguments> readers() {
    return Stream.of(
        Arguments.of(
            OpenCodeProperties.defaults(),
            "{\"structured_output\":{\"failure\":\"\",\"rows\":[{\"criterion\":\"page"
                + " opens\",\"status\":\"met\",\"evidence\":\"GET answered 200\"}]}}"),
        Arguments.of(
            new OpenCodeProperties(
                "opencode", "http://127.0.0.1:8080/v1", "qwen-4b", Map.of("acceptance", "")),
            "{\"type\":\"text\",\"part\":{\"text\":\"```json\\n"
                + "{\\\"failure\\\":\\\"\\\",\\\"rows\\\":[{\\\"criterion\\\":\\\"page"
                + " opens\\\",\\\"status\\\":\\\"met\\\",\\\"evidence\\\":\\\"GET answered"
                + " 200\\\"}]}\\n```\"}}"));
  }

  @ParameterizedTest
  @MethodSource("readers")
  void acceptanceVerdictIsReadThroughTheShippedRoleOnWhicheverCliTheReadsChoose(
      OpenCodeProperties reads, String stdout, @TempDir Path worktree, @TempDir Path state) {
    FakeProcesses runner = new FakeProcesses().answering(new Processes.Result(0, stdout, ""));
    InstalledRoles roles =
        new InstalledRoles(
            new OrchestratorPaths(
                OrchestratorProperties.defaults().withRoot("../.."), FakeHost.machine()));
    var chosen =
        new ChosenCheckerReader(
            new HeadlessClaudeCheckerReader(
                headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class)),
                ClaudeProperties.defaults(),
                AssistantProperties.empty(),
                roles),
            new OpenCodeCheckerReader(
                new HeadlessOpenCode(
                    runner,
                    reads,
                    mock(UsageTracker.class),
                    openCodeGate().withState(state).build(mock(McpHealth.class), new ReadScopes())),
                reads,
                roles),
            reads);

    AcceptanceVerdict verdict =
        chosen
            .read(
                checkerRun()
                    .withWorktree(worktree)
                    .withSchema(
                        RoleDefinitions.fromFile(Path.of("../../roles/acceptance/role.yml.dist"))
                            .replies()
                            .answer()
                            .json())
                    .build())
            .facts()
            .orElseThrow();

    assertThat(verdict.rows())
        .extracting(AcceptanceVerdict.Row::criterion)
        .containsExactly("page opens");
  }
}
