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

/** A project is checked as far as the filesystem answers; asking a remote costs a fetch per project per start. */
@Component
@RequiredArgsConstructor
public class ConfigCheck implements StartupCheck {

    private static final List<String> VIEW_MODES = List.of("shared", "tab-per-task");
    private static final Map<String, String> RETIRED_KEYS = Map.of(
            "ui", "the board is the only surface",
            "terminal", "the driver comes from `platform`",
            "webTerminal", "`focus` raises the kitty window",
            "dashboard", "nothing renders a console table",
            "openWarpWindow", "renamed to `openTerminalWindow`; set it again");
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
            problems.add(paths.configFile() + " defines no projects. Add one under `orchestrator.projects`"
                    + " (see jagt.yml.dist).");
            return problems;
        }
        projects.forEach((key, project) -> problems.addAll(projectProblems(key, project)));
        return problems;
    }

    /** A file no longer read is worse than a missing one: everything it says is still true, and none applies. */
    private List<String> retiredConfig() {
        Path retired = paths.root().resolve("config.json");
        if (!Files.exists(retired) || Files.exists(paths.configFile())) {
            return List.of();
        }
        return List.of(retired + " is no longer read — everything lives in " + paths.configFile()
                + " now, under one `orchestrator` root. Copy jagt.yml.dist next to it"
                + " and move your projects across:\n"
                + "    orchestrator:\n"
                + "      projects:\n"
                + "        my-project:\n"
                + "          path: /absolute/path/to/base/repository\n"
                + "          baseBranch: origin/main\n"
                + "          deployBranch: dev");
    }

    /** A key nothing binds is dropped in silence, so an older jagt.yml goes on saying what no longer applies. */
    private List<String> retiredKeys() {
        return configService.declaredKeys().stream()
                .filter(RETIRED_KEYS::containsKey)
                .sorted()
                .map(key -> "orchestrator." + key + " is no longer read: " + RETIRED_KEYS.get(key)
                        + ". Remove it from " + paths.configFile() + ".")
                .toList();
    }

    /** Spring reads {@code ./config/application.yml} above anything a launch hands it, so it outranks jagt.yml. */
    private static List<String> shadowingConfigDir() {
        Path shadowing = Path.of(System.getProperty("user.dir")).resolve("config").resolve("application.yml");
        if (!Files.exists(shadowing)) {
            return List.of();
        }
        return List.of(shadowing + " outranks jagt.yml for every key it names."
                + " Move what it holds into jagt.yml and delete it.");
    }

    private static List<String> viewerProblems(ConfigService.ConfigFile config) {
        List<String> problems = new ArrayList<>();
        String session = config.viewer().tmuxSession();
        if (session != null && !session.isBlank()
                && session.chars().anyMatch(c -> RESERVED_IN_SESSION_NAME.indexOf(c) >= 0)) {
            problems.add("viewer.tmuxSession '" + session + "' contains ':' or '.', which tmux reserves."
                    + " Pick a name without them.");
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
                    + "' — jagt never writes to the base branch. Point it elsewhere or remove it.");
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
