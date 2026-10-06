package dev.jagt.orchestrator.adapter.assistant;

import dev.jagt.orchestrator.adapter.agent.ClaudeProperties;

import dev.jagt.orchestrator.port.Processes;

import dev.jagt.orchestrator.port.MasterAssistant;
import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.protocol.CommandRead;
import dev.jagt.orchestrator.protocol.MergeRequestRead;
import dev.jagt.orchestrator.protocol.ProjectRead;
import dev.jagt.orchestrator.protocol.RuleRead;
import dev.jagt.orchestrator.protocol.ReviewRead;
import dev.jagt.orchestrator.protocol.TicketRead;
import dev.jagt.orchestrator.protocol.TicketText;
import dev.jagt.orchestrator.protocol.TicketSearch;
import dev.jagt.orchestrator.task.AssistantCallKind;
import dev.jagt.orchestrator.task.MergeRequestFacts;
import dev.jagt.orchestrator.task.ReviewFacts;
import dev.jagt.orchestrator.task.RoutingAnswer;
import dev.jagt.orchestrator.task.RoutingQuestion;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /** A model cannot otherwise tell "no such item" from "I never got to look"; the second must not read as the first. */
    private static final String FAILURE_RULE = " Answer failure=\"\" ONLY when the host itself answered you:"
            + " with an answer, or with a no such item — that one is exists=false and empty fields. If ANYTHING"
            + " stopped you from reading it instead — no MCP tool for that host, a tool that errored, an"
            + " authentication or network failure, a denied permission — then failure=<one line naming exactly"
            + " what stopped you, and which tool or server it was>, and NEVER report that as not existing."
            + " A tool asking for a site or cloud id gets one the host's own listing of reachable sites"
            + " answered, never one recalled or guessed.";
    /** The sweep makes several code-host calls, not one lookup. */
    private static final Duration REVIEW_TIMEOUT = Duration.ofMinutes(6);
    private static final int MAX_CAUSE = 400;
    private static final int MAX_RELAYED = 2000;
    /** Mapping text to a command reads nothing and must feel like typing. */
    private static final Duration MAP_TIMEOUT = Duration.ofSeconds(90);
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    private final ProcessRunner processRunner;
    private final ClaudeProperties claude;
    private final McpHealthProbe mcpHealth;
    private final AssistantProperties assistant;
    private final JsonMapper mapper = new JsonMapper();

    @Override
    public Answer<TicketFacts> readTicket(String ticketRef, List<String> corrections) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You read one work item from whichever tracker holds it.</role>\n"
                + "<task>Read the work item identified by \"" + ticketRef + "\" — this is EITHER an issue"
                + " key (e.g. ABC-123) OR a URL to it in some tracker (Jira, GitHub, GitLab, …). Open it"
                + " with the matching MCP tool: if it is a URL, follow the URL — do NOT try to parse a key"
                + " out of it.</task>\n"
                + "<rules>Return exists=true with its canonical issue key as key, its summary as"
                + " title, its project key as trackerProject, its labels, its workflow status as"
                + " trackerStatus and the login or display name it is assigned to as assignee — empty where"
                + " nobody holds it — and its canonical web URL as url"
                + " — the link the item itself reports, never one you assemble. Where the item carries no"
                + " summary of its own, WRITE the title yourself: at most eight words naming what the item"
                + " asks for, from its description. Never answer exists=true with an empty title or an"
                + " empty url." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt + correcting(corrections), TicketRead.SCHEMA.json(), ticketRef,
                AssistantCallKind.TICKET_READ), ticketRef).map(n -> {
            List<String> labels = new ArrayList<>();
            n.path("labels").forEach(l -> labels.add(l.asString("")));
            return TicketFacts.defaults()
                    .withExists(n.path("exists").asBoolean(false))
                    .withKey(n.path("key").asString(""))
                    .withTitle(n.path("title").asString(""))
                    .withTrackerProject(n.path("trackerProject").asString(""))
                    .withLabels(labels)
                    .withUrl(n.path("url").asString(""))
                    .withTrackerStatus(n.path("trackerStatus").asString(""))
                    .withAssignee(n.path("assignee").asString(""));
        });
    }

    @Override
    public Answer<String> readTicketText(String ticketRef) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You copy one work item out of whichever tracker holds it.</role>\n"
                + "<task>Read the work item \"" + ticketRef + "\" with the matching MCP tool; if it is a URL,"
                + " follow it.</task>\n"
                + "<rules>Answer text with its summary, description, acceptance criteria and every comment, each"
                + " under its own heading, VERBATIM: never summarise, shorten or add." + FAILURE_RULE
                + "</rules>\n"
                + "Respond directly, no preamble.";
        Answer<String> read = readable(ask(prompt, TicketText.SCHEMA.json(), ticketRef,
                AssistantCallKind.MASTER_REVIEW), ticketRef).map(n -> n.path("text").asString("").strip());
        return new Answer<>(read.facts().filter(text -> !text.isEmpty()), read.usage());
    }

    @Override
    public Answer<List<String>> findCandidates(String query) {
        if (query == null || query.isBlank()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You list work items from whichever tracker holds them.</role>\n"
                + "<task>Find every work item matching: " + query + ". Use the matching tracker MCP tool's own"
                + " search.</task>\n"
                + "<rules>Return keys: the canonical issue key of each item the search answered with, and"
                + " NOTHING else — no item you were not shown, none you think belongs there. An empty list is"
                + " the right answer where the search matched nothing." + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt, TicketSearch.SCHEMA.json(), query, AssistantCallKind.INTAKE), query)
                .map(n -> {
                    List<String> keys = new ArrayList<>();
                    n.path("keys").forEach(key -> keys.add(key.asString("").strip()));
                    keys.removeIf(String::isEmpty);
                    return List.copyOf(keys);
                });
    }

    @Override
    public Answer<RoutingAnswer> routeProject(RoutingQuestion question) {
        if (question == null || !question.answerable()) {
            return Answer.unavailable();
        }
        TicketFacts item = question.item();
        String prompt = "<role>You place one work item in the repository whose code has to change.</role>\n"
                + "<task>The item is \"" + item.key() + " — " + item.title() + "\". OPEN it with the"
                + " matching tracker MCP tool and read it: its description, its components, its epic,"
                + " whatever says which system changes. Its board says nothing about which repository that"
                + " is.</task>\n"
                + "<rules>The repositories configured here, each with what it is:\n"
                + listed(question.projects())
                + "\nIts labels " + item.labels() + " matched " + question.suggested() + ". That is a"
                + " SUGGESTION and nothing more — whoever filed the item wrote those labels, and a label"
                + " naming a layer rather than a system places nothing. Confirm it against what the item"
                + " actually asks for, and answer a different key where the item says otherwise.\n"
                + written(question.rules())
                + precedents(question.precedents())
                + "Answer project with the ONE key whose repository the work belongs in, or "
                + ProjectRead.NONE + " where the item does not say clearly enough to be sure — a wrong"
                + " repository costs more than a human being asked. reason is one line.\n"
                + "Answer rule with the short phrase that would place the NEXT item like this one, and ONLY"
                + " where placing this one took something no label carried — leave it EMPTY otherwise, an"
                + " obvious placement being worth nothing to write down. Where a rule above already covers"
                + " this case, answer ITS phrase character for character rather than a rewording: two"
                + " wordings of one rule are two rules, and this file is read whole every time."
                + FAILURE_RULE
                + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt, ProjectRead.schemaFor(question.projects().keySet()).json(), item.key(),
                AssistantCallKind.ROUTE), item.key())
                .map(n -> new RoutingAnswer(n.path("project").asString(""), n.path("rule").asString("")));
    }

    private static String listed(Map<String, String> projects) {
        return projects.entrySet().stream().map(entry -> "- " + entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining("\n"));
    }

    /** What the install wrote down itself, which outranks a pattern because a human meant it. */
    private static String written(List<String> rules) {
        return rules.isEmpty() ? ""
                : "Rules this install has written, which outrank everything above:\n"
                        + String.join("\n", rules) + "\n";
    }

    /** Where items like this one were actually worked on, which is the only evidence here nobody wrote down. */
    private static String precedents(List<String> precedents) {
        return precedents.isEmpty() ? ""
                : "Items finished before, and the repository each was done in:\n"
                        + String.join("\n", precedents) + "\n";
    }

    @Override
    public Answer<String> staleRule(String ticketKey, String project, List<String> rules) {
        if (ticketKey == null || project == null || rules == null || rules.isEmpty()) {
            return Answer.unavailable();
        }
        String prompt = "<role>You find the written rule a person has just contradicted.</role>\n"
                + "<task>A person placed the work item " + ticketKey + " in the repository '" + project
                + "' by hand. Read that item with the matching tracker MCP tool.</task>\n"
                + "<rules>These rules are written down here:\n" + String.join("\n", rules) + "\n"
                + "Answer rule with the ONE of them that would have sent this item somewhere else, or "
                + RuleRead.NONE + " where none of them covers it — which is the usual answer, and retiring a"
                + " rule that was never wrong costs more than leaving it. reason is one line."
                + FAILURE_RULE + "</rules>\n"
                + "Respond directly, no preamble.";
        return readable(ask(prompt, RuleRead.schemaFor(rules).json(), ticketKey, AssistantCallKind.ROUTE),
                ticketKey).map(n -> n.path("rule").asString(""));
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
        return readable(ask(prompt, MergeRequestRead.SCHEMA.json(), mrUrl, AssistantCallKind.MR_READ), mrUrl).map(n -> new MergeRequestFacts(
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
                + " error lines of its log, at most 20 lines, cut to what names the fault. A bridge or trigger"
                + " job is never that job: follow it into the pipeline it started and read the one failing there."
                + " Where that log only points at an external analysis (a quality gate, a scanner), read its"
                + " verdict with that tool's own MCP when one is available: the project key, then one line per"
                + " failed condition as \"<metric> <actual> / <threshold>\". Tools an MCP keeps behind a"
                + " discovery or category tool are yours to activate first. A log or verdict you could not read"
                + " fails nothing: pipelineFailure is then the job's name and the lines you did read, never a word"
                + " of your own about what you could not. No timestamps, run"
                + " ids, URLs or durations — one failure read twice must read the same, or every poll relays a"
                + " brief the agent has already answered. Empty string in every other case."
                + " Return openedAt, the request's OWN creation timestamp"
                + " as the host reports it (ISO-8601; empty string if it does not say), and threads — ONE entry"
                + " per DISCUSSION THREAD still awaiting an answer, never one per note: every thread holding a"
                + " resolvable note that is not resolved. A RESOLVED thread is CLOSED — leave it out, whatever"
                + " landed in it since. Read each of them WHOLE with the host's discussion tool and keep EVERY"
                + " note in it, oldest first, bots and humans alike — never drop a note because an earlier one"
                + " already answers it, an answer being what the exchange is. One string per thread: its own"
                + " link (or file:line) on the first line, then one line per note as \"<author>: <body>\"."
                + " Copy each body as the host gives it, cut at its first 60 words — never paraphrased and"
                + " never summarised, a thread read twice having to read the same or every poll relays a brief"
                + " the agent has already answered. Empty array where no thread awaits an answer."
                + FAILURE_RULE
                + " The pipelines are the ONE exception to the failure rule above, and they change nothing"
                + " about exists: a listing you could not get is pipelineStatus=unknown with failure=\"\"."
                + "</rules>\n"
                + "Respond directly, no preamble.";
        Answer<JsonNode> answer = ask(prompt, ReviewRead.SCHEMA.json(), mrUrl, AssistantCallKind.REVIEW_SWEEP,
                REVIEW_TIMEOUT);
        return readable(answer, mrUrl).map(n -> {
            List<String> threads = new ArrayList<>();
            n.path("threads").forEach(t -> threads.add(cappedTail(t.asString(""))));
            return new ReviewFacts(n.path("exists").asBoolean(false), n.path("approved").asBoolean(false),
                    n.path("pipelineStatus").asString(""), capped(n.path("pipelineFailure").asString("")),
                    threads, HostStamp.epochMillis(n.path("openedAt").asString("")));
        });
    }

    /** What the last answer got wrong, appended so the next one is not the same answer. */
    private static String correcting(List<String> corrections) {
        return corrections.isEmpty() ? ""
                : "\n\nYour last answer was refused:\n- " + String.join("\n- ", corrections)
                + "\nAnswer again, fixing every one of those.";
    }

    /** Relayed into a worktree file, so a host that answered with a whole build log is cut here. */
    private static String capped(String excerpt) {
        String trimmed = excerpt.strip();
        return trimmed.length() <= MAX_RELAYED ? trimmed : trimmed.substring(0, MAX_RELAYED) + "…";
    }

    /** A thread runs oldest note first, and the round answers its NEWEST: an over-long one loses its head. */
    private static String cappedTail(String thread) {
        String trimmed = thread.strip();
        return trimmed.length() <= MAX_RELAYED
                ? trimmed
                : "…" + trimmed.substring(trimmed.length() - MAX_RELAYED);
    }

    /** A read that reports what stopped it comes back unreadable, never as an answer with empty facts. */
    private Answer<JsonNode> readable(Answer<JsonNode> answer, String label) {
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
        return ask(prompt, CommandRead.SCHEMA.json(), "command mapping", AssistantCallKind.COMMAND_MAP, MAP_TIMEOUT)
                .map(n -> new CommandProposal(n.path("command").asString(""), n.path("task").asString(""),
                        n.path("ticket").asString(""), n.path("reason").asString("")));
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label, AssistantCallKind kind) {
        return ask(prompt, schema, label, kind, TIMEOUT);
    }

    private Answer<JsonNode> ask(String prompt, String schema, String label, AssistantCallKind kind,
                                 Duration timeout) {
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

    /** A failed call carries no envelope: asking again would not mend it. */
    private record Reply(JsonNode envelope, TokenUsage usage) {

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
                "--exclude-dynamic-system-prompt-sections"));
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
            log.atWarn().setMessage("assistant call did not return")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", e.toString())
                    .addKeyValue("limit", timeout)
                    .addKeyValue("effect", "token cost unmeasured")
                    .log();
            return new Reply(null, TokenUsage.NONE);
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
            log.atWarn().setMessage("assistant call failed")
                    .addKeyValue("ref", label)
                    .addKeyValue("exit", result.exitCode())
                    .addKeyValue("cause", oneLine(result.stderr().isBlank() ? result.stdout() : result.stderr()))
                    .log();
            return new Reply(null, usage);
        }
        if (envelope.path("is_error").asBoolean(false)) {
            log.atWarn().setMessage("assistant call errored")
                    .addKeyValue("ref", label)
                    .addKeyValue("cause", envelope.path("result").asString(""))
                    .log();
            return new Reply(null, usage);
        }
        return new Reply(envelope, usage);
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
