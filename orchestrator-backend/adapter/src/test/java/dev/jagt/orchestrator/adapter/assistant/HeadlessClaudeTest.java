package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;

import dev.jagt.orchestrator.port.Processes;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.protocol.MergeRequestRead;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.protocol.ReviewRead;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.protocol.TicketSearch;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HeadlessClaudeTest {

    @Test
    void aReadThatNamesWhatStoppedItComesBackUnreadableInsteadOfAsAMissingRequest() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"structured_output":{"exists":false,"failure":"no GitLab MCP tool in this session",\
                "sourceBranch":"","targetBranch":"","title":""}}""", ""));

        var answer = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class)).read("Read https://git.example.com/g/p/-/merge_requests/7.",
                MergeRequestRead.SCHEMA.json(), "https://git.example.com/g/p/-/merge_requests/7",
                AssistantCallKind.MR_READ);

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void liftsThePermissionGateSoTheHeadlessReadCanCallMcpTools() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty().withPermissionMode("bypassPermissions"), mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--permission-mode", "bypassPermissions");
    }

    @Test
    void runsOnlyTheConfiguredMcpToolsInsteadOfBypassingEveryPermission() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty()
                .withPermissionMode("bypassPermissions").withAllowedTools(List.of("mcp__acme_jira", "mcp__acme_gitlab")),
                mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).endsWith("--permission-mode", "dontAsk", "--allowedTools", "mcp__acme_jira",
                "mcp__acme_gitlab");
    }

    @Test
    void loadsNoBuiltInToolSoATicketCannotAskTheReadForAShell() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty().withPermissionMode("bypassPermissions"), mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--tools", "");
    }

    @Test
    void refusesEveryMcpToolNamedAsAWriteSoATicketCannotAskTheReadToChangeTheHost() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(),
                AssistantProperties.empty().withPermissionMode("bypassPermissions"), mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("mcp__*__create*", "mcp__*__save*", "mcp__*__accept*",
                "mcp__*__transition*");
    }

    @Test
    void loadsOnlyTheDeclaredMcpServersInsteadOfWhateverTheHumanHasInstalled() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty()
                .withMcpConfig("{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}"), mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--mcp-config",
                "{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}");
        assertThat(command.getValue()).contains("--strict-mcp-config");
        assertThat(command.getValue()).containsSequence("--setting-sources", "user,project,local");
    }

    @Test
    void inheritsTheHumansOwnMcpServersWhenTheInstallDeclaresNone() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--setting-sources", "user,project,local");
        assertThat(command.getValue()).doesNotContain("--strict-mcp-config");
    }

    @Test
    void runsTheConfiguredModelInsteadOfWhateverTheHumansDefaultCostsToday() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty().withModel("haiku"), mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--model", "haiku");
    }

    @Test
    void inheritsTheHumansOwnModelWhenNoneIsConfigured() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty().withModel(""), mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).doesNotContain("--model");
    }

    @Test
    void asksForTheJsonEnvelopeSoEveryCallCarriesItsOwnCost() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--output-format", "json");
    }

    @Test
    void keepsTheWorktreeOutOfTheSystemPromptSoEveryReadSharesOneCache() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class));

        claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("--exclude-dynamic-system-prompt-sections");
    }

    @Test
    void readsNoFactsFromAnAnswerCarryingOnlyTheSchemasGenericFields() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"type\":\"result\",\"is_error\":false,"
                        + "\"result\":\"{\\\"title\\\":\\\"Late invoice mail\\\",\\\"url\\\":\\\"\\\"}\"}", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class));

        var answer = claude.read("Read ABC-7.", TicketRead.SCHEMA.json(), "ABC-7", AssistantCallKind.TICKET_READ);

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void fallsBackToTheResultStringWhenTheEnvelopeCarriesNoParsedOutput() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "result":"{\\"exists\\":true,\\"key\\":\\"ABC-7\\",\\"title\\":\\"Late invoice mail\\",\
                \\"trackerProject\\":\\"ABC\\",\\"labels\\":[],\\"url\\":\\"\\"}"}""", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class));

        var facts = claude.read("Read ABC-7.", TicketRead.SCHEMA.json(), "ABC-7", AssistantCallKind.TICKET_READ).facts();

        assertThat(facts).map(answer -> answer.path("title").asString()).contains("Late invoice mail");
    }

    @Test
    void readsTheSchemasObjectAModelFencedInsideItsProse() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "result":"The search returned no matching issues.\\n\\n```json\\n{\\"failure\\": \\"\\", \\"keys\\": []}\\n```"}""", ""));

        var facts = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class)).read("Find assignee = me.", TicketSearch.SCHEMA.json(), "assignee = me",
                AssistantCallKind.INTAKE).facts();

        assertThat(facts).map(answer -> answer.path("keys").isEmpty()).contains(true);
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void namesTheModelsOwnWordsWhenItAnsweredPastTheSchema() {
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger assistantLog = (Logger) LoggerFactory.getLogger(HeadlessClaude.class);
        assistantLog.addAppender(log);
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "result":"I need the GitLab MCP server logged in before I can read this request."}""", ""));

        new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class))
                .read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                        AssistantCallKind.REVIEW_SWEEP);

        assertThat(List.copyOf(log.list))
                .filteredOn(event -> "assistant answered outside the schema".equals(event.getMessage()))
                .flatExtracting(ILoggingEvent::getKeyValuePairs)
                .extracting(pair -> pair.key + "=" + pair.value)
                .contains("said=I need the GitLab MCP server logged in before I can read this request.");
        assistantLog.detachAppender(log);
    }

    @Test
    void asksOnceMoreWhenTheModelClaimedTheSchemaToolButTheEnvelopeCarriesNoObject() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(
                new Processes.Result(0, """
                        {"type":"result","is_error":false,
                         "result":"I have already called the StructuredOutput tool with project abc."}""", ""),
                new Processes.Result(0, """
                        {"structured_output":{"failure":"","project":"abc","reason":"named","rule":""}}""", ""));

        var answer = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class)).read("Place ABC-42.", ProjectRead.schemaFor(Set.of("abc", "xyz")).json(),
                "ABC-42", AssistantCallKind.ROUTE);

        assertThat(answer.facts()).map(facts -> facts.path("project").asString()).contains("abc");
    }

    @Test
    void readsNoFactsFromAnAnswerNamingNoneOfTheSchemasFields() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,
                 "result":"{\\"error\\":\\"the GitLab MCP server is not authenticated\\"}"}""", ""));

        var answer = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class)).read("Read https://host/mr/9.", MergeRequestRead.SCHEMA.json(),
                "https://host/mr/9", AssistantCallKind.MR_READ);

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void readsNoFactsFromAnAnswerThatIsJsonButNotTheSchemasObject() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,"result":"\\"no such merge request\\""}""", ""));

        var answer = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class)).read("Read https://host/mr/9.", MergeRequestRead.SCHEMA.json(),
                "https://host/mr/9", AssistantCallKind.MR_READ);

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void reportsTheCostOfACallTheModelAnsweredWithAnError() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":true,"total_cost_usd":0.12,
                 "usage":{"input_tokens":5,"cache_creation_input_tokens":25000,\
                "cache_read_input_tokens":0,"output_tokens":40},
                 "result":"the tracker MCP is not available"}""", ""));
        var claude = new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class));

        var answer = claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.usage()).isEqualTo(TokenUsage.ofCall(25_005, 0, 40, 0.12));
    }

    @Test
    void booksACallUnderItsKindEvenWhenItsReadCameBackUnreadable() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"total_cost_usd":0.06,"usage":{"input_tokens":31000,"output_tokens":40},
                 "structured_output":{"exists":false,"failure":"no tracker MCP tool"}}""", ""));
        UsageTracker usage = mock(UsageTracker.class);

        new HeadlessClaude(runner, ClaudeProperties.defaults(), AssistantProperties.empty(), usage)
                .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        verify(usage).record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(31_000, 0, 40, 0.06));
    }

    @Test
    void countsCacheWritesAsFreshInputAndCacheReadsApart() {
        var envelope = new JsonMapper().readTree("""
                {"total_cost_usd":0.4,"usage":{"input_tokens":4,"cache_creation_input_tokens":38441,
                 "cache_read_input_tokens":38395,"output_tokens":60}}""");

        TokenUsage usage = HeadlessClaude.usageOf(envelope);

        assertThat(usage.calls()).isEqualTo(1);
        assertThat(usage.inputTokens()).isEqualTo(38445);
        assertThat(usage.cachedInputTokens()).isEqualTo(38395);
        assertThat(usage.outputTokens()).isEqualTo(60);
        assertThat(usage.costUsd()).isEqualTo(0.4);
    }

    @Test
    void reportsNoUsageRatherThanZerosWhenTheOutputCarriesNoUsageBlock() {
        var envelope = new JsonMapper().readTree("{\"result\":\"{}\"}");

        assertThat(HeadlessClaude.usageOf(envelope)).isEqualTo(TokenUsage.NONE);
    }
}
