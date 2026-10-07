package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.GitRemote;
import lombok.RequiredArgsConstructor;
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
            String path = GitRemote.projectPath(gitWorktrees.remoteUrl(Path.of(project.getValue().path())));
            if (path != null && mrUrl.contains(path)) {
                return project.getKey();
            }
        }
        throw new IllegalArgumentException("no configured project matches request url: " + mrUrl);
    }
}
