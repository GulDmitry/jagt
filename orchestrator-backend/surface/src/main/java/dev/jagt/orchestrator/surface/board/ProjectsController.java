package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectSetup;
import dev.jagt.orchestrator.surface.board.TaskCommandsController.ActionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectSetup setup;

    /** A GET: reading a path writes nothing. */
    @GetMapping("/check")
    public ProjectSetup.Reading check(@RequestParam("path") String path) {
        return setup.read(path);
    }

    @PostMapping
    public ActionResult add(@RequestBody ProjectSetup.Entry entry) {
        return new ActionResult(setup.add(entry));
    }
}
