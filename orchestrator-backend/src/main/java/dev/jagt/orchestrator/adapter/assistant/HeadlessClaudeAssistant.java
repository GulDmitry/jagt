package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.port.MasterAssistant;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.adapter.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@link MasterAssistant} via a one-shot headless Claude. Portable by design: it hardcodes NO MCP
 * server or path — {@code --setting-sources user,project,local} makes the child inherit the human's
 * own MCP config (so whatever issue-tracker / code-host MCP the human already has, this call gets), and
 * {@code --json-schema} forces a deterministic JSON answer. Runs from the temp dir so only the
 * human's user-level MCP loads (no jagt project MCP), keeping the context — and tokens — small.
 * <p>An install that would rather not depend on whichever servers the human has installed today declares
 * them itself; only the SERVERS stop being inherited, so a declared file's {@code ${ENV}} placeholders and
 * the model still resolve as before. Declared servers lose their plugin scope in tool names, so an
 * allow-list written for the inherited spelling stops matching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HeadlessClaudeAssistant implements MasterAssistant {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final String TICKET_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "key":{"type":"string"},\
            "title":{"type":"string"},\
            "trackerProject":{"type":"string"},\
            "labels":{"type":"array","items":{"type":"string"}},\
            "url":{"type":"string"}},\
            "required":["exists","key","title","trackerProject","labels","url"]}""";
    private static final String MR_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "sourceBranch":{"type":"string"},\
            "targetBranch":{"type":"string"},\
            "title":{"type":"string"}},\
            "required":["exists","sourceBranch","targetBranch","title"]}""";
    private static final String REVIEW_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "approved":{"type":"boolean"},\
            "pipelineStatus":{"type":"string"},\
            "comments":{"type":"array","items":{"type":"string"}}},\
            "required":["exists","approved","pipelineStatus","comments"]}""";
    private static final String COMMAND_SCHEMA = """
            {"type":"object","properties":{\
            "command":{"type":"string"},\
            "task":{"type":"string"},\
            "ticket":{"type":"string"},\
            "reason":{"type":"string"}},\
            "required":["command","task","ticket","reason"]}""";
    /** The review sweep makes several code-host calls; give it much longer than a single lookup. */
    private static final Duration REVIEW_TIMEOUT = Duration.ofMinutes(6);
    /** Mapping text to a command reads nothing and must feel like typing — a slow answer is worse than none. */
    private static final Duration MAP_TIMEOUT = Duration.ofSeconds(90);

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;
    private final AssistantProperties assistant;
    private final JsonMapper mapper = new JsonMapper();

    @Override
    public Answer<TicketFacts> readTicket(String ticketRef) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "Read the work item identified by \"" + ticketRef + "\" — this is EITHER an issue"
                + " key (e.g. ABC-123) OR a URL to it in some tracker (Jira, GitHub, GitLab, …). Open it"
                + " with the matching MCP tool: if it is a URL, follow the URL — do NOT try to parse a key"
                + " out of it. Return exists=true with its canonical issue key as key, its summary as"
                + " title, its project key as trackerProject, its labels, and its canonical web URL as url"
                + " (the human-facing link to the item; empty string if the tracker has none). If it"
                + " cannot be read, exists=false with empty strings and array.";
        return ask(prompt, TICKET_SCHEMA, ticketRef).map(n -> {
            List<String> labels = new ArrayList<>();
            n.path("labels").forEach(l -> labels.add(l.asString("")));
            return new TicketFacts(n.path("exists").asBoolean(false), n.path("key").asString(""),
                    n.path("title").asString(""), n.path("trackerProject").asString(""), labels,
                    n.path("url").asString(""));
        });
    }

    @Override
    public Answer<MergeRequestFacts> readMergeRequest(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Answer.unavailable();
        }
        String prompt = "Fetch the merge/pull request at " + mrUrl + " via the matching code-host MCP tools"
                + " (GitLab MR, GitHub PR, Bitbucket PR — whichever the URL points to). Return exists=true"
                + " with its source branch as sourceBranch, the branch it merges INTO as targetBranch, and its"
                + " title. If it does not exist, exists=false with empty strings.";
        return ask(prompt, MR_SCHEMA, mrUrl).map(n -> new MergeRequestFacts(
                n.path("exists").asBoolean(false), n.path("sourceBranch").asString(""),
                n.path("targetBranch").asString(""), n.path("title").asString("")));
    }

    @Override
    public Answer<ReviewFacts> readReview(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Answer.unavailable();
        }
        String prompt = "Review sweep of the merge/pull request at " + mrUrl + " via the matching code-host"
                + " MCP tools. Return exists, approved (true only if the request is actually APPROVED by a human"
                + " reviewer — not merely mergeable), pipelineStatus (latest pipeline/checks result, e.g."
                + " success/failed/none), and comments — every UNRESOLVED discussion note (bots like"
                + " CodeRabbit + humans), each as one string \"author (file:line): body\". Empty array if none.";
        return ask(prompt, REVIEW_SCHEMA, mrUrl, REVIEW_TIMEOUT).map(n -> {
            List<String> comments = new ArrayList<>();
            n.path("comments").forEach(c -> comments.add(c.asString("")));
            return new ReviewFacts(n.path("exists").asBoolean(false), n.path("approved").asBoolean(false),
                    n.path("pipelineStatus").asString(""), comments);
        });
    }

    @Override
    public Answer<CommandProposal> mapCommand(String text, String context) {
        if (text == null || text.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "Map this operator request onto EXACTLY ONE command of the tool below.\n\nREQUEST: "
                + text + "\n\n" + context + "\n\nAnswer with the command word, the task it applies to (its"
                + " id or alias, copied verbatim from the list — never invented), the ticket reference when"
                + " the command is `do`, and a reason. Leave a field as an empty string when it does not"
                + " apply. If the request does not clearly match one command and one task, answer"
                + " command=\"none\" and put the ambiguity in reason. Do NOT guess between two tasks:"
                + " ambiguity is a `none`. Respond directly.";
        // No MCP at all: this is text -> command, so a tool call could only be a mistake (and every server
        // loaded would be paid for in context on a call that is meant to be the cheapest one jagt makes).
        return ask(prompt, COMMAND_SCHEMA, "command mapping", MAP_TIMEOUT, false)
                .map(n -> new CommandProposal(n.path("command").asString(""), n.path("task").asString(""),
                        n.path("ticket").asString(""), n.path("reason").asString("")));
    }

    /** Runs one stripped, schema-forced headless Claude call; empty facts on any failure, cost always. */
    private Answer<JsonNode> ask(String prompt, String schema, String label) {
        return ask(prompt, schema, label, TIMEOUT, true);
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label, Duration timeout) {
        return ask(prompt, schema, label, timeout, true);
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label, Duration timeout, boolean withMcp) {
        List<String> cmd = new ArrayList<>(List.of(properties.claudeCommand(), prompt, "-p",
                "--json-schema", schema,
                // The JSON envelope wraps the answer together with the call's token usage and cost, so the
                // spend is measurable. Plain text output would hide the price of every read.
                "--output-format", "json"));
        if (!withMcp) {
            // An empty --mcp-config with --strict-mcp-config: no servers, no tool schemas, nothing to approve.
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", "{\"mcpServers\":{}}"));
        } else if (!assistant.mcpConfig().isBlank()) {
            cmd.addAll(List.of("--strict-mcp-config", "--mcp-config", assistant.mcpConfig(),
                    "--setting-sources", assistant.settingSources()));
        } else {
            cmd.addAll(List.of("--setting-sources", assistant.settingSources()));
        }
        if (assistant.model() != null && !assistant.model().isBlank()) {
            cmd.add("--model");
            cmd.add(assistant.model());
        }
        // Headless `-p` can't answer the permission classifier, which then silently blocks the MCP
        // calls the read needs; an allow-list or a permission mode lifts that gate. A call with no MCP has
        // nothing to gate, so it stays off that path entirely.
        if (!withMcp) {
            log.debug("Stripped (no-MCP) assistant call for {}", label);
        } else if (!assistant.allowedTools().isEmpty()) {
            cmd.add("--allowedTools");
            cmd.addAll(assistant.allowedTools());
        } else if (assistant.permissionMode() != null && !assistant.permissionMode().isBlank()) {
            cmd.add("--permission-mode");
            cmd.add(assistant.permissionMode());
        }
        Processes.Result result;
        try {
            result = processRunner.run(Path.of(System.getProperty("java.io.tmpdir")), timeout, cmd);
        } catch (RuntimeException e) {
            // A timeout kills the CLI, so there is no envelope and no number: the tokens it already burned
            // are unknowable, not zero. Say so in the log — silently returning would understate the spend —
            // and degrade to an empty answer so the caller reports an error instead of throwing at the human.
            log.warn("Headless assistant call for {} never returned ({}) — it was killed after {}, so its"
                    + " token cost is UNMEASURED and missing from the totals", label, e.getMessage(), timeout);
            return new Answer<>(Optional.empty(), TokenUsage.NONE);
        }
        JsonNode envelope = parseEnvelope(result.stdout(), label);
        // The cost is reported whatever the outcome: a call that errored or came back unusable was still
        // paid for, and dropping it would make the setup that fails most look like the cheapest.
        TokenUsage usage = usageOf(envelope);
        if (usage.isNone()) {
            log.warn("Headless assistant call for {} produced no usage data — its cost is not accounted"
                    + " for (the CLI aborted before reaching a model, or reported nothing)", label);
        }
        if (result.exitCode() != 0 || envelope == null) {
            log.warn("Headless assistant failed for {} (exit {}): {}", label, result.exitCode(),
                    result.stderr().isBlank() ? result.stdout() : result.stderr());
            return new Answer<>(Optional.empty(), usage);
        }
        if (envelope.path("is_error").asBoolean(false)) {
            log.warn("Headless assistant reported an error for {}: {}", label,
                    envelope.path("result").asString(""));
            return new Answer<>(Optional.empty(), usage);
        }
        return new Answer<>(answerOf(envelope, label), usage);
    }

    private JsonNode parseEnvelope(String stdout, String label) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(stdout);
        } catch (RuntimeException e) {
            log.warn("Headless assistant returned unparseable JSON for {}: {}", label, e.toString());
            return null;
        }
    }

    /**
     * The schema-validated answer: {@code structured_output} when the CLI already parsed it, else the
     * {@code result} string, which then holds the same JSON verbatim.
     */
    private Optional<JsonNode> answerOf(JsonNode envelope, String label) {
        JsonNode structured = envelope.path("structured_output");
        if (structured.isObject()) {
            return Optional.of(structured);
        }
        String raw = envelope.path("result").asString("");
        if (raw.isBlank()) {
            log.warn("Headless assistant returned no answer for {}", label);
            return Optional.empty();
        }
        JsonNode answer = parseEnvelope(raw, label);
        return Optional.ofNullable(answer);
    }

    /**
     * One call's cost out of the envelope. Fresh input = prompt + cache WRITES (both billed at input
     * rates); cache reads are counted apart because they are far cheaper. A missing usage block (older CLI,
     * unparseable output) yields {@link TokenUsage#NONE} rather than a fabricated number.
     */
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
