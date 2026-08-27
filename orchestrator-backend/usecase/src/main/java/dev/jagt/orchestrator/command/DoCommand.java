package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.task.BranchStrategy;
import dev.jagt.orchestrator.task.LaunchRequest;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.command.GlobalCommand;
import dev.jagt.orchestrator.service.TaskLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DoCommand implements GlobalCommand {

    private static final Set<String> BRANCH_STRATEGIES = Set.copyOf(BranchStrategy.ids());
    private static final String USAGE = "do <ticket|url> [project[,project…]] [plan] [from <branch>] [notes…]";

    private final TaskLauncher launcher;
    private final ConfigService configService;

    @Override
    public String id() {
        return "do";
    }

    @Override
    public String hint() {
        return "spin up a sub-agent in a worktree, from a ticket key or a URL";
    }

    @Override
    public List<String> usage() {
        return List.of("do <ticket> [project] [plan]",
                "  … [proj1,proj2] — one session, a worktree in EACH: work that spans repositories",
                "  … [from <branch>] — cut the worktree from <branch> and target its request at it");
    }

    @Override
    public String run(String tail) {
        return launcher.launch(parse(List.of(tail.split("\\s+")))).message();
    }

    /**
     * Splits the tail after the ticket: {@code plan}, a known project key, a branch strategy and
     * {@code from <branch>} are consumed as modifiers in any order, and the rest is free-text notes. Each is
     * recognised only as a LEADING token, so a note may contain the word "plan".
     */
    LaunchRequest parse(List<String> tail) {
        if (tail.isEmpty() || tail.get(0).isBlank()) {
            throw new IllegalArgumentException("usage: " + USAGE);
        }
        String ref = tail.get(0);
        List<String> rest = new ArrayList<>(tail.subList(1, tail.size()));
        Set<String> projectKeys = configService.load().projects().keySet();
        String mode = null;
        String project = null;
        String strategy = null;
        String baseBranch = null;
        while (!rest.isEmpty()) {
            String head = rest.get(0);
            if (mode == null && head.equals("plan")) {
                mode = "plan";
            } else if (project == null && isProjects(head, projectKeys)) {
                project = head;
            } else if (strategy == null && BRANCH_STRATEGIES.contains(head)) {
                strategy = head;
            } else if (baseBranch == null && head.equals("from")) {
                if (rest.size() < 2 || rest.get(1).isBlank()) {
                    throw new IllegalArgumentException("usage: " + USAGE
                            + " — `from` needs the branch to start from");
                }
                baseBranch = rest.remove(1);
            } else {
                break;
            }
            rest.remove(0);
        }
        return new LaunchRequest(ref, project, mode, strategy, baseBranch,
                String.join(" ", rest).strip()).normalized();
    }

    /**
     * One project key or several comma-separated, EVERY one of them configured — the token is a project only
     * then, so a note that happens to contain a comma is still a note.
     */
    private static boolean isProjects(String token, Set<String> known) {
        List<String> named = Arrays.stream(token.split(",")).map(String::strip)
                .filter(key -> !key.isEmpty()).toList();
        return !named.isEmpty() && known.containsAll(named);
    }
}
