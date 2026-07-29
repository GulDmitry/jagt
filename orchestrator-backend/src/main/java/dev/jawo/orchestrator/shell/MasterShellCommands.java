package dev.jawo.orchestrator.shell;

import dev.jawo.orchestrator.mcp.OrchestratorTools;
import dev.jawo.orchestrator.service.ConfigService;
import dev.jawo.orchestrator.service.DashboardRenderer;
import org.springframework.shell.core.command.annotation.Argument;
import org.springframework.shell.core.command.annotation.Command;
import org.springframework.shell.core.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * The Master control terminal as deterministic Spring Shell commands: they parse a fixed grammar and
 * call {@link OrchestratorTools} directly (same JVM — no LLM, no MCP round-trip, no drift). Every
 * command prints its result then the dashboard, so the terminal always ends on current state.
 *
 * <p>Callers here are the Master (never a sub-agent), so the {@code callerTaskId} scoping argument is
 * always {@code null}. {@code ship}/{@code review} are not here yet: they need Jira/GitLab access,
 * which lands with the external-ops layer; until then use the Claude Master for those.
 */
@Component
public class MasterShellCommands {

    private final OrchestratorTools tools;
    private final DashboardRenderer dashboard;
    private final ConfigService configService;

    public MasterShellCommands(OrchestratorTools tools, DashboardRenderer dashboard, ConfigService configService) {
        this.tools = tools;
        this.dashboard = dashboard;
        this.configService = configService;
    }

    @Command(name = "status", description = "Show the task dashboard")
    public String status() {
        return dashboard.render();
    }

    @Command(name = "do", description = "Delegate a ticket: create a worktree and spawn a sub-agent")
    public String delegate(
            @Argument(index = 0, description ="ticket id, e.g. PAN-123") String ticket,
            @Option(longName = "project", description = "project key (optional if only one is configured)") String project,
            @Option(longName = "plan", defaultValue = "false", description = "start the agent in plan mode") boolean plan) {
        String projectKey = resolveProject(project);
        String instructions = "Read " + ticket + " via your Jira MCP and implement it.";
        String result = tools.initializeTask(ticket, projectKey, instructions, plan ? "plan" : null, null, null);
        return result + "\n\n" + dashboard.render();
    }

    @Command(name = "focus", description = "Bring the task's agent window to the front")
    public String focus(@Argument(index = 0, description ="ticket id or alias") String ticket) {
        return tools.focusTask(ticket) + "\n\n" + dashboard.render();
    }

    @Command(name = "ide", description = "Open the task's diff (default) or full project (--project) in the editor")
    public String ide(
            @Argument(index = 0, description ="ticket id or alias") String ticket,
            @Option(longName = "project", defaultValue = "false", description = "open the worktree as a full project instead of a diff") boolean project) {
        return tools.openInIde(ticket, project ? "project" : "diff", null) + "\n\n" + dashboard.render();
    }

    @Command(name = "deploy", description = "Merge the task branch into deployBranch and push")
    public String deploy(@Argument(index = 0, description ="ticket id or alias") String ticket) {
        return tools.deployTask(ticket, null) + "\n\n" + dashboard.render();
    }

    @Command(name = "respawn", description = "Start a fresh agent session for a registered task")
    public String respawn(@Argument(index = 0, description ="ticket id or alias") String ticket) {
        return tools.openTaskTab(ticket, null) + "\n\n" + dashboard.render();
    }

    @Command(name = "done", description = "Close the task: full cleanup (window, worktree, state); branch kept")
    public String done(@Argument(index = 0, description ="ticket id or alias") String ticket) {
        return tools.removeTask(ticket, null) + "\n\n" + dashboard.render();
    }

    /** Single-project setups need no key; otherwise it must be given and must exist. */
    private String resolveProject(String project) {
        Set<String> keys = configService.load().projects().keySet();
        if (project != null && !project.isBlank()) {
            if (!keys.contains(project)) {
                throw new IllegalArgumentException("Unknown project '" + project + "'. Configured: " + keys);
            }
            return project;
        }
        if (keys.size() == 1) {
            return keys.iterator().next();
        }
        throw new IllegalArgumentException("Multiple projects configured " + keys
                + " — specify one: do <ticket> --project <key>");
    }
}
