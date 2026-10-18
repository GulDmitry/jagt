package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.config.OrchestratorPaths;
import dev.jagt.orchestrator.config.ProjectProblems;
import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.SessionHost;
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

    private static final String CONFIG_FILE = "orchestrator.config-file";
    private static final List<String> VIEW_MODES = List.of("shared", "tab-per-task");
    private static final Map<String, String> RETIRED_KEYS = Map.of(
            "ui", "the board is the only surface",
            "terminal", "the driver comes from `platform`",
            "webTerminal", "`focus` raises the terminal window",
            "dashboard", "nothing renders a console table",
            "openWarpWindow", "renamed to `openTerminalWindow`; set it again");
    private final ConfigService configService;
    private final OrchestratorPaths paths;
    private final SessionHost sessions;

    @Override
    public List<Problem> problems() {
        List<Problem> retired = retiredConfig();
        if (!retired.isEmpty()) {
            return retired;
        }
        List<Problem> shadowed = shadowingConfigDir();
        if (!shadowed.isEmpty()) {
            return shadowed;
        }
        ConfigService.ConfigFile config;
        try {
            config = configService.load();
        } catch (RuntimeException unreadable) {
            return List.of(new Problem(CONFIG_FILE, String.valueOf(unreadable.getMessage())));
        }
        List<Problem> problems = new ArrayList<>(retiredKeys());
        problems.addAll(viewerProblems(config));
        Map<String, ProjectConfig> projects = config.projects();
        if (projects.isEmpty()) {
            problems.add(new Problem("orchestrator.projects", paths.configFile() + " defines no projects. Add one"
                    + " under `orchestrator.projects` (see jagt.yml.dist)."));
            return problems;
        }
        projects.forEach((key, project) -> problems.addAll(ProjectProblems.of(key, project)));
        return problems;
    }

    /** A file no longer read is worse than a missing one: everything it says is still true, and none applies. */
    private List<Problem> retiredConfig() {
        Path retired = paths.root().resolve("config.json");
        if (!Files.exists(retired) || Files.exists(paths.configFile())) {
            return List.of();
        }
        return List.of(new Problem(CONFIG_FILE, retired + " is no longer read — everything lives in "
                + paths.configFile() + " now, under one `orchestrator` root. Copy jagt.yml.dist next to it"
                + " and move your projects across:\n"
                + "    orchestrator:\n"
                + "      projects:\n"
                + "        my-project:\n"
                + "          path: /absolute/path/to/base/repository\n"
                + "          baseBranch: origin/main\n"
                + "          deployBranch: dev"));
    }

    /** A key nothing binds is dropped in silence, so an older jagt.yml goes on saying what no longer applies. */
    private List<Problem> retiredKeys() {
        return configService.declaredKeys().stream()
                .filter(RETIRED_KEYS::containsKey)
                .sorted()
                .map(key -> new Problem("orchestrator." + key, "orchestrator." + key + " is no longer read: "
                        + RETIRED_KEYS.get(key) + ". Remove it from " + paths.configFile() + "."))
                .toList();
    }

    /** Spring reads {@code ./config/application.yml} above anything a launch hands it, so it outranks jagt.yml. */
    private static List<Problem> shadowingConfigDir() {
        Path shadowing = Path.of(System.getProperty("user.dir")).resolve("config").resolve("application.yml");
        if (!Files.exists(shadowing)) {
            return List.of();
        }
        return List.of(new Problem("config/application.yml", shadowing + " outranks jagt.yml for every key it"
                + " names. Move what it holds into jagt.yml and delete it."));
    }

    private List<Problem> viewerProblems(ConfigService.ConfigFile config) {
        List<Problem> problems = new ArrayList<>();
        String session = config.viewer().session();
        if (session != null && !session.isBlank()) {
            sessions.unaddressable(session).ifPresent(why -> problems.add(new Problem(
                    "orchestrator.viewer.tmuxSession", "viewer.tmuxSession '" + session + "' " + why
                    + ". Pick another name.")));
        }
        String viewMode = config.viewer().viewMode();
        if (viewMode != null && !viewMode.isBlank()
                && VIEW_MODES.stream().noneMatch(mode -> mode.equalsIgnoreCase(viewMode))) {
            problems.add(new Problem("orchestrator.viewer.viewMode", "viewer.viewMode '" + viewMode
                    + "' is not one of " + VIEW_MODES + "."));
        }
        return problems;
    }
}
