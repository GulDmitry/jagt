package dev.jagt.orchestrator.startup;

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
    /** tmux addresses a window as {@code session:window.pane}, so a name carrying either is unaddressable. */
    private static final String RESERVED_IN_SESSION_NAME = ":.";

    private final ConfigService configService;

    @Override
    public List<String> problems() {
        ConfigService.ConfigFile config;
        try {
            config = configService.load();
        } catch (RuntimeException unreadable) {
            return List.of(unreadable.getMessage());
        }
        List<String> problems = new ArrayList<>(viewerProblems(config));
        Map<String, ProjectConfig> projects = config.projects();
        if (projects.isEmpty()) {
            problems.add("config.json defines no projects — jagt has nothing to cut a worktree from."
                    + " Add one under `projects` (see config.json.dist).");
            return problems;
        }
        projects.forEach((key, project) -> problems.addAll(projectProblems(key, project)));
        return problems;
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
