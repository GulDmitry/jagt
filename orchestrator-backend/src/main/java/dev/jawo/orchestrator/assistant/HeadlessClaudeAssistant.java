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
        List<String> cmd = new ArrayList<>(List.of(properties.claudeCommand(), prompt, "-p",
                "--setting-sources", settingSources, "--json-schema", TICKET_SCHEMA));
        if (model != null && !model.isBlank()) {
            cmd.add("--model");
            cmd.add(model);
        }
        var result = processRunner.run(Path.of(System.getProperty("java.io.tmpdir")), TIMEOUT, cmd);
        if (result.exitCode() != 0 || result.stdout().isBlank()) {
            log.warn("Headless assistant failed for {} (exit {}): {}", ticketKey, result.exitCode(),
                    result.stderr().isBlank() ? result.stdout() : result.stderr());
            return Optional.empty();
        }
        return parse(result.stdout(), ticketKey);
    }

    private Optional<TicketFacts> parse(String json, String ticketKey) {
        try {
            JsonNode n = mapper.readTree(json);
            List<String> labels = new ArrayList<>();
            n.path("labels").forEach(l -> labels.add(l.asString("")));
            return Optional.of(new TicketFacts(n.path("exists").asBoolean(false),
                    n.path("title").asString(""), n.path("jiraProject").asString(""), labels));
        } catch (RuntimeException e) {
            log.warn("Headless assistant returned unparseable JSON for {}: {}", ticketKey, e.toString());
            return Optional.empty();
        }
    }
}
