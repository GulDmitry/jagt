package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.port.MasterAssistant;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.TicketFacts;
import dev.jagt.orchestrator.task.TokenUsage;
import dev.jagt.orchestrator.adapter.HostStamp;
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
 * Hardcodes no MCP server or path: {@code --setting-sources} makes the child inherit the human's own MCP
 * config, and running from the temp dir loads only their user-level servers. Servers declared here instead lose
 * their plugin scope in tool names, so an allow-list written for the inherited spelling stops matching.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HeadlessClaudeAssistant implements MasterAssistant {

    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final String TICKET_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "failure":{"type":"string"},\
            "key":{"type":"string"},\
            "title":{"type":"string"},\
            "trackerProject":{"type":"string"},\
            "labels":{"type":"array","items":{"type":"string"}},\
            "url":{"type":"string"}},\
            "required":["exists","failure","key","title","trackerProject","labels","url"]}""";
    private static final String MR_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "failure":{"type":"string"},\
            "sourceBranch":{"type":"string"},\
            "targetBranch":{"type":"string"},\
            "title":{"type":"string"}},\
            "required":["exists","failure","sourceBranch","targetBranch","title"]}""";
    /** {@code pipelineStatus} is an enum, not free text: it is read by keyword, so a sentence carrying "fail" is one. */
    private static final String REVIEW_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "failure":{"type":"string"},\
            "approved":{"type":"boolean"},\
            "pipelineStatus":{"type":"string","enum":["success","failed","running","none","unknown"]},\
            "pipelineFailure":{"type":"string"},\
            "openedAt":{"type":"string"},\
            "comments":{"type":"array","items":{"type":"string"}}},\
            "required":["exists","failure","approved","pipelineStatus","pipelineFailure","openedAt",\
            "comments"]}""";
    private static final String COMMAND_SCHEMA = """
            {"type":"object","properties":{\
            "command":{"type":"string"},\
            "task":{"type":"string"},\
            "ticket":{"type":"string"},\
            "reason":{"type":"string"}},\
            "required":["command","task","ticket","reason"]}""";
    /** A model cannot otherwise tell "no such item" from "I never got to look"; the second must not read as the first. */
    private static final String FAILURE_RULE = " Answer failure=\"\" ONLY when the host itself answered you:"
            + " with an answer, or with a no such item — that one is exists=false and empty fields. If ANYTHING"
            + " stopped you from reading it instead — no MCP tool for that host, a tool that errored, an"
            + " authentication or network failure, a denied permission — then failure=<one line naming exactly"
            + " what stopped you, and which tool or server it was>, and NEVER report that as not existing.";
    /** The sweep makes several code-host calls, not one lookup. */
    private static final Duration REVIEW_TIMEOUT = Duration.ofMinutes(6);
    private static final int MAX_CAUSE = 400;
    private static final int MAX_CHECKS_DETAIL = 2000;
    /** Mapping text to a command reads nothing and must feel like typing. */
    private static final Duration MAP_TIMEOUT = Duration.ofSeconds(90);

    private final ProcessRunner processRunner;
    private final ClaudeProperties claude;
    private final McpHealthProbe mcpHealth;
    private final AssistantProperties assistant;
    private final JsonMapper mapper = new JsonMapper();

    @Override
    public Answer<TicketFacts> readTicket(String ticketRef) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You read one work item from whichever tracker holds it.</role>\n"
                + "<task>Read the work item identified by \"" + ticketRef + "\" — this is EITHER an issue"
                + " key (e.g. ABC-123) OR a URL to it in some tracker (Jira, GitHub, GitLab, …). Open it"
                + " with the matching MCP tool: if it is a URL, follow the URL — do NOT try to parse a key"
                + " out of it.</task>\n"
                + "<rules>Return exists=true with its canonical issue key as key, its summary as"
                + " title, its project key as trackerProject, its labels, and its canonical web URL as url"
                + " — the link the item itself reports, never one you assemble. Where the item carries no"
                + " summary of its own, WRITE the title yourself: at most eight words naming what the item"
                + " asks for, from its description. Never answer exists=true with an empty title or an"
                + " empty url." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt, TICKET_SCHEMA, ticketRef), ticketRef).map(n -> {
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
        String prompt = "<role>You read one merge/pull request from whichever code host holds it.</role>\n"
                + "<task>Fetch the merge/pull request at " + mrUrl + " via the matching code-host MCP tools"
                + " (GitLab MR, GitHub PR, Bitbucket PR — whichever the URL points to).</task>\n"
                + "<rules>Return exists=true"
                + " with its source branch as sourceBranch, the branch it merges INTO as targetBranch, and its"
                + " title." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt, MR_SCHEMA, mrUrl), mrUrl).map(n -> new MergeRequestFacts(
                n.path("exists").asBoolean(false), n.path("sourceBranch").asString(""),
                n.path("targetBranch").asString(""), n.path("title").asString("")));
    }

    @Override
    public Answer<ReviewFacts> readReview(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Answer.unavailable();
        }
        String prompt = "<role>You sweep one merge/pull request for its review state.</role>\n"
                + "<task>Review sweep of the merge/pull request at " + mrUrl + " via the matching code-host"
                + " MCP tools.</task>\n"
                + "<rules>Return exists; approved (true only if the request is actually APPROVED by a human"
                + " reviewer — not merely mergeable); pipelineStatus, the CI PIPELINE's own latest result: LIST"
                + " this request's pipelines (or its head commit's check runs) with the host's own tool and read"
                + " the newest one. Exactly one of success | failed | running | none | unknown,"
                + " never the merge status (mergeable, can_be_merged) and never a review bot's verdict; none ONLY"
                + " when that listing came back EMPTY, and unknown where you could not list them at all."
                + " Return pipelineFailure ONLY where pipelineStatus is failed: the failing job's name and the"
                + " error lines of its log, at most 20 lines, cut to what names the fault. No timestamps, run"
                + " ids, URLs or durations — one failure read twice must read the same, or every poll relays a"
                + " brief the agent has already answered. Empty string in every other case."
                + " Return openedAt, the request's OWN creation timestamp"
                + " as the host reports it (ISO-8601; empty string if it does not say), and comments — every"
                + " UNRESOLVED discussion note (bots and humans alike), each as one string"
                + " \"author (file:line): body\". Empty array if none." + FAILURE_RULE
                + " The pipelines are the ONE exception to the failure rule above, and they change nothing"
                + " about exists: a listing you could not get is pipelineStatus=unknown with failure=\"\"."
                + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt, REVIEW_SCHEMA, mrUrl, REVIEW_TIMEOUT), mrUrl).map(n -> {
            List<String> comments = new ArrayList<>();
            n.path("comments").forEach(c -> comments.add(c.asString("")));
            return new ReviewFacts(n.path("exists").asBoolean(false), n.path("approved").asBoolean(false),
                    n.path("pipelineStatus").asString(""), capped(n.path("pipelineFailure").asString("")),
                    comments, HostStamp.epochMillis(n.path("openedAt").asString("")));
        });
    }

    /** The excerpt is relayed into a worktree file, so a host that answered with the whole log is cut here. */
    private static String capped(String detail) {
        String trimmed = detail.strip();
        return trimmed.length() <= MAX_CHECKS_DETAIL ? trimmed : trimmed.substring(0, MAX_CHECKS_DETAIL) + "…";
    }

    /** A read that reports what stopped it comes back unreadable, never as an answer with empty facts. */
    private Answer<JsonNode> readable(Answer<JsonNode> answer, String label) {
        String failure = answer.facts().map(n -> n.path("failure").asString("")).orElse("").trim();
        if (failure.isEmpty()) {
            return answer;
        }
        log.atError().setMessage("read failed")
                .addKeyValue("ref", label)
                .addKeyValue("cause", failure)
                .log();
        return new Answer<>(Optional.empty(), answer.usage());
    }

    @Override
    public Optional<List<String>> brokenMcpServers() {
        return mcpHealth.brokenServers();
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
        // Text -> command reads nothing, so a tool call could only be a mistake, and each loaded server costs context.
        return ask(prompt, COMMAND_SCHEMA, "command mapping", MAP_TIMEOUT, false)
                .map(n -> new CommandProposal(n.path("command").asString(""), n.path("task").asString(""),
                        n.path("ticket").asString(""), n.path("reason").asString("")));
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label) {
        return ask(prompt, schema, label, TIMEOUT, true);
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label, Duration timeout) {
        return ask(prompt, schema, label, timeout, true);
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label, Duration timeout, boolean withMcp) {
        List<String> cmd = new ArrayList<>(List.of(claude.command(), prompt, "-p",
                "--json-schema", schema,
                // The envelope carries the call's token usage and cost alongside the answer.
                "--output-format", "json"));
        if (!withMcp) {
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
        // Headless `-p` cannot answer the permission classifier, which then silently blocks the MCP calls a
        // read needs; an allow-list or a permission mode lifts that gate. No MCP means nothing to gate.
        if (!withMcp) {
            log.atDebug().setMessage("assistant call without mcp")
                    .addKeyValue("ref", label)
                    .log();
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
            // A timeout kills the CLI: no envelope, so the tokens already burned are unknowable, not zero.
            log.atError().setMessage("assistant call did not return")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("limit", timeout)
                    .addKeyValue("effect", "token cost unmeasured")
                    .log();
            return new Answer<>(Optional.empty(), TokenUsage.NONE);
        }
        JsonNode envelope = parseEnvelope(result.stdout(), label);
        // Reported whatever the outcome: a call that errored or came back unusable was still paid for.
        TokenUsage usage = usageOf(envelope);
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
            log.atError().setMessage("assistant call failed")
                    .addKeyValue("ref", label)
                    .addKeyValue("exit", result.exitCode())
                    .addKeyValue("cause", oneLine(result.stderr().isBlank() ? result.stdout() : result.stderr()))
                    .log();
            return new Answer<>(Optional.empty(), usage);
        }
        if (envelope.path("is_error").asBoolean(false)) {
            log.atError().setMessage("assistant call errored")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", envelope.path("result").asString(""))
                    .log();
            return new Answer<>(Optional.empty(), usage);
        }
        return new Answer<>(answerOf(envelope, label), usage);
    }

    /** `%kvp` quotes a value but escapes nothing, so a multi-line stderr would break the console line apart. */
    private static String oneLine(String value) {
        String flat = value == null ? "" : value.replaceAll("\\s+", " ").replace('"', '\'').strip();
        return flat.length() <= MAX_CAUSE ? flat : flat.substring(0, MAX_CAUSE) + "…";
    }

    private JsonNode parseEnvelope(String stdout, String label) {
        if (stdout == null || stdout.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(stdout);
        } catch (RuntimeException e) {
            log.atError().setMessage("assistant json unparseable")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", e.toString())
                    .log();
            return null;
        }
    }

    /** {@code structured_output} when the CLI already parsed it, else {@code result}, holding the same JSON. */
    private Optional<JsonNode> answerOf(JsonNode envelope, String label) {
        JsonNode structured = envelope.path("structured_output");
        if (structured.isObject()) {
            return Optional.of(structured);
        }
        String raw = envelope.path("result").asString("");
        if (raw.isBlank()) {
            log.atError().setMessage("assistant answer empty")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", "result blank")
                    .log();
            return Optional.empty();
        }
        JsonNode answer = parseEnvelope(raw, label);
        return Optional.ofNullable(answer);
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
