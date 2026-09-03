package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.port.Processes;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpHealthProbeTest {

    @Test
    void namesEveryServerAReadCannotUseAndNoneItCan() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0, """
                Checking MCP server health...
                context7: npx -y @upstash/context7-mcp - ✔ Connected
                plugin:acme:gitlab: sh -c exec docker run -i --rm acme/gitlab-mcp - ✘ Failed to connect \
                — -32000: Connection closed
                acme tracker: https://mcp.example.com/mcp (HTTP) - ! Needs authentication
                """, ""));

        var broken = new McpHealthProbe(runner, ClaudeProperties.defaults(), AssistantProperties.empty())
                .brokenServers();

        assertThat(broken).contains(List.of("plugin:acme:gitlab (Failed to connect)",
                "acme tracker (Needs authentication)"));
    }

    @Test
    void reportsAProbeThatFailedAsUnknownRatherThanAsNothingBeingDown() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(1, "", "claude: command not found"));

        var broken = new McpHealthProbe(runner, ClaudeProperties.defaults(), AssistantProperties.empty())
                .brokenServers();

        assertThat(broken).isEmpty();
    }

    @Test
    void reportsOutputThatNamesNoServerAsUnknownRatherThanAsNothingBeingDown() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "No MCP servers configured. Use `claude mcp add` to add one.", ""));

        var broken = new McpHealthProbe(runner, ClaudeProperties.defaults(), AssistantProperties.empty())
                .brokenServers();

        assertThat(broken).isEmpty();
    }

    @Test
    void namesTheLoginADeclaredServerNeedsWhereItCannotJudgeOne() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger probeLog = (Logger) LoggerFactory.getLogger(McpHealthProbe.class);
        probeLog.addAppender(log);

        new McpHealthProbe(mock(ProcessRunner.class), ClaudeProperties.defaults(),
                AssistantProperties.empty().withMcpConfig("/opt/acme/servers.json")).brokenServers();

        assertThat(List.copyOf(log.list)).filteredOn(event -> "mcp probe skipped".equals(event.getMessage()))
                .flatExtracting(ILoggingEvent::getKeyValuePairs)
                .extracting(pair -> pair.key + "=" + pair.value)
                .contains("fix=claude --strict-mcp-config --mcp-config /opt/acme/servers.json"
                        + " --setting-sources user,project,local, then /mcp: a declared server carries its"
                        + " own login");
        probeLog.detachAppender(log);
    }

    @Test
    void refusesToJudgeServersThatAreDeclaredRatherThanResolvedByTheCli() {
        ProcessRunner runner = mock(ProcessRunner.class);

        var broken = new McpHealthProbe(runner, ClaudeProperties.defaults(), AssistantProperties.empty()
                .withMcpConfig("{\"mcpServers\":{\"acme\":{\"command\":\"x\"}}}")).brokenServers();

        assertThat(broken).isEmpty();
        verifyNoInteractions(runner);
    }
}
