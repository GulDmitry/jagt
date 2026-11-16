package dev.jagt.orchestrator.adapter.assistant.reach;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.service.reach.ReachLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class McpConfigReachTest {

  private final ReachLedger reach = new ReachLedger();

  @Test
  void headNamesTheKeyAndFileWhereTheAgentsConfigNamesOneTheContainerLacks() {
    ClaudeProperties claude =
        new ClaudeProperties("", "/run/secrets/no-such-mcp.json", "", List.of());

    new McpConfigReach(claude, AssistantProperties.empty(), reach).run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> {
          assertThat(lost.host()).isEqualTo("claude.mcpConfig");
          assertThat(lost.said())
              .contains("/run/secrets/no-such-mcp.json", "Mount it (docs/server.md)");
        });
  }

  @Test
  void headNamesTheAssistantKeyWhereItsConfigNamesOneTheContainerLacks() {
    AssistantProperties assistant =
        AssistantProperties.empty().withMcpConfig("/run/secrets/no-such-mcp.json");

    new McpConfigReach(ClaudeProperties.defaults(), assistant, reach).run();

    assertThat(reach.standing())
        .singleElement()
        .satisfies(lost -> assertThat(lost.host()).isEqualTo("assistant.mcpConfig"));
  }

  @Test
  void headClearsOnTheFirstReadThatFindsTheFile(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("mcp.json");
    ClaudeProperties claude = new ClaudeProperties("", file.toString(), "", List.of());
    McpConfigReach mcp = new McpConfigReach(claude, AssistantProperties.empty(), reach);
    mcp.run();

    Files.writeString(file, "{}");
    mcp.run();

    assertThat(reach.standing()).isEmpty();
  }

  @Test
  void headSaysNothingWhereMcpConfigIsTheJsonItself() {
    ClaudeProperties claude = new ClaudeProperties("", "{\"mcpServers\":{}}", "", List.of());

    new McpConfigReach(claude, AssistantProperties.empty(), reach).run();

    assertThat(reach.standing()).isEmpty();
  }
}
