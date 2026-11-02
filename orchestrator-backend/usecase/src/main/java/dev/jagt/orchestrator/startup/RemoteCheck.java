package dev.jagt.orchestrator.startup;

import dev.jagt.orchestrator.port.Problem;
import dev.jagt.orchestrator.port.Processes;
import dev.jagt.orchestrator.port.StartupCheck;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.Remote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A project's remote is one its repository lists, and a repository with none has no code host; read locally, never asked of the network. */
@Component
@RequiredArgsConstructor
public class RemoteCheck implements StartupCheck {

    private static final Duration GIT = Duration.ofSeconds(10);
    private static final String ROOT = "orchestrator";

    private final ConfigService configService;
    private final Processes processes;

    @Override
    public List<Problem> problems() {
        ConfigService.ConfigFile config;
        try {
            config = configService.load();
        } catch (RuntimeException unreadable) {
            return List.of();
        }
        List<Problem> problems = new ArrayList<>();
        for (Map.Entry<String, ProjectConfig> project : config.projects().entrySet()) {
            problems.addAll(problemsOf(config, project.getKey(), project.getValue()));
        }
        return problems;
    }

    private List<Problem> problemsOf(ConfigService.ConfigFile config, String key, ProjectConfig project) {
        String remoteKey = ROOT + ".projects." + key + ".remote";
        Remote remote = project.gitRemote();
        if (remote.local()) {
            return config.codeHostNamed(key).filter(kind -> !ProjectConfig.NO_CODE_HOST.equalsIgnoreCase(kind))
                    .map(kind -> List.of(new Problem(remoteKey, remoteKey + " is none, and "
                            + codeHostKey(project, key) + " is '" + kind + "': a repository with no remote has no code"
                            + " host. Set that codeHost to none, or give the project a remote.")))
                    .orElse(List.of());
        }
        return listed(project).filter(names -> !names.contains(remote.name()))
                .map(names -> List.of(new Problem(remoteKey, remoteKey + " '" + remote.name() + "' is not a remote of "
                        + project.path() + " (git lists " + (names.isEmpty() ? "none" : String.join(", ", names))
                        + "). Name one of them, or set it to none for a repository kept on this machine.")))
                .orElse(List.of());
    }

    private static String codeHostKey(ProjectConfig project, String key) {
        return project.codeHost() == null || project.codeHost().isBlank() ? ROOT + ".codeHost"
                : ROOT + ".projects." + key + ".codeHost";
    }

    private java.util.Optional<List<String>> listed(ProjectConfig project) {
        try {
            Processes.Result result = processes.run(null, GIT, List.of("git", "-C", project.path(), "remote"));
            return result.exitCode() == 0 ? java.util.Optional.of(result.stdout().lines().map(String::strip)
                    .filter(name -> !name.isEmpty()).toList()) : java.util.Optional.empty();
        } catch (RuntimeException unreadable) {
            return java.util.Optional.empty();
        }
    }
}
