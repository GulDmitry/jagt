package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.ProjectSetup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectCommand implements GlobalCommand {

    private final ProjectSetup setup;

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
        return "add a repository to jagt.yml as a project, its entry read from the repository";
    }

    @Override
    public List<String> usage() {
        return List.of("project", "project <path>");
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
        ProjectSetup.Reading reading = setup.read(path);
        if (!reading.ready()) {
            throw new IllegalArgumentException(reading.firstProblem());
        }
        return setup.add(new ProjectSetup.Entry(reading.key(), path, reading.baseBranch(),
                reading.deployBranch().isBlank() ? null : reading.deployBranch(),
                reading.verifyCommand().isEmpty() ? null : reading.verifyCommand(), null));
    }
}
