package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.TaskArtifacts;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskFilesController {

    public record Rendered(String name, String html) {
    }

    private final TaskArtifacts artifacts;

    /** The documents in the task's worktree now; one the agent has not written is not listed. */
    @GetMapping("/tasks/files")
    public List<String> files(@RequestParam("task") String taskId) {
        return artifacts.present(taskId);
    }

    @GetMapping("/tasks/file")
    public Rendered file(@RequestParam("task") String taskId, @RequestParam("name") String name) {
        return new Rendered(name, MarkdownHtml.render(artifacts.read(taskId, name)));
    }
}
