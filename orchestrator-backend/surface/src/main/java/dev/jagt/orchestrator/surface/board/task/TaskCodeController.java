package dev.jagt.orchestrator.surface.board.task;

import dev.jagt.orchestrator.service.worktree.WorktreeCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks/code")
@RequiredArgsConstructor
public class TaskCodeController {

  private final WorktreeCode code;

  @GetMapping("/tree")
  public List<WorktreeCode.Tree> tree(@RequestParam("task") String taskId) {
    return code.tree(taskId);
  }

  @GetMapping("/file")
  public WorktreeCode.Opened file(
      @RequestParam("task") String taskId,
      @RequestParam(value = "project", defaultValue = "") String project,
      @RequestParam("path") String path) {
    return code.file(taskId, project, path);
  }

  @GetMapping("/search")
  public WorktreeCode.Found search(
      @RequestParam("task") String taskId,
      @RequestParam(value = "project", defaultValue = "") String project,
      @RequestParam("q") String query) {
    return code.search(taskId, project, query);
  }
}
