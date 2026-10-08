package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.ProcessRunner;
import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.port.Answer;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.ReadScopes.ReadScope;
import dev.jagt.orchestrator.service.UsageTracker;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.TokenUsage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One headless {@code claude -p} question, booked the moment it returns. Hardcodes no MCP server or path:
 * {@code --setting-sources} makes the child inherit the human's own MCP config, and running from the temp dir loads
 * only their user-level servers. Servers declared here instead lose their plugin scope in tool names, so an
 * allow-list written for the inherited spelling stops matching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class HeadlessClaude {

    static final Duration TIMEOUT = Duration.ofMinutes(3);
    /** A model cannot otherwise tell "no such item" from "I never got to look"; the second must not read as the first. */
    static final String FAILURE_RULE = " Answer failure=\"\" ONLY when the host itself answered you:"
            + " with an answer, or with a no such item — that one is exists=false and empty fields. If ANYTHING"
            + " stopped you from reading it instead — no MCP tool for that host, a tool that errored, an"
            + " authentication or network failure, a denied permission — then failure=<one line naming exactly"
            + " what stopped you, and which tool or server it was>, and NEVER report that as not existing."
            + " A tool asking for a site or cloud id gets one the host's own listing of reachable sites"
            + " answered, never one recalled or guessed.";
    /** The CLI's own tool carrying the answer a {@code --json-schema} asks for. */
    static final String STRUCTURED_OUTPUT = "StructuredOutput";
    private static final int MAX_CAUSE = 400;
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    private final ProcessRunner processRunner;
    private final ClaudeProperties claude;
    private final AssistantProperties assistant;
    private final UsageTracker usageTracker;
    private final ReadOnlyTools readOnlyTools;
    private final JsonMapper mapper = new JsonMapper();

    /** A read that reports what stopped it comes back unreadable, never as an answer with empty facts. */
    Answer<JsonNode> read(String prompt, String schema, String label, AssistantCallKind kind, Duration timeout) {
        Answer<JsonNode> answer = ask(prompt, schema, label, kind, timeout);
        String failure = answer.facts().map(n -> n.path("failure").asString("")).orElse("").trim();
        if (failure.isEmpty()) {
            return answer;
        }
        log.atWarn().setMessage("read failed")
                .addKeyValue("ref", label)
                .addKeyValue("cause", failure)
                .log();
        return new Answer<>(Optional.empty(), answer.usage());
    }

    Answer<JsonNode> read(String prompt, String schema, String label, AssistantCallKind kind) {
        return read(prompt, schema, label, kind, TIMEOUT);
    }

    Answer<JsonNode> ask(String prompt, String schema, String label, AssistantCallKind kind, Duration timeout) {
        Reply first = call(prompt, schema, label, kind, timeout);
        Optional<JsonNode> answer = first.answered() ? answerOf(first.envelope(), label, schema) : Optional.empty();
        if (!first.answered() || answer.isPresent()) {
            return new Answer<>(answer, first.usage());
        }
        // The CLI at times hands back the model's prose without the object its schema tool validated.
        Reply second = call(prompt, schema, label, kind, timeout);
        return new Answer<>(second.answered() ? answerOf(second.envelope(), label, schema) : Optional.empty(),
                first.usage().plus(second.usage()));
    }

    /** A failed call carries no envelope, only its cause: asking again would not mend it. */
    record Reply(JsonNode envelope, TokenUsage usage, String cause) {

        static Reply failed(TokenUsage usage, String cause) {
            return new Reply(null, usage, cause);
        }

        boolean answered() {
            return envelope != null;
        }
    }

    /** Which servers load, and whether any load at all, are the KIND's to answer — never a flag beside it. */
    private Reply call(String prompt, String schema, String label, AssistantCallKind kind, Duration timeout) {
        boolean withMcp = kind != AssistantCallKind.COMMAND_MAP;
        String pinned = assistant.mcpConfigFor(kind);
        List<String> cmd = new ArrayList<>(List.of(claude.command(), prompt, "-p",
                "--json-schema", schema,
                // The envelope carries the call's token usage and cost alongside the answer.
                "--output-format", "json",
                // Off the system prompt, cwd and git status stop making every worktree write its own cache.
                "--exclude-dynamic-system-prompt-sections",
                "--tools", ""));
        if (!withMcp) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}"));
        } else if (!pinned.isBlank()) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", pinned,
                    "--setting-sources", assistant.settingSources()));
        } else {
            cmd.addAll(List.of("--setting-sources", assistant.settingSources()));
        }
        if (assistant.model() != null && !assistant.model().isBlank()) {
            cmd.add("--model");
            cmd.add(assistant.model());
        }
        // Headless `-p` cannot answer a permission prompt: what the allow-list does not name is refused.
        List<String> allowed = withMcp ? readOnlyTools.allowed(kind) : List.of();
        if (!withMcp) {
            log.atDebug().setMessage("assistant call without mcp")
                    .addKeyValue("ref", label)
                    .log();
        } else {
            cmd.addAll(List.of("--permission-mode", "dontAsk", "--disallowedTools"));
            cmd.addAll(ReadOnlyTools.MCP_WRITES);
            if (!allowed.isEmpty()) {
                cmd.add("--allowedTools");
                cmd.addAll(allowed);
            }
        }
        List<String> tools = new ArrayList<>(allowed);
        tools.add(STRUCTURED_OUTPUT);
        return run(Path.of(System.getProperty("java.io.tmpdir")), timeout, cmd, new ReadScope(List.of(), tools, false),
                kind, label);
    }

    /**
     * Any headless command, held to {@code scope} while it runs and booked under {@code kind} the moment it returns,
     * whatever it answered.
     */
    Reply run(Path cwd, Duration timeout, List<String> cmd, ReadScope scope, AssistantCallKind kind, String label) {
        String fence = UUID.randomUUID().toString();
        List<String> fenced = new ArrayList<>(cmd);
        fenced.addAll(readOnlyTools.fence(fence, scope));
        try {
            return booked(cwd, timeout, fenced, kind, label);
        } finally {
            readOnlyTools.lift(fence);
        }
    }

    private Reply booked(Path cwd, Duration timeout, List<String> cmd, AssistantCallKind kind, String label) {
        Processes.Result result;
        try {
            result = processRunner.run(cwd, timeout, cmd);
        } catch (RuntimeException e) {
            // A timeout kills the CLI: no envelope, so the tokens already burned are unknowable, not zero.
            log.atWarn().setMessage("assistant call did not return")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("limit", timeout)
                    .addKeyValue("effect", "token cost unmeasured")
                    .log();
            return Reply.failed(TokenUsage.NONE, "did not return: " + e.getMessage());
        }
        JsonNode envelope = parseEnvelope(result.stdout(), label);
        // Booked whatever the outcome: a call that errored or came back unusable was still paid for.
        TokenUsage usage = usageOf(envelope);
        usageTracker.record(kind, usage);
        if (usage.isNone()) {
            log.atWarn().setMessage("assistant call reported no usage")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", "no usage block")
                    .addKeyValue("effect", "token cost unaccounted")
                    .log();
        }
        JsonNode denials = envelope == null ? null : envelope.path("permission_denials");
        if (denials != null && denials.isArray() && !denials.isEmpty()) {
            log.atError().setMessage("assistant tool calls denied")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", oneLine(denials.toString()))
                    .log();
        }
        if (result.exitCode() != 0 || envelope == null) {
            log.atWarn().setMessage("assistant call failed")
                    .addKeyValue("ref", label)
                    .addKeyValue("exit", result.exitCode())
                    .addKeyValue("cause", oneLine(result.stderr().isBlank() ? result.stdout() : result.stderr()))
                    .log();
            return Reply.failed(usage, "exit " + result.exitCode());
        }
        if (envelope.path("is_error").asBoolean(false)) {
            log.atWarn().setMessage("assistant call errored")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", oneLine(envelope.path("result").asString("")))
                    .log();
            return Reply.failed(usage, "errored");
        }
        return new Reply(envelope, usage, "");
    }

    /** `%kvp` quotes a value but escapes nothing, so a multi-line stderr would break the console line apart. */
    private static String oneLine(String value) {
        return OneLine.of(value == null ? "" : value.replace('"', '\''), MAX_CAUSE);
    }

    private JsonNode parseEnvelope(String stdout, String label) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(stdout);
        } catch (RuntimeException e) {
            log.atWarn().setMessage("assistant json unparseable")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", e.toString())
                    .log();
            return null;
        }
    }

    /** {@code structured_output} when the CLI already parsed it, else {@code result}, holding the same JSON. */
    private Optional<JsonNode> answerOf(JsonNode envelope, String label, String schema) {
        JsonNode structured = envelope.path("structured_output");
        if (structured.isObject()) {
            return Optional.of(structured);
        }
        String raw = envelope.path("result").asString("");
        if (raw.isBlank()) {
            log.atWarn().setMessage("assistant answer empty")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", "result blank")
                    .log();
            return Optional.empty();
        }
        try {
            JsonNode answer = mapper.readTree(fenced(raw));
            return answer.isObject() && carriesSchema(answer, schema)
                    ? Optional.of(answer)
                    : outsideSchema(label, "not the schema's object", raw);
        } catch (RuntimeException e) {
            return outsideSchema(label, e.getMessage(), raw);
        }
    }

    /** A model that skipped the schema tool often still writes the object, fenced inside its prose: the last fence. */
    private static String fenced(String raw) {
        Matcher fence = FENCE.matcher(raw);
        String last = raw;
        while (fence.find()) {
            last = fence.group(1);
        }
        return last;
    }

    /**
     * The CLI validated {@code structured_output} against the schema; this fallback nobody checked. The
     * schema's FIRST required field is its discriminator, so an answer built of generic names alone
     * ({@code title}, {@code url}) cannot pass as facts and be read as "no such item".
     */
    private boolean carriesSchema(JsonNode answer, String schema) {
        String discriminator = mapper.readTree(schema).path("required").path(0).asString("");
        return !discriminator.isEmpty() && answer.has(discriminator);
    }

    /** An answer read as facts would report a read that never happened as the item not existing. */
    private Optional<JsonNode> outsideSchema(String label, String cause, String said) {
        log.atWarn().setMessage("assistant answered outside the schema")
                .addKeyValue("ref", label)
                .addKeyValue("cause", oneLine(cause))
                .addKeyValue("said", oneLine(said))
                .log();
        return Optional.empty();
    }

    /** Fresh input = prompt + cache WRITES, both billed at input rates; cache reads count apart, being cheaper. */
    static TokenUsage usageOf(JsonNode envelope) {
        if (envelope == null) {
            return TokenUsage.NONE;
        }
        JsonNode usage = envelope.path("usage");
        if (usage.isMissingNode() || !usage.isObject()) {
            return TokenUsage.NONE;
        }
        return TokenUsage.ofCall(
                usage.path("input_tokens").asLong(0) + usage.path("cache_creation_input_tokens").asLong(0),
                usage.path("cache_read_input_tokens").asLong(0),
                usage.path("output_tokens").asLong(0),
                envelope.path("total_cost_usd").asDouble(0));
    }
}
