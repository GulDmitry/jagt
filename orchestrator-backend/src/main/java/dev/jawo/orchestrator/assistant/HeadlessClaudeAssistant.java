package dev.jawo.orchestrator.assistant;

import dev.jawo.orchestrator.config.OrchestratorProperties;
import dev.jawo.orchestrator.service.ProcessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * {@link MasterAssistant} via a one-shot headless Claude. Portable by design: it hardcodes NO MCP
 * server or path — {@code --setting-sources user,project,local} makes the child inherit the human's
 * own MCP config (so whatever Jira/GitLab MCP the human already has, this call gets), and
 * {@code --json-schema} forces a deterministic JSON answer. Runs from the temp dir so only the
 * human's user-level MCP loads (no jawo project MCP), keeping the context — and tokens — small.
 */
@Component
public class HeadlessClaudeAssistant implements MasterAssistant {

    private static final Logger log = LoggerFactory.getLogger(HeadlessClaudeAssistant.class);
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");
    private static final String TICKET_SCHEMA = """
            {"type":"object","properties":{\
            "exists":{"type":"boolean"},\
            "title":{"type":"string"},\
            "jiraProject":{"type":"string"},\
            "labels":{"type":"array","items":{"type":"string"}}},\
            "required":["exists","title","jiraProject","labels"]}""";
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
    /** The review sweep makes several GitLab calls; give it much longer than a single lookup. */
    private static final Duration REVIEW_TIMEOUT = Duration.ofMinutes(6);

    private final ProcessRunner processRunner;
    private final OrchestratorProperties properties;
    private final String settingSources;
    private final String model;
    private final JsonMapper mapper = new JsonMapper();

    public HeadlessClaudeAssistant(ProcessRunner processRunner, OrchestratorProperties properties,
                                   @Value("${orchestrator.assistant.setting-sources:user,project,local}") String settingSources,
                                   @Value("${orchestrator.assistant.model:}") String model) {
        this.processRunner = processRunner;
        this.properties = properties;
        this.settingSources = settingSources;
        this.model = model;
    }

    @Override
    public Optional<TicketFacts> readTicket(String ticketKey) {
        if (ticketKey == null || !SAFE_KEY.matcher(ticketKey).matches()) {
            return Optional.empty();
        }
        String prompt = "Fetch Jira issue " + ticketKey + " via your Jira MCP tools. Return exists=true"
                + " with its summary as title, its project key as jiraProject, and its labels. If it does"
                + " not exist, exists=false with empty strings and array.";
        return ask(prompt, TICKET_SCHEMA, ticketKey).map(n -> {
            List<String> labels = new ArrayList<>();
            n.path("labels").forEach(l -> labels.add(l.asString("")));
            return new TicketFacts(n.path("exists").asBoolean(false),
                    n.path("title").asString(""), n.path("jiraProject").asString(""), labels);
        });
    }

    @Override
    public Optional<MergeRequestFacts> readMergeRequest(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Optional.empty();
        }
        String prompt = "Fetch the GitLab merge request at " + mrUrl + " via your GitLab MCP tools. Return"
                + " exists=true with its source branch as sourceBranch, its project path (group/project)"
                + " as projectPath, and its title. If it does not exist, exists=false with empty strings.";
        return ask(prompt, MR_SCHEMA, mrUrl).map(n -> new MergeRequestFacts(
                n.path("exists").asBoolean(false), n.path("sourceBranch").asString(""),
                n.path("projectPath").asString(""), n.path("title").asString("")));
    }

    @Override
    public Optional<ReviewFacts> readReview(String mrUrl) {
        if (mrUrl == null || !mrUrl.startsWith("http")) {
            return Optional.empty();
        }
        String prompt = "Review sweep of the GitLab merge request at " + mrUrl + " via your GitLab MCP"
                + " tools. Return exists, pipelineStatus (latest pipeline result, e.g. success/failed/none),"
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
                "--setting-sources", settingSources, "--json-schema", schema));
        if (model != null && !model.isBlank()) {
            cmd.add("--model");
            cmd.add(model);
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
