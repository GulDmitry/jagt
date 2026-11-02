package dev.jagt.orchestrator.service;

import dev.jagt.orchestrator.task.TaskRepo;
import dev.jagt.orchestrator.task.TaskState;

import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.List;

/** The project whose code host answers for a pipeline address: the repository whose request lives on the same host, else the first. */
final class PipelineProject {

    private PipelineProject() {
    }

    static String of(TaskState task, String pipelineUrl) {
        List<TaskRepo> repos = task.repos().stream().filter(repo -> repo.project() != null).toList();
        return repos.stream().filter(repo -> hostOf(pipelineUrl).equals(hostOf(repo.mrUrl()))).findFirst()
                .or(() -> repos.stream().findFirst()).map(TaskRepo::project).orElse("");
    }

    private static String hostOf(@Nullable String url) {
        try {
            String host = url == null ? null : URI.create(url.strip()).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException malformed) {
            return "";
        }
    }
}
