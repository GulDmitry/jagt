package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the human's own config file promises: repositories that are there, branches that can be told apart, and
 * a session name tmux will accept.
 *
 * <p>A project is checked as far as the filesystem answers — a branch lives on a remote, and asking costs a
 * fetch per project on every start.
 */
@Component
@RequiredArgsConstructor
public class ConfigCheck implements StartupCheck {

    private static final List<String> VIEW_MODES = List.of("shared", "tab-per-task");
    /** What each key a jagt.yml may still carry has become, printed as-is rather than described. */
    private static final Map<String, String> RETIRED_KEYS = Map.of(
            "ui", "there is one surface now, the board — a console no longer ships",
            "terminal", "kitty is the only viewer; the driver comes from `platform`",
            "webTerminal", "the embedded ttyd terminal is gone; `focus` raises the kitty window",
            "dashboard", "it sized the console's table, and nothing renders one",
            "openWarpWindow", "renamed to `openTerminalWindow` — SET IT AGAIN or a window opens on every launch");
    /** tmux addresses a window as {@code session:window.pane}, so a name carrying either is unaddressable. */
    private static final String RESERVED_IN_SESSION_NAME = ":.";

    private final ConfigService configService;
    private final OrchestratorPaths paths;

    @Override
    public List<String> problems() {
        List<String> retired = retiredConfig();
        if (!retired.isEmpty()) {
            return retired;
        }
        List<String> shadowed = shadowingConfigDir();
        if (!shadowed.isEmpty()) {
            return shadowed;
        }
        ConfigService.ConfigFile config;
        try {
            config = configService.load();
        } catch (RuntimeException unreadable) {
            return List.of(unreadable.getMessage());
        }
        List<String> problems = new ArrayList<>(retiredKeys());
        problems.addAll(viewerProblems(config));
        Map<String, ProjectConfig> projects = config.projects();
        if (projects.isEmpty()) {
            problems.add(paths.configFile() + " defines no projects — jagt has nothing to cut a worktree"
                    + " from. Add one under `orchestrator.projects` (see jagt.yml.dist).");
            return problems;
        }
        projects.forEach((key, project) -> problems.addAll(projectProblems(key, project)));
        return problems;
    }

    /**
     * A settings file that is no longer read is worse than a missing one: everything it says is still true, and
     * none of it applies. So it is named, and the shape that replaces it is printed rather than described.
     */
    private List<String> retiredConfig() {
        Path retired = paths.root().resolve("config.json");
        if (!Files.exists(retired) || Files.exists(paths.configFile())) {
            return List.of();
        }
        return List.of(retired + " is no longer read — everything lives in " + paths.configFile()
                + " now, under one `orchestrator` root, and it takes comments. Copy jagt.yml.dist next to it"
                + " and move your projects across:\n"
                + "    orchestrator:\n"
                + "      projects:\n"
                + "        my-project:\n"
                + "          path: /absolute/path/to/base/repository\n"
                + "          baseBranch: origin/main\n"
                + "          deployBranch: dev");
    }

    /**
     * A key nothing binds is dropped in silence by both readers, so a jagt.yml written for an older jagt goes
     * on saying something true that no longer applies — which is the one thing a startup check exists for.
     */
    private List<String> retiredKeys() {
        return configService.declaredKeys().stream()
                .filter(RETIRED_KEYS::containsKey)
                .sorted()
                .map(key -> "orchestrator." + key + " is no longer read: " + RETIRED_KEYS.get(key)
                        + ". Remove it from " + paths.configFile() + ".")
                .toList();
    }

    /**
     * Spring reads {@code ./config/application.yml} at a HIGHER precedence than anything a launch hands it, so
     * one left behind quietly wins over jagt.yml for every key it names — and nothing on screen would say so.
     */
    private static List<String> shadowingConfigDir() {
        Path shadowing = Path.of(System.getProperty("user.dir")).resolve("config").resolve("application.yml");
        if (!Files.exists(shadowing)) {
            return List.of();
        }
        return List.of(shadowing + " outranks jagt.yml for every key it names, and nothing would tell you."
                + " Move what it holds into jagt.yml and delete it.");
    }

    private static List<String> viewerProblems(ConfigService.ConfigFile config) {
        List<String> problems = new ArrayList<>();
        String session = config.viewer().tmuxSession();
        if (session != null && !session.isBlank()
                && session.chars().anyMatch(c -> RESERVED_IN_SESSION_NAME.indexOf(c) >= 0)) {
            problems.add("viewer.tmuxSession '" + session + "': tmux reserves ':' and '.' — every agent window"
                    + " would be unaddressable. Pick a name without them.");
        }
        String viewMode = config.viewer().viewMode();
        if (viewMode != null && !viewMode.isBlank()
                && VIEW_MODES.stream().noneMatch(mode -> mode.equalsIgnoreCase(viewMode))) {
            problems.add("viewer.viewMode '" + viewMode + "' is not one of " + VIEW_MODES + ".");
        }
        return problems;
    }

    private static List<String> projectProblems(String key, ProjectConfig project) {
        List<String> problems = new ArrayList<>();
        String where = "projects." + key;
        if (isBlank(project.path())) {
            problems.add(where + ".path is empty — set it to the base repository.");
        } else {
            problems.addAll(repositoryProblems(where, Path.of(project.path())));
        }
        if (isBlank(project.baseBranch())) {
            problems.add(where + ".baseBranch is empty — set the branch tasks are cut from, e.g."
                    + " `origin/main`.");
        }
        if (project.deploysIntoTheBaseBranch()) {
            problems.add(where + ".deployBranch equals the base branch '" + project.baseBranchName()
                    + "' — a deploy would merge into the branch tasks are created from, which jagt refuses."
                    + " Point it elsewhere or remove it.");
        }
        return problems;
    }

    private static List<String> repositoryProblems(String where, Path path) {
        if (!Files.isDirectory(path)) {
            return List.of(where + ".path " + path + " is not a directory.");
        }
        if (!Files.exists(path.resolve(".git"))) {
            return List.of(where + ".path " + path + " is not a git repository.");
        }
        Path worktreeParent = path.toAbsolutePath().normalize().getParent();
        if (worktreeParent != null && !Files.isWritable(worktreeParent)) {
            return List.of(where + ".path " + path + ": jagt cuts every worktree next to the repository and"
                    + " cannot write in " + worktreeParent + ".");
        }
        return List.of();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
