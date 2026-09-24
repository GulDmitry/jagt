package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.AgentRuntime;
import dev.jagt.orchestrator.port.SessionHost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The unattended reviewer's own session: ONE, in the orchestrator root rather than any worktree, so it can read
 * every task and belongs to none. What it judges by is a file it re-reads, never the context it accumulates —
 * a session that lives for days is compacted, and the standards would go first.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterSession {

    /** Its window name, which is also how it is found again — it is not a task and has no id of its own. */
    public static final String WINDOW = "master";

    private final ConfigService configService;
    private final SessionHost sessions;
    private final AgentRuntime agentRuntime;
    private final OrchestratorPaths paths;

    public boolean live() {
        return sessions.taskWindowState(session(), WINDOW) == SessionHost.WindowState.AGENT_RUNNING;
    }

    /** Starts it where it is wanted and not already running; answers whether this call is what started it. */
    public boolean startIfWanted() {
        ConfigService.ConfigFile config = configService.load();
        if (!config.master().running() || live()) {
            return false;
        }
        sessions.openWindow(session(config), viewerTitle(config), WINDOW, paths.root(),
                agentRuntime.launchCommand(paths.root(), false, config.master().modelOrInherited()));
        log.atInfo().setMessage("master session started")
                .addKeyValue("mode", config.master().modeOrOff().id())
                .addKeyValue("model", config.master().modelOrInherited())
                .log();
        return true;
    }

    /** Ends it; answers how many windows there were, which is 0 where it was not running. */
    public int stop() {
        return sessions.killTaskWindows(session(), WINDOW);
    }

    private String session() {
        return session(configService.load());
    }

    private String session(ConfigService.ConfigFile config) {
        return sessions.sessionName(config.viewer().tmuxSession());
    }

    private static String viewerTitle(ConfigService.ConfigFile config) {
        return config.viewer().tmuxSession();
    }
}
