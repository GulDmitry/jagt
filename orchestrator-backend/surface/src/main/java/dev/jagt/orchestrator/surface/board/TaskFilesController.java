package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.port.LineChange;
import dev.jagt.orchestrator.service.PlanBaseline;
import dev.jagt.orchestrator.service.TaskArtifacts;
import dev.jagt.orchestrator.task.Artifact;
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
    private final PlanBaseline baseline;

    @GetMapping("/tasks/files")
    public List<String> files(@RequestParam("task") String taskId) {
        return artifacts.present(taskId);
    }

    @GetMapping("/tasks/file")
    public Rendered file(@RequestParam("task") String taskId, @RequestParam("name") String name) {
        String content = artifacts.read(taskId, name);
        LineChange change = name.equals(Artifact.PLAN.fileName()) ? baseline.since(taskId, content) : LineChange.none();
        return new Rendered(name, MarkdownHtml.render(content, change));
    }
}
