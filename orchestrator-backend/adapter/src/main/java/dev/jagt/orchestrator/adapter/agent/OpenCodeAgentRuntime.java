package dev.jagt.orchestrator.adapter.agent;

import dev.jagt.orchestrator.adapter.Executables;
import dev.jagt.orchestrator.config.SessionBrief;
import dev.jagt.orchestrator.port.AgentWorktree;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.Step;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * OpenCode reads {@code AGENTS.md} natively and reaches jagt as a remote MCP server carrying the caller header.
 * {@code OPENCODE_CONFIG} points at a jagt-owned file inside the worktree and the project's own config is disabled,
 * so a repository's {@code opencode.json} is neither read nor overwritten.
 */
@Component
@ConditionalOnProperty(name = "orchestrator.agent.cli", havingValue = "opencode")
@RequiredArgsConstructor
public class OpenCodeAgentRuntime extends AbstractAgentRuntime implements StartupCheck {

    private static final String CONFIG_FILE = ".jagt/opencode/opencode.json";
    private static final String CLI_KEY = "orchestrator.agent.cli";
    private static final String ENDPOINT_KEY = "orchestrator.opencode.endpoint";
    private static final String PLAN_AGENT = "{\"default_agent\":\"plan\"}";
    private static final JsonMapper JSON = new JsonMapper();

    private final SessionBrief brief;
    private final OpenCodeProperties opencode;
    private final McpEndpoint mcp;
    private final ConfigService config;

    @Override
    public List<Problem> problems() {
        List<Problem> problems = new ArrayList<>();
        if (Executables.unresolved(Executables.resolve(opencode.command()))) {
            problems.add(new Problem("orchestrator.opencode.command",
                    "orchestrator.agent.cli=opencode needs '" + opencode.command() + "' on PATH."));
        }
        if (config.load().agent().sandboxOrDefault().enabledOrDefault()) {
            problems.add(new Problem(CLI_KEY, CLI_KEY + "=opencode has no sandbox: set"
                    + " orchestrator.agent.sandbox.enabled: false."));
        }
        if (opencode.hasEndpoint() && !httpAddress(opencode.endpoint())) {
            problems.add(new Problem(ENDPOINT_KEY, ENDPOINT_KEY + ": '" + opencode.endpoint()
                    + "' is not an http(s) address"));
        }
        if (opencode.hasEndpoint() && opencode.model().isEmpty()) {
            problems.add(new Problem("orchestrator.opencode.model",
                    "orchestrator.opencode.model: an endpoint needs the model name to send"));
        }
        return problems;
    }

    @Override
    public String displayName() {
        return "OpenCode";
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode) {
        return launchCommand(worktree, planMode, "", brief.line(Step.START));
    }

    @Override
    public String launchCommand(Path worktree, boolean planMode, String model, @Nullable String prompt) {
        return "OPENCODE_CONFIG=" + shellQuote(worktree.resolve(CONFIG_FILE).toString())
                + " OPENCODE_DISABLE_PROJECT_CONFIG=1"
                + (planMode ? " OPENCODE_CONFIG_CONTENT=" + shellQuote(PLAN_AGENT) : "")
                + " " + opencode.command() + " --standalone --prompt " + shellQuote(prompt);
    }

    @Override
    protected void wireAgent(AgentWorktree worktree) {
        ObjectNode root = JSON.createObjectNode();
        root.put("$schema", "https://opencode.ai/config.json");
        root.put("autoupdate", false);
        root.put("share", "disabled");
        ObjectNode permission = root.putObject("permission");
        permission.put("edit", "allow");
        permission.put("bash", "allow");
        permission.put("webfetch", "allow");
        ObjectNode server = root.putObject("mcp").putObject("jagt-orchestrator");
        server.put("type", "remote");
        server.put("url", mcp.url());
        server.putObject("headers").put(McpEndpoint.CALLER_HEADER, McpEndpoint.callerHeaderValue(worktree.path()));
        server.put("oauth", false);
        server.put("enabled", true);
        if (opencode.hasEndpoint()) {
            ObjectNode local = root.putObject("provider").putObject("local");
            local.put("npm", "@ai-sdk/openai-compatible");
            local.put("name", "local");
            local.putObject("options").put("baseURL", opencode.endpoint());
            local.putObject("models").putObject(opencode.model());
            root.put("model", "local/" + opencode.model());
        }
        write(worktree.path().resolve(CONFIG_FILE), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }

    private static boolean httpAddress(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
