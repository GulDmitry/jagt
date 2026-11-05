package dev.jagt.orchestrator.adapter.agent;

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

import java.nio.file.Path;
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
    private static final String PLAN_AGENT = "{\"default_agent\":\"plan\"}";
    private static final JsonMapper JSON = new JsonMapper();

    private final SessionBrief brief;
    private final OpenCodeProperties opencode;
    private final McpEndpoint mcp;
    private final ConfigService config;

    @Override
    public List<Problem> problems() {
        if (config.load().agent().sandboxOrDefault().enabledOrDefault()) {
            return List.of(new Problem(CLI_KEY, CLI_KEY + "=opencode has no sandbox: set"
                    + " orchestrator.agent.sandbox.enabled: false."));
        }
        return List.of();
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
        OpenCodeProvider.write(root, opencode);
        write(worktree.path().resolve(CONFIG_FILE), JSON.writerWithDefaultPrettyPrinter().writeValueAsString(root));
    }
}
