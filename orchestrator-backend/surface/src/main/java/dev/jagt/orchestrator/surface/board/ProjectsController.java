package dev.jagt.orchestrator.surface.board;

import dev.jagt.orchestrator.service.ProjectFolders;
import dev.jagt.orchestrator.service.ProjectRemoval;
import dev.jagt.orchestrator.service.ProjectSetup;
import dev.jagt.orchestrator.surface.board.TaskCommandsController.ActionResult;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectsController {

    private final ProjectSetup setup;
    private final ProjectFolders folders;
    private final ProjectRemoval removal;

    /** A GET: reading a path writes nothing. */
    @GetMapping("/check")
    public ProjectSetup.Reading check(@RequestParam("path") String path,
                                      @RequestParam(value = "own", required = false) @Nullable String own) {
        return setup.read(path, own);
    }

    /** A GET: the entry the edit form starts from. */
    @GetMapping("/{key}")
    public ProjectSetup.Entry entry(@PathVariable("key") String key) {
        return setup.entry(key);
    }

    /** A GET: listing folders writes nothing. */
    @GetMapping("/dirs")
    public List<ProjectFolders.Folder> dirs(@RequestParam("under") String under) {
        return folders.under(under);
    }

    @PostMapping
    public ActionResult add(@RequestBody ProjectSetup.Entry entry) {
        return new ActionResult(setup.add(entry));
    }

    @PutMapping("/{key}")
    public ActionResult edit(@PathVariable("key") String key, @RequestBody ProjectSetup.Entry entry) {
        return new ActionResult(setup.edit(new ProjectSetup.Entry(key, entry.path(), entry.baseBranch(),
                entry.deployBranch(), entry.verifyCommand(), entry.about())));
    }

    @DeleteMapping("/{key}")
    public ActionResult remove(@PathVariable("key") String key) {
        return new ActionResult(removal.remove(key));
    }
}
