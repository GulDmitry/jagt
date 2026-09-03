package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.LaunchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DoCommand implements GlobalCommand {

    private final TaskLauncher launcher;

    @Override
    public String id() {
        return "do";
    }

    @Override
    public String hint() {
        return "spin up a sub-agent in a worktree, from a ticket key, a URL, or your own words";
    }

    @Override
    public List<String> usage() {
        return List.of(LaunchRequest.GRAMMAR,
                LaunchRequest.OWN_GRAMMAR + " — no ticket: your own words are the task, and name its branch",
                "  … [proj1,proj2] — one session, a worktree in EACH: work that spans repositories",
                "  … [from <branch>] — cut the worktree from <branch> and target its request at it",
                "  … [recreate|resume] — the branch exists already: cut it fresh, or take over its commits");
    }

    @Override
    public String run(String tail) {
        return launcher.launchLine(tail).message();
    }
}
