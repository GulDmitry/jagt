package dev.jagt.orchestrator.adapter.assistant;

import static dev.jagt.orchestrator.adapter.assistant.TestHeadlessClaudeBuilder.headlessClaude;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import dev.jagt.orchestrator.adapter.FakeProcesses;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.protocol.MergeRequestRead;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.protocol.read.ReviewRead;
import dev.jagt.orchestrator.protocol.read.TicketSearch;
import dev.jagt.orchestrator.service.gate.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.usage.UsageTracker;
import dev.jagt.orchestrator.task.usage.AssistantCallKind;
import dev.jagt.orchestrator.task.usage.TokenUsage;
import dev.jagt.orchestrator.testing.FakeLogCapture;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class HeadlessClaudeTest {

  private final FakeProcesses runner =
      new FakeProcesses()
          .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
  private final HeadlessClaude claude =
      headlessClaude().build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class));

  @Test
  void readThatNamesWhatStoppedItComesBackUnreadableInsteadOfAsMissingRequest() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    """
                    {"structured_output":{"exists":false,"failure":"no GitLab MCP tool in this session",\
                    "sourceBranch":"","targetBranch":"","title":""}}\
                    """,
                    ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read https://git.example.com/g/p/-/merge_requests/7.",
                MergeRequestRead.SCHEMA.json(),
                "https://git.example.com/g/p/-/merge_requests/7",
                AssistantCallKind.MR_READ);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("no GitLab MCP tool in this session");
  }

  @Test
  void readAnErrorEndedWithNoResultNamesWhatEndedIt() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    """
                    {"type":"result","is_error":true,"subtype":"error_during_execution","result":""}\
                    """,
                    ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.failure()).isEqualTo("errored: error_during_execution");
  }

  @Test
  void readWhoseCommandStoppedWithJagtIsNoFailure() {
    runner.failing(new Processes.Stopped(List.of("claude")));

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            claude.read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ))
        .isInstanceOf(Processes.Stopped.class);
  }

  @Test
  void readRefusedByTheBreakerFailsAsUnavailableNotAsDidNotReturn() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    1,
"""
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
"uuid":"02368b97-942b-4e63-9855-74344bfb179e","queued_turn_count":0,"result_index":0}\
""",
                    ""));
    var claude =
        headlessClaude()
            .withBreaker(AssistantProperties.empty().withBreakerFailures(1))
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class));
    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            claude.read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ))
        .isInstanceOf(dev.jagt.orchestrator.port.assistant.AssistantUnavailable.class);
  }

  @Test
  void discardsAnAnswerWhoseRunNoCallOfReachedTheReadGate() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    ReadOnlyTools reads = mock(ReadOnlyTools.class);
    when(reads.lift(any())).thenReturn(Optional.of("no call reached the read gate"));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), reads)
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.facts()).isEmpty();
  }

  @Test
  void namesTheReadGateAndItsErrorWhenItRefusedEveryCallOfTheRun() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    "{\"result\":\"done\",\"permission_denials\":[{\"tool_name\":"
                        + "\"StructuredOutput\",\"error\":\"curl: not found\"}]}",
                    ""));
    ReadOnlyTools reads = mock(ReadOnlyTools.class);
    when(reads.refused(any())).thenReturn(true);
    when(reads.lift(any())).thenReturn(Optional.of("no call reached the read gate"));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), reads)
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.failure()).contains("read gate", "curl: not found");
  }

  @Test
  void callsOnlyTheAllowedReadsAndRefusesWhateverElseWouldPrompt() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    ReadOnlyTools reads = mock(ReadOnlyTools.class);
    when(reads.allowed(AssistantCallKind.TICKET_READ)).thenReturn(List.of("mcp__acme__get*"));
    var claude = headlessClaude().build(runner, mock(UsageTracker.class), reads);

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command)
        .containsSequence("--permission-mode", "dontAsk")
        .endsWith("--allowedTools", "mcp__acme__get*")
        .doesNotContain("bypassPermissions");
  }

  @Test
  void loadsNoBuiltInToolSoTicketCannotAskTheReadForShell() {

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).containsSequence("--tools", "");
  }

  @Test
  void refusesEveryMcpToolNamedAsWriteSoTicketCannotAskTheReadToChangeTheHost() {

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command)
        .contains(
            "mcp__*__create*",
            "mcp__*__save*",
            "mcp__*__accept*",
            "mcp__*__transition*",
            "mcp__*__evaluate*",
            "mcp__*__python*",
            "mcp__*__navigate*");
  }

  @Test
  void loadsOnlyTheDeclaredMcpServersInsteadOfWhateverTheHumanHasInstalled() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    var claude =
        headlessClaude()
            .withProperties(
                AssistantProperties.empty()
                    .withMcpConfig(
                        "{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}"))
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class));

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command)
        .containsSequence(
            "--mcp-config",
            "{\"mcpServers\":{\"a\":{\"command\":\"x\"},\"b\":{\"command\":\"y\"}}}");
    assertThat(command).contains("--strict-mcp-config");
    assertThat(command).containsSequence("--setting-sources", "user,project,local");
  }

  @Test
  void inheritsTheHumansOwnMcpServersWhenTheInstallDeclaresNone() {

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).containsSequence("--setting-sources", "user,project,local");
    assertThat(command).doesNotContain("--strict-mcp-config");
  }

  @Test
  void runsTheConfiguredModelInsteadOfWhateverTheHumansDefaultCostsToday() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    var claude =
        headlessClaude()
            .withProperties(AssistantProperties.empty().withModel("haiku"))
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class));

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).containsSequence("--model", "haiku");
  }

  @Test
  void inheritsTheHumansOwnModelWhenNoneIsConfigured() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    var claude =
        headlessClaude()
            .withProperties(AssistantProperties.empty().withModel(""))
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class));

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).doesNotContain("--model");
  }

  @Test
  void asksForTheJsonEnvelopeSoEveryCallCarriesItsOwnCost() {

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).containsSequence("--output-format", "json");
  }

  @Test
  void keepsTheWorktreeOutOfTheSystemPromptSoEveryReadSharesOneCache() {

    claude.read(
        "Read https://host/mr/9.",
        ReviewRead.SCHEMA.json(),
        "https://host/mr/9",
        AssistantCallKind.REVIEW_SWEEP);

    List<String> command = runner.command();
    assertThat(command).contains("--exclude-dynamic-system-prompt-sections");
  }

  @Test
  void tellsTheRetryWhyTheFirstReplyWasRefused() {
    runner.answeringInTurn(
        new Processes.Result(0, "{\"result\":\"The request is open.\"}", ""),
        new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));

    claude.read(
        "Read https://host/mr/9.",
        ReviewRead.SCHEMA.json(),
        "https://host/mr/9",
        AssistantCallKind.REVIEW_SWEEP);

    assertThat(runner.issued()).hasSize(2);
    assertThat(runner.issued().get(1))
        .contains(
            "Read https://host/mr/9.\n\n"
                + "Your last reply was prose without the StructuredOutput call: answer through"
                + " StructuredOutput alone.");
  }

  @Test
  void leavesNoTranscriptBehindSinceNothingResumesOneShot() {

    claude.read(
        "Read https://host/mr/9.",
        ReviewRead.SCHEMA.json(),
        "https://host/mr/9",
        AssistantCallKind.REVIEW_SWEEP);

    List<String> command = runner.command();
    assertThat(command).contains("--no-session-persistence");
  }

  @Test
  void cachesForMinutesSinceNoLaterReadComesBackWithinTheHour() {

    claude.read(
        "Read https://host/mr/9.",
        ReviewRead.SCHEMA.json(),
        "https://host/mr/9",
        AssistantCallKind.REVIEW_SWEEP);

    List<String> command = runner.command();
    assertThat(command).startsWith("env", "FORCE_PROMPT_CACHING_5M=1");
  }

  @Test
  void capsTheCliRetriesSoAnOutageEndsInsideTheTimeoutWithItsOwnError() {

    claude.read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).contains("CLAUDE_CODE_MAX_RETRIES=2");
  }

  @Test
  void readsNoFactsFromAnAnswerCarryingOnlyTheSchemasGenericFields() {
    runner.answering(
        new Processes.Result(
            0,
            "{\"type\":\"result\",\"is_error\":false,\"result\":\"{\\\"title\\\":\\\"Late"
                + " invoice mail\\\",\\\"url\\\":\\\"\\\"}\"}",
            ""));

    var answer =
        claude.read(
            "Read ABC-7.", TicketRead.SCHEMA.json(), "ABC-7", AssistantCallKind.TICKET_READ);

    assertThat(answer.facts()).isEmpty();
  }

  @Test
  void fallsBackToTheResultStringWhenTheEnvelopeCarriesNoParsedOutput() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":false,
             "result":"{\\"exists\\":true,\\"key\\":\\"ABC-7\\",\\"title\\":\\"Late invoice mail\\",\
            \\"trackerProject\\":\\"ABC\\",\\"labels\\":[],\\"url\\":\\"\\"}"}\
            """,
            ""));

    var facts =
        claude
            .read("Read ABC-7.", TicketRead.SCHEMA.json(), "ABC-7", AssistantCallKind.TICKET_READ)
            .facts();

    assertThat(facts).map(answer -> answer.path("title").asString()).contains("Late invoice mail");
  }

  @Test
  void readsTheSchemasObjectModelFencedInsideItsProse() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    """
                    {"type":"result","is_error":false,
                     "result":"The search returned no matching issues.\\n\\n```json\\n{\\"failure\\": \\"\\", \\"keys\\": []}\\n```"}\
                    """,
                    ""));

    var facts =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Find assignee = me.",
                TicketSearch.SCHEMA.json(),
                "assignee = me",
                AssistantCallKind.INTAKE)
            .facts();

    assertThat(facts).map(answer -> answer.path("keys").isEmpty()).contains(true);
  }

  @Test
  void namesTheModelsOwnWordsWhenItAnsweredPastTheSchema() {
    try (FakeLogCapture log = new FakeLogCapture(SchemaAnswer.class)) {
      FakeProcesses runner =
          new FakeProcesses()
              .answering(
                  new Processes.Result(
                      0,
                      """
                      {"type":"result","is_error":false,
                       "result":"I need the GitLab MCP server logged in before I can read this request."}\
                      """,
                      ""));

      headlessClaude()
          .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
          .read(
              "Read https://host/mr/9.",
              ReviewRead.SCHEMA.json(),
              "https://host/mr/9",
              AssistantCallKind.REVIEW_SWEEP);

      assertThat(log.events())
          .filteredOn(event -> "assistant answered outside the schema".equals(event.getMessage()))
          .flatExtracting(ILoggingEvent::getKeyValuePairs)
          .extracting(pair -> pair.key + "=" + pair.value)
          .contains("said=I need the GitLab MCP server logged in before I can read this request.");
    }
  }

  @Test
  void asksOnceMoreWhenTheModelClaimedTheSchemaToolButTheEnvelopeCarriesNoObject() {
    FakeProcesses runner =
        new FakeProcesses()
            .answeringInTurn(
                new Processes.Result(
                    0,
                    """
                    {"type":"result","is_error":false,
                     "result":"I have already called the StructuredOutput tool with project abc."}\
                    """,
                    ""),
                new Processes.Result(
                    0,
                    """
                    {"structured_output":{"failure":"","project":"abc","reason":"named","rule":""}}\
                    """,
                    ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Place ABC-42.",
                ProjectRead.schemaFor(Set.of("abc", "xyz")).json(),
                "ABC-42",
                AssistantCallKind.ROUTE);

    assertThat(answer.facts()).map(facts -> facts.path("project").asString()).contains("abc");
  }

  @Test
  void readsNoFactsFromAnAnswerNamingNoneOfTheSchemasFields() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    """
                    {"type":"result","is_error":false,
                     "result":"{\\"error\\":\\"the GitLab MCP server is not authenticated\\"}"}\
                    """,
                    ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read https://host/mr/9.",
                MergeRequestRead.SCHEMA.json(),
                "https://host/mr/9",
                AssistantCallKind.MR_READ);

    assertThat(answer.facts()).isEmpty();
  }

  @Test
  void readsNoFactsFromAnAnswerThatIsJsonButNotTheSchemasObject() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    """
                    {"type":"result","is_error":false,"result":"\\"no such merge request\\""}\
                    """,
                    ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read https://host/mr/9.",
                MergeRequestRead.SCHEMA.json(),
                "https://host/mr/9",
                AssistantCallKind.MR_READ);

    assertThat(answer.facts()).isEmpty();
  }

  @Test
  void reportsTheCostOfCallTheModelAnsweredWithAnError() {
    runner.answering(
        new Processes.Result(
            0,
            """
            {"type":"result","is_error":true,"total_cost_usd":0.12,
             "usage":{"input_tokens":5,"cache_creation_input_tokens":25000,\
            "cache_read_input_tokens":0,"output_tokens":40},
             "result":"the tracker MCP is not available"}\
            """,
            ""));

    var answer =
        claude.read(
            "Read https://host/mr/9.",
            ReviewRead.SCHEMA.json(),
            "https://host/mr/9",
            AssistantCallKind.REVIEW_SWEEP);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.usage()).isEqualTo(TokenUsage.ofCall(25_005, 0, 40, 0.12).asStart());
  }

  @Test
  void booksCallUnderItsKindEvenWhenItsReadCameBackUnreadable() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    """
                    {"total_cost_usd":0.06,"usage":{"input_tokens":31000,"output_tokens":40},
                     "structured_output":{"exists":false,"failure":"no tracker MCP tool"}}\
                    """,
                    ""));
    UsageTracker usage = mock(UsageTracker.class);

    headlessClaude()
        .build(runner, usage, mock(ReadOnlyTools.class))
        .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    verify(usage).record(AssistantCallKind.TICKET_READ, TokenUsage.ofCall(31_000, 0, 40, 0.06));
  }

  @Test
  void countsCacheWritesAsFreshInputAndCacheReadsApart() {
    var envelope =
        new JsonMapper()
            .readTree(
                """
                {"total_cost_usd":0.4,"usage":{"input_tokens":4,"cache_creation_input_tokens":38441,
                 "cache_read_input_tokens":38395,"output_tokens":60}}\
                """);

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

  @ParameterizedTest
  @MethodSource("readCalls")
  void logsTheCallsOfReadByToolRatherThanPrintingWhatEachRead(List<String> calls, String expected) {
    ReadOnlyTools reads = mock(ReadOnlyTools.class);
    when(reads.calls(any())).thenReturn(calls);
    try (FakeLogCapture log = new FakeLogCapture(HeadlessClaude.class)) {
      headlessClaude()
          .build(runner, mock(UsageTracker.class), reads)
          .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

      assertThat(log.events())
          .filteredOn(event -> "assistant read".equals(event.getMessage()))
          .flatExtracting(event ->
              event.getKeyValuePairs().stream().map(pair -> pair.key + "=" + pair.value).toList())
          .contains("ref=ABC-42", expected);
    }
  }

  private static Stream<Arguments> readCalls() {
    return Stream.of(
        Arguments.of(
            List.of(
                "Bash {command=ls src}",
                "Read {file_path=src/Foo.java}",
                "Bash {command=git diff --stat}"),
            "calls={Bash=2, Read=1}"),
        Arguments.of(
            List.of(
                "mcp__acme__get_issue {issueIdOrKey=ABC-42}",
                "StructuredOutput {text=# ABC-42 the whole ticket}"),
            "calls={mcp__acme__get_issue=1}"));
  }

  @Test
  void failedCallHandsTheCallerTheCliErrorTextBesideTheExit() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    1, "{\"type\":\"result\",\"is_error\":true,\"result\":\"Not logged in\"}", ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.failure()).isEqualTo("exit 1: Not logged in");
  }

  @Test
  void failedCallLogsTheCliErrorTextNotTheEnvelopeCountersThatPrecedeIt() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    1,
                    """
                    {"type":"result","is_error":true,"duration_api_ms":0,"total_cost_usd":0,\
                    "usage":{"input_tokens":0,"output_tokens":0,"cache_creation_input_tokens":0,\
                    "cache_read_input_tokens":0},"result":"Not logged in"}\
                    """,
                    ""));
    try (FakeLogCapture log = new FakeLogCapture(HeadlessClaude.class)) {
      headlessClaude()
          .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
          .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

      assertThat(log.events())
          .filteredOn(event -> "assistant call failed".equals(event.getMessage()))
          .flatExtracting(event ->
              event.getKeyValuePairs().stream().map(pair -> pair.key + "=" + pair.value).toList())
          .contains("cause=Not logged in");
    }
  }

  @Test
  void warnsRatherThanErrsWhenTheReadWasDeniedCallAndAnsweredAnyway() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    0,
                    "{\"structured_output\":{\"exists\":false},\"permission_denials\":[{\"tool_name"
                        + "\":\"Bash\"}]}",
                    ""));
    try (FakeLogCapture log = new FakeLogCapture(HeadlessClaude.class)) {
      headlessClaude()
          .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
          .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

      assertThat(log.events())
          .filteredOn(event -> "assistant tool calls denied".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.WARN);
    }
  }

  @Test
  void holdsReadToItsOwnReadsAndItsAnswerUntilItReturns() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    ReadOnlyTools reads = mock(ReadOnlyTools.class);
    when(reads.allowed(AssistantCallKind.TICKET_READ)).thenReturn(List.of("mcp__acme__get*"));
    when(reads.fence(any(), any())).thenReturn(List.of("--settings", "{\"hooks\":{}}"));

    headlessClaude()
        .build(runner, mock(UsageTracker.class), reads)
        .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    ArgumentCaptor<String> fence = ArgumentCaptor.captor();
    verify(reads)
        .fence(
            fence.capture(),
            eq(new ReadScope(List.of(), List.of("mcp__acme__get*", "StructuredOutput"), false)));
    verify(reads).lift(fence.getValue());
    List<String> command = runner.command();
    assertThat(command).containsSequence("--settings", "{\"hooks\":{}}");
  }

  @Test
  void countsCallColdWhenItsCacheReadIsZero() {
    var envelope =
        new JsonMapper()
            .readTree(
                """
                {"usage":{"input_tokens":4,"cache_creation_input_tokens":30000,
                 "cache_read_input_tokens":0,"output_tokens":60}}\
                """);

    assertThat(HeadlessClaude.usageOf(envelope).coldStarts()).isEqualTo(1);
  }

  @Test
  void countsCallInNeitherStartCountWhenItsUsageHasNoCacheFields() {
    var envelope =
        new JsonMapper().readTree("{\"usage\":{\"input_tokens\":4,\"output_tokens\":60}}");

    assertThat(HeadlessClaude.usageOf(envelope).measuredStarts()).isZero();
  }

  @Test
  void holdsEveryCallToTheConfiguredSpendCeiling() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));

    headlessClaude()
        .withProperties(AssistantProperties.empty().withMaxCostUsd(1.5))
        .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
        .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    List<String> command = runner.command();
    assertThat(command).containsSequence("--max-budget-usd", "1.50");
  }

  @Test
  void callThatReachesTheSpendCeilingFailsNamingItInsteadOfAnsweringEmpty() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(
                new Processes.Result(
                    1,
                    """
                    {"type":"result","subtype":"error_max_budget_usd","is_error":true,\
                    "errors":["Reached maximum budget ($0.70)"],"total_cost_usd":0.71}\
                    """,
                    ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).startsWith("spend ceiling reached");
  }

  @Test
  void callThatExitsWithoutAnEnvelopeFailsNamingItsCauseInsteadOfAnsweringEmpty() {
    FakeProcesses runner =
        new FakeProcesses().answering(new Processes.Result(2, "", "claude: not logged in"));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("exit 2: claude: not logged in");
  }

  @Test
  void callThatExitsWithOnlyPlainStdoutFailsNamingThatText() {
    FakeProcesses runner =
        new FakeProcesses().answering(new Processes.Result(2, "Service unavailable", ""));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.failure()).isEqualTo("exit 2: Service unavailable");
  }

  @Test
  void callThatNeverReturnedFailsNamingWhyAndBooksNoCost() {
    FakeProcesses runner =
        new FakeProcesses().failing(new IllegalStateException("Command timed out after PT2M"));

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.facts()).isEmpty();
    assertThat(answer.failure()).isEqualTo("did not return: Command timed out after PT2M");
    assertThat(answer.usage()).isEqualTo(TokenUsage.NONE);
  }

  @ParameterizedTest
  @CsvSource({"true,false,true", "false,true,true", "false,false,false"})
  void saysRetriedReadWasRefusedWhenEitherAttemptWasRefusedAtTheGate(
      boolean first, boolean second, boolean expected) {
    FakeProcesses runner =
        new FakeProcesses()
            .answeringInTurn(
                new Processes.Result(0, "{\"result\":\"The request is open.\"}", ""),
                new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    ReadOnlyTools reads = mock(ReadOnlyTools.class);
    when(reads.refused(any())).thenReturn(first, second);

    var answer =
        headlessClaude()
            .build(runner, mock(UsageTracker.class), reads)
            .read(
                "Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

    assertThat(answer.refused()).isEqualTo(expected);
  }

  @Test
  void warnsWhereCallReportedNoUsageSoItsCostIsKnownToBeUnaccounted() {
    FakeProcesses runner =
        new FakeProcesses()
            .answering(new Processes.Result(0, "{\"structured_output\":{\"exists\":false}}", ""));
    try (FakeLogCapture log = new FakeLogCapture(HeadlessClaude.class)) {
      headlessClaude()
          .build(runner, mock(UsageTracker.class), mock(ReadOnlyTools.class))
          .read("Read ABC-42.", TicketRead.SCHEMA.json(), "ABC-42", AssistantCallKind.TICKET_READ);

      assertThat(log.events())
          .filteredOn(event -> "assistant call reported no usage".equals(event.getMessage()))
          .extracting(ILoggingEvent::getLevel)
          .containsExactly(Level.WARN);
    }
  }

  @Test
  void countsAnEnvelopeThatReportsNoCostAsUnpricedNotAsFree() {
    var envelope =
        new JsonMapper().readTree("{\"usage\":{\"input_tokens\":4,\"output_tokens\":60}}");

    assertThat(HeadlessClaude.usageOf(envelope).costIsLowerBound()).isTrue();
  }
}
