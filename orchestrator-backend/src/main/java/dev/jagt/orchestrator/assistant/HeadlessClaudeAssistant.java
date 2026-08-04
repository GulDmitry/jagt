package dev.jagt.orchestrator.assistant;

import dev.jagt.orchestrator.config.AssistantProperties;
import dev.jagt.orchestrator.config.OrchestratorProperties;
import dev.jagt.orchestrator.service.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 */
@Component
public class HeadlessClaudeAssistant implements MasterAssistant {

    private static final Logger log = LoggerFactory.getLogger(HeadlessClaudeAssistant.class);
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
            "projectPath":{"type":"string"},\
            "title":{"type":"string"}},\
            "required":["exists","sourceBranch","projectPath","title"]}""";
    private static final String REVIEW_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "pipelineStatus":{"type":"string"},\
            "comments":{"type":"array","items":{"type":"string"}}},\
            "required":["exists","pipelineStatus","comments"]}""";
    /** The review sweep makes several code-host calls; give it much longer than a single lookup. */
    private static final Duration REVIEW_TIMEOUT = Duration.ofMinutes(6);

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;
    private final AssistantProperties assistant;
    private final JsonMapper mapper = new JsonMapper();

    public HeadlessClaudeAssistant(ProcessRunner processRunner, OrchestratorProperties properties,
                                   AssistantProperties assistant) {
        this.processRunner = processRunner;
        this.properties = properties;
        this.assistant = assistant;
    }

    @Override
    public Optional<TicketFacts> readTicket(String ticketRef) {
        if (ticketRef == null || ticketRef.isBlank()) {
            return Optional.empty();
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
    public Optional<MergeRequestFacts> readMergeRequest(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Optional.empty();
        }
        String prompt = "Fetch the merge/pull request at " + mrUrl + " via the matching code-host MCP tools"
                + " (GitLab MR, GitHub PR, Bitbucket PR — whichever the URL points to). Return exists=true"
                + " with its source branch as sourceBranch, its project path (group/project) as projectPath,"
                + " and its title. If it does not exist, exists=false with empty strings.";
        return ask(prompt, MR_SCHEMA, mrUrl).map(n -> new MergeRequestFacts(
                n.path("exists").asBoolean(false), n.path("sourceBranch").asString(""),
                n.path("projectPath").asString(""), n.path("title").asString("")));
    }

    @Override
    public Optional<ReviewFacts> readReview(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Optional.empty();
        }
        String prompt = "Review sweep of the merge/pull request at " + mrUrl + " via the matching code-host"
                + " MCP tools. Return exists, pipelineStatus (latest pipeline/checks result, e.g. success/failed/none),"
                + " and comments — every UNRESOLVED discussion note (bots like CodeRabbit + humans),"
                + " each as one string \"author (file:line): body\". Empty array if none.";
        return ask(prompt, REVIEW_SCHEMA, mrUrl, REVIEW_TIMEOUT).map(n -> {
            List<String> comments = new ArrayList<>();
            n.path("comments").forEach(c -> comments.add(c.asString("")));
            return new ReviewFacts(n.path("exists").asBoolean(false),
                    n.path("pipelineStatus").asString(""), comments);
        });
    }

    /** Runs one stripped, schema-forced headless Claude call; empty on any failure. */
    private Optional<JsonNode> ask(String prompt, String schema, String label) {
        return ask(prompt, schema, label, TIMEOUT);
    }

    private Optional<JsonNode> ask(String prompt, String schema, String label, Duration timeout) {
        List<String> cmd = new ArrayList<>(List.of(properties.claudeCommand(), prompt, "-p",
                "--setting-sources", assistant.settingSources(), "--json-schema", schema));
        if (assistant.model() != null && !assistant.model().isBlank()) {
            cmd.add("--model");
            cmd.add(assistant.model());
        }
        // Headless `-p` can't answer the permission classifier, which then silently blocks the MCP
        // calls the read needs; an allow-list or a permission mode lifts that gate.
        if (!assistant.allowedTools().isEmpty()) {
            cmd.add("--allowedTools");
            cmd.addAll(assistant.allowedTools());
        } else if (assistant.permissionMode() != null && !assistant.permissionMode().isBlank()) {
            cmd.add("--permission-mode");
            cmd.add(assistant.permissionMode());
        }
        var result = processRunner.run(Path.of(System.getProperty("java.io.tmpdir")), timeout, cmd);
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            log.warn("Headless assistant failed for {} (exit {}): {}", label, result.exitCode(),
                    result.stderr().isBlank() ? result.stdout() : result.stderr());
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readTree(result.stdout()));
        } catch (RuntimeException e) {
            log.warn("Headless assistant returned unparseable JSON for {}: {}", label, e.toString());
            return Optional.empty();
        }
    }
}
