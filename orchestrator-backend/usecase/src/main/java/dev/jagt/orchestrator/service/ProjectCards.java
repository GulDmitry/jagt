package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.ProjectConfig;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

/** A project's whole jagt.yml entry, with the address of the repository it points at. */
@Service
@RequiredArgsConstructor
public class ProjectCards {

    /** {@code remoteUrl} is blank where the repository has no remote to read. */
    public record Card(String key, String path, String remoteUrl, @Nullable String remote, String baseBranch,
                       String deployBranch, List<String> labels, @Nullable String about,
                       @Nullable List<String> verifyCommand, @Nullable List<String> humanPaths,
                       @Nullable String codeHost, @Nullable String codeHostName,
                       @Nullable List<String> pipelineCommand, @Nullable List<String> retryCommand) {
    }

    private final ConfigService configService;
    private final GitWorktrees gitWorktrees;

    public Card of(String key) {
        ProjectConfig project = configService.load().projects().get(key);
        if (project == null) {
            throw new IllegalArgumentException("No project " + key + " in jagt.yml");
        }
        return new Card(key, project.path(), remoteUrl(project), project.remote(), project.baseBranch(),
                project.deployBranch(), project.labels(), project.about(), project.verifyCommand(),
                project.humanPaths(), project.codeHost(), project.codeHostName(), project.pipelineCommand(),
                project.retryCommand());
    }

    private String remoteUrl(ProjectConfig project) {
        try {
            return gitWorktrees.remoteUrl(Path.of(project.path()));
        } catch (IllegalStateException noRemote) {
            return "";
        }
    }
}
