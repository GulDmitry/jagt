package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ProjectRemoval;
import dev.jagt.orchestrator.service.ProjectSetup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectCommand implements GlobalCommand {

    private final ProjectSetup setup;
    private final ProjectRemoval removal;

    @Override
    public String id() {
        return "project";
    }

    @Override
    public int rank() {
        return 195;
    }

    @Override
    public String hint() {
        return "add a repository to jagt.yml as a project, its entry read from the repository, change one or remove one";
    }

    @Override
    public List<String> usage() {
        return List.of("project", "project <path>", "project edit <key>", "project remove <key>");
    }

    @Override
    public String part() {
        return "project";
    }

    @Override
    public String run(String tail) {
        String path = tail.strip();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("usage: project <path> — typed alone, `project` opens the form");
        }
        if (path.startsWith("edit ")) {
            throw new IllegalArgumentException("project edit <key> opens the form on the board, prefilled");
        }
        if (path.startsWith("remove ")) {
            return removal.remove(path.substring("remove ".length()).strip());
        }
        ProjectSetup.Reading reading = setup.read(path);
        if (!reading.ready()) {
            throw new IllegalArgumentException(reading.firstProblem());
        }
        return setup.add(new ProjectSetup.Entry(reading.key(), path, reading.baseBranch(),
                reading.deployBranch().isBlank() ? null : reading.deployBranch(),
                reading.verifyCommand().isEmpty() ? null : reading.verifyCommand(), null, null, null));
    }
}
