package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;

import dev.jagt.orchestrator.port.Processes;

import ch.qos.logback.classic.Level;
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
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class)).read("Read https://git.example.com/g/p/-/merge_requests/7.",
                MergeRequestRead.SCHEMA.json(), "https://git.example.com/g/p/-/merge_requests/7",
                AssistantCallKind.MR_READ);

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("no GitLab MCP tool in this session");
    }

    @Test
    void aReadRefusedByTheBreakerFailsAsUnavailableNotAsDidNotReturn() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(1, """
{"duration_api_ms":0,"stop_reason":"stop_sequence",\
"session_id":"d638fd9d-2a7c-449d-8e68-a64542931b9c","total_cost_usd":0,\
"usage":{"output_tokens_details":{"thinking_tokens":0},"input_tokens":0,\
"cache_creation_input_tokens":0,"cache_read_input_tokens":0,"output_tokens":0,\
"server_tool_use":{"web_search_requests":0,"web_fetch_requests":0},"service_tier":"standard",\
"cache_creation":{"ephemeral_1h_input_tokens":0,"ephemeral_5m_input_tokens":0},"inference_geo":"",\
"iterations":[],"speed":"standard","fallback_credit":null},"modelUsage":{},"permission_denials":[],\
"terminal_reason":"api_error","fast_mode_state":"off",\
"fast_mode_disabled_reason":"sdk_opt_in_required","subagent_stats":{"spawned":0,\
"requested":{"background":0,"foreground":0,"unset":0},"started_in_background":0,"max_depth":0,\
"spawned_by_subagents":0,"completed":0,"failed":0,"killed":{"parent":0,"user":0,"system":0},\
"refused":{"depth_limit":0,"concurrency_limit":0,"budget":0},"by_type":{}},"safety_stops":0,\
"is_error":true,"num_turns":1,"subtype":"success","api_error_status":529,\
"result":"API Error: 529 Overloaded. This is a server-side issue,\
 usually temporary — try again in a moment. If it persists,\
 check your inference gateway (127.0.0.1:18529).","type":"result","duration_ms":733,\
"uuid":"02368b97-942b-4e63-9855-74344bfb179e","queued_turn_count":0,"result_index":0}""", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(
                AssistantProperties.empty().withBreakerFailures(1), java.time.Clock.systemUTC())),
                ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class),
                mock(ReadOnlyTools.class));
        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> claude.read("Read ABC-42.",
                TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ))
                .isInstanceOf(dev.jagt.orchestrator.port.AssistantUnavailable.class);
    }

    @Test
    void discardsAnAnswerWhoseRunNoCallOfReachedTheReadGate() {
        ProcessRunner runner = mock(ProcessRunner.class);
        ReadOnlyTools reads = mock(ReadOnlyTools.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        when(reads.lift(any())).thenReturn(Optional.of("no call reached the read gate"));

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), reads).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42",
                AssistantCallKind.TICKET_READ);

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void callsOnlyTheAllowedReadsAndRefusesWhateverElseWouldPrompt() {
        ProcessRunner runner = mock(ProcessRunner.class);
        ReadOnlyTools reads = mock(ReadOnlyTools.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        when(reads.allowed(AssistantCallKind.TICKET_READ)).thenReturn(List.of("mcp__acme__get*"));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), reads);

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--permission-mode", "dontAsk")
                .endsWith("--allowedTools", "mcp__acme__get*")
                .doesNotContain("bypassPermissions");
    }

    @Test
    void loadsNoBuiltInToolSoATicketCannotAskTheReadForAShell() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(),
                AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("mcp__*__create*", "mcp__*__save*", "mcp__*__accept*",
                "mcp__*__transition*", "mcp__*__evaluate*", "mcp__*__python*", "mcp__*__navigate*");
    }

    @Test
    void loadsOnlyTheDeclaredMcpServersInsteadOfWhateverTheHumanHasInstalled() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty()
                .withMcpConfig("{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}"), mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty().withModel("haiku"), mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty().withModel(""), mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

        claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("--exclude-dynamic-system-prompt-sections");
    }

    @Test
    void tellsTheRetryWhyTheFirstReplyWasRefused() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"result\":\"The request is open.\"}", ""))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

        claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner, times(2)).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getAllValues().get(1))
                .contains("Read https://host/mr/9.\n\nYour last reply was prose without the StructuredOutput call:"
                        + " answer through StructuredOutput alone.");
    }

    @Test
    void leavesNoTranscriptBehindSinceNothingResumesAOneShot() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

        claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("--no-session-persistence");
    }

    @Test
    void cachesForMinutesSinceNoLaterReadComesBackWithinTheHour() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

        claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).startsWith("env", "FORCE_PROMPT_CACHING_5M=1");
    }

    @Test
    void capsTheCliRetriesSoAnOutageEndsInsideTheTimeoutWithItsOwnError() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(),
                java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

        claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).contains("CLAUDE_CODE_MAX_RETRIES=2");
    }

    @Test
    void readsNoFactsFromAnAnswerCarryingOnlyTheSchemasGenericFields() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"type\":\"result\",\"is_error\":false,"
                        + "\"result\":\"{\\\"title\\\":\\\"Late invoice mail\\\",\\\"url\\\":\\\"\\\"}\"}", ""));
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

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

        var facts = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class)).read("Find assignee = me.", TicketSearch.SCHEMA.json(), "assignee = me",
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

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class), mock(ReadOnlyTools.class))
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

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class)).read("Place ABC-42.", ProjectRead.schemaFor(Set.of("abc", "xyz")).json(),
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

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class)).read("Read https://host/mr/9.", MergeRequestRead.SCHEMA.json(),
                "https://host/mr/9", AssistantCallKind.MR_READ);

        assertThat(answer.facts()).isEmpty();
    }

    @Test
    void readsNoFactsFromAnAnswerThatIsJsonButNotTheSchemasObject() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"type":"result","is_error":false,"result":"\\"no such merge request\\""}""", ""));

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class)).read("Read https://host/mr/9.", MergeRequestRead.SCHEMA.json(),
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
        var claude = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class));

        var answer = claude.read("Read https://host/mr/9.", ReviewRead.SCHEMA.json(), "https://host/mr/9",
                AssistantCallKind.REVIEW_SWEEP);

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.usage()).isEqualTo(TokenUsage.ofCall(25_005, 0, 40, 0.12).asStart());
    }

    @Test
    void booksACallUnderItsKindEvenWhenItsReadCameBackUnreadable() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                """
                {"total_cost_usd":0.06,"usage":{"input_tokens":31000,"output_tokens":40},
                 "structured_output":{"exists":false,"failure":"no tracker MCP tool"}}""", ""));
        UsageTracker usage = mock(UsageTracker.class);

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), usage, mock(ReadOnlyTools.class))
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

    @Test
    @ResourceLock(Resources.GLOBAL)
    void countsTheCallsOfAReadByToolRatherThanPrintingWhatEachRead() {
        ProcessRunner runner = mock(ProcessRunner.class);
        ReadOnlyTools reads = mock(ReadOnlyTools.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        when(reads.calls(any())).thenReturn(List.of("Bash {command=ls src}", "Read {file_path=src/Foo.java}",
                "Bash {command=git diff --stat}"));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(HeadlessClaude.class)).addAppender(log);

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class),
                reads).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        assertThat(List.copyOf(log.list)).filteredOn(event -> "assistant read".equals(event.getMessage()))
                .flatExtracting(ILoggingEvent::getKeyValuePairs)
                .extracting(pair -> pair.key + "=" + pair.value)
                .contains("ref=ABC-42", "calls={Bash=2, Read=1}");
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void warnsRatherThanErrsWhenTheReadWasDeniedACallAndAnsweredAnyway() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"exists\":false},\"permission_denials\":[{\"tool_name\":\"Bash\"}]}", ""));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(HeadlessClaude.class)).addAppender(log);

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class),
                mock(ReadOnlyTools.class)).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        assertThat(List.copyOf(log.list)).filteredOn(event -> "assistant tool calls denied".equals(event.getMessage()))
                .extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void leavesTheAnswerOutOfTheCallsItLogsBecauseItIsNoSource() {
        ProcessRunner runner = mock(ProcessRunner.class);
        ReadOnlyTools reads = mock(ReadOnlyTools.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        when(reads.calls(any())).thenReturn(List.of("mcp__acme__get_issue {issueIdOrKey=ABC-42}",
                "StructuredOutput {text=# ABC-42 the whole ticket}"));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        ((Logger) LoggerFactory.getLogger(HeadlessClaude.class)).addAppender(log);

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class),
                reads).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        assertThat(List.copyOf(log.list)).filteredOn(event -> "assistant read".equals(event.getMessage()))
                .flatExtracting(ILoggingEvent::getKeyValuePairs)
                .extracting(pair -> pair.key + "=" + pair.value)
                .contains("calls={mcp__acme__get_issue=1}");
    }

    @Test
    void holdsAReadToItsOwnReadsAndItsAnswerUntilItReturns() {
        ProcessRunner runner = mock(ProcessRunner.class);
        ReadOnlyTools reads = mock(ReadOnlyTools.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        when(reads.allowed(AssistantCallKind.TICKET_READ)).thenReturn(List.of("mcp__acme__get*"));
        when(reads.fence(any(), any())).thenReturn(List.of("--settings", "{\"hooks\":{}}"));

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class),
                reads).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<String> fence = ArgumentCaptor.captor();
        verify(reads).fence(fence.capture(),
                eq(new ReadScope(List.of(), List.of("mcp__acme__get*", "StructuredOutput"), false)));
        verify(reads).lift(fence.getValue());
        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--settings", "{\"hooks\":{}}");
    }

    @Test
    void countsACallColdWhenItsCacheReadIsZero() {
        var envelope = new JsonMapper().readTree("""
                {"usage":{"input_tokens":4,"cache_creation_input_tokens":30000,
                 "cache_read_input_tokens":0,"output_tokens":60}}""");

        assertThat(HeadlessClaude.usageOf(envelope).coldStarts()).isEqualTo(1);
    }

    @Test
    void countsACallInNeitherStartCountWhenItsUsageHasNoCacheFields() {
        var envelope = new JsonMapper().readTree("{\"usage\":{\"input_tokens\":4,\"output_tokens\":60}}");

        assertThat(HeadlessClaude.usageOf(envelope).measuredStarts()).isZero();
    }

    @Test
    void holdsEveryCallToTheConfiguredSpendCeiling() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty().withMaxCostUsd(1.5),
                mock(UsageTracker.class), mock(ReadOnlyTools.class))
                .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        ArgumentCaptor<List<String>> command = ArgumentCaptor.captor();
        verify(runner).run(any(Path.class), any(Duration.class), command.capture());
        assertThat(command.getValue()).containsSequence("--max-budget-usd", "1.50");
    }

    @Test
    void aCallThatReachesTheSpendCeilingFailsNamingItInsteadOfAnsweringEmpty() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(1,
                """
                {"type":"result","subtype":"error_max_budget_usd","is_error":true,\
                "errors":["Reached maximum budget ($0.70)"],"total_cost_usd":0.71}""", ""));

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class))
                .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).startsWith("spend ceiling reached");
    }

    @Test
    void aCallThatExitsWithoutAnEnvelopeFailsNamingItsCauseInsteadOfAnsweringEmpty() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(2, "", "claude: not logged in"));

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class))
                .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("exit 2");
    }

    @Test
    void aCallThatNeverReturnedFailsNamingWhyAndBooksNoCost() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenThrow(new IllegalStateException("Command timed out after PT2M"));

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), mock(ReadOnlyTools.class)).read("Read ABC-42.",
                TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

        assertThat(answer.facts()).isEmpty();
        assertThat(answer.failure()).isEqualTo("did not return: Command timed out after PT2M");
        assertThat(answer.usage()).isEqualTo(TokenUsage.NONE);
    }

    @ParameterizedTest
    @CsvSource({"true,false,true", "false,true,true", "false,false,false"})
    void saysARetriedReadWasRefusedWhenEitherAttemptWasRefusedAtTheGate(boolean first, boolean second, boolean expected) {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any()))
                .thenReturn(new Processes.Result(0, "{\"result\":\"The request is open.\"}", ""))
                .thenReturn(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
        ReadOnlyTools reads = mock(ReadOnlyTools.class);
        when(reads.refused(any())).thenReturn(first, second);

        var answer = new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(),
                mock(UsageTracker.class), reads).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42",
                AssistantCallKind.TICKET_READ);

        assertThat(answer.refused()).isEqualTo(expected);
    }

    @Test
    @ResourceLock(Resources.GLOBAL)
    void warnsWhereACallReportedNoUsageSoItsCostIsKnownToBeUnaccounted() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any(Path.class), any(Duration.class), any())).thenReturn(new Processes.Result(0,
                "{\"structured_output\":{\"exists\":false}}", ""));
        ListAppender<ILoggingEvent> log = new ListAppender<>();
        log.start();
        Logger assistantLog = (Logger) LoggerFactory.getLogger(HeadlessClaude.class);
        assistantLog.addAppender(log);

        new HeadlessClaude(new AssistantProcess(runner, new AssistantBreaker(AssistantProperties.empty(), java.time.Clock.systemUTC())), ClaudeProperties.defaults(), AssistantProperties.empty(), mock(UsageTracker.class),
                mock(ReadOnlyTools.class)).read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42",
                AssistantCallKind.TICKET_READ);

        assertThat(List.copyOf(log.list)).filteredOn(event -> "assistant call reported no usage".equals(event.getMessage()))
                .extracting(ILoggingEvent::getLevel).containsExactly(Level.WARN);
        assistantLog.detachAppender(log);
    }
}
