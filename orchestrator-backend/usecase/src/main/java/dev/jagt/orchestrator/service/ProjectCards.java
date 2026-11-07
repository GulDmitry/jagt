package dev.jagt.orchestrator.service;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import dev.jagt.orchestrator.task.ProjectConfig;
import dev.jagt.orchestrator.task.ProjectConfig.Branches;
import dev.jagt.orchestrator.task.ProjectConfig.CodeHost;
import dev.jagt.orchestrator.task.ProjectConfig.Repository;
import dev.jagt.orchestrator.task.ProjectConfig.Verify;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/** A project's whole jagt.yml entry, with the address of the repository it points at. */
@Service
@RequiredArgsConstructor
public class ProjectCards {

    /** {@code remoteUrl} is blank where the repository has no remote to read. */
    public record Card(String key, String remoteUrl, @JsonUnwrapped Repository repository,
                       @JsonUnwrapped Branches branches, @JsonUnwrapped CodeHost host,
                       @JsonUnwrapped Verify verify) {
    }

    private final ConfigService configService;
    private final GitWorktrees gitWorktrees;

    public Card of(String key) {
        ProjectConfig project = configService.load().projects().get(key);
        if (project == null) {
            throw new IllegalArgumentException("No project " + key + " in jagt.yml");
        }
        return new Card(key, remoteUrl(project), project.repository(), project.branches(), project.host(),
                project.verify());
    }

    private String remoteUrl(ProjectConfig project) {
        try {
            return gitWorktrees.remoteUrl(Path.of(project.path()));
        } catch (IllegalStateException noRemote) {
            return "";
        }
    }
}
