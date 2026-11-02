package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.GitRemote;
import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/** Which configured project a review request belongs to, matched on the repository path its own remote names. */
@Component
@RequiredArgsConstructor
public class RequestProject {

    private final ConfigService configService;
    private final GitWorktrees gitWorktrees;

    public String of(String mrUrl) {
        for (var project : configService.load().projects().entrySet()) {
            if (GitRemote.holds(remoteOf(project.getValue()), mrUrl)) {
                return project.getKey();
            }
        }
        throw new IllegalArgumentException("no configured project matches request url: " + mrUrl);
    }

    private @Nullable String remoteOf(ProjectConfig project) {
        try {
            return gitWorktrees.remoteUrl(Path.of(project.path()));
        } catch (IllegalStateException noRemote) {
            return null;
        }
    }
}
