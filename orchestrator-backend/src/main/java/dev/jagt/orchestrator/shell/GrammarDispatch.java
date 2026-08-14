package dev.jagt.orchestrator.shell;

import dev.jagt.orchestrator.model.ActionOrigin;
import dev.jagt.orchestrator.model.LaunchRequest;
import dev.jagt.orchestrator.model.TaskAction;
import dev.jagt.orchestrator.service.CommandReference;
import dev.jagt.orchestrator.service.CommandService;
import dev.jagt.orchestrator.service.ConfigService;
import dev.jagt.orchestrator.service.NaturalLanguageDispatch;
import dev.jagt.orchestrator.service.OriginContext;
import dev.jagt.orchestrator.service.StateViews;
import dev.jagt.orchestrator.service.TaskLauncher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tier 1 of the dispatch: a typed line, parsed by a fixed grammar and executed in-process. No model, no MCP
 * round-trip. Free text falls through to {@link NaturalLanguageDispatch}, which is the only path that spends
 * tokens.
 */
@Component
@RequiredArgsConstructor
public class GrammarDispatch {

    private static final Set<String> BRANCH_STRATEGIES = Set.of("recreate", "resume", "fresh");
    private static final String DO_USAGE = "do <ticket|url> [project] [plan] [from <branch>] [notes…]";

    private final StateViews views;
    private final CommandService commands;
    private final TaskLauncher launcher;
    private final NaturalLanguageDispatch naturalLanguage;
    private final ConfigService configService;

    /** A blank answer means the caller shows the dashboard alone. */
    public String run(String line) {
        return OriginContext.as(ActionOrigin.CONSOLE, () -> runHere(line));
    }

    private String runHere(String line) {
        List<String> tok = List.of(line.split("\\s+"));
        try {
            return switch (tok.get(0)) {
                case "status" -> "";
                case "stats" -> views.usageStats();
                case "help" -> CommandReference.text();
                case "do" -> launcher.launch(parseDoArgs(tok));
                case "resume" -> resume(tok);
                case "review" -> act(tok, TaskAction.SWEEP);
                case "ship" -> act(tok, TaskAction.SHIP);
                case "focus" -> act(tok, TaskAction.FOCUS);
                case "ide" -> act(tok, tok.contains("diff") ? TaskAction.DIFF : TaskAction.IDE);
                case "deploy" -> act(tok, TaskAction.DEPLOY);
                case "revert" -> act(tok, TaskAction.REVERT);
                case "respawn" -> act(tok, TaskAction.RESPAWN);
                case "done" -> act(tok, TaskAction.DONE);
                default -> naturalLanguage.interpret(line);
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "error: " + e.getMessage();
        }
    }

    /** The same action a board button posts, through the gate that refuses what a status does not allow. */
    private String act(List<String> tok, TaskAction action) {
        return commands.execute(arg(tok, 1, action.id() + " <ticket>"), action);
    }

    /** The request names its own branches, so anything typed beside its URL could only contradict it. */
    String resume(List<String> tok) {
        String url = tok.stream().skip(1).filter(token -> token.startsWith("http")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("usage: resume <mr-url>"));
        if (tok.size() > 2) {
            throw new IllegalArgumentException("usage: resume <mr-url> — the request carries its own branches;"
                    + " to start a NEW task on a new branch use `do <ticket>`");
        }
        return launcher.resume(url);
    }

    /**
     * Splits {@code do <ticket> …} after the ticket: {@code plan}, a known project key, a branch strategy and
     * {@code from <branch>} are consumed as modifiers in any order, and the rest is free-text notes. Each is
     * recognised only as a LEADING token, so a note may contain the word "plan".
     */
    LaunchRequest parseDoArgs(List<String> tok) {
        String ref = arg(tok, 1, DO_USAGE);
        List<String> rest = new ArrayList<>(tok.subList(Math.min(2, tok.size()), tok.size()));
        Set<String> projectKeys = configService.load().projects().keySet();
        String mode = null;
        String project = null;
        String strategy = null;
        String baseBranch = null;
        while (!rest.isEmpty()) {
            String head = rest.get(0);
            if (mode == null && head.equals("plan")) {
                mode = "plan";
            } else if (project == null && projectKeys.contains(head)) {
                project = head;
            } else if (strategy == null && BRANCH_STRATEGIES.contains(head)) {
                strategy = head;
            } else if (baseBranch == null && head.equals("from")) {
                if (rest.size() < 2 || rest.get(1).isBlank()) {
                    throw new IllegalArgumentException("usage: " + DO_USAGE
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

    private static String arg(List<String> tok, int i, String usage) {
        if (tok.size() <= i || tok.get(i).isBlank()) {
            throw new IllegalArgumentException("usage: " + usage);
        }
        return tok.get(i);
    }
}
