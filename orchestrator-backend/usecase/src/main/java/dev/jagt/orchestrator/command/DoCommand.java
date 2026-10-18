package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.Refusal;
import dev.jagt.orchestrator.service.TaskLauncher;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.task.Launched;
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
    public int rank() {
        return 40;
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
    public String part() {
        return "launch";
    }

    @Override
    public String run(String tail) {
        Launched launched = launcher.launchLine(tail);
        if (launched.reason() == Launched.Reason.NOTHING_TO_DO || launched.reason() == Launched.Reason.NO_BRANCH_NAME) {
            return created(Launched.refused(launched.reason(),
                    launched.message() + " — say what the task is: " + LaunchRequest.OWN_GRAMMAR));
        }
        return created(launched);
    }

    static String created(Launched launched) {
        if (!launched.created()) {
            throw new Refusal(Refusal.Code.STATE, launched.message());
        }
        return launched.message();
    }
}
