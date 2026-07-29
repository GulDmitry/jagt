package dev.jawo.orchestrator.shell;

import dev.jawo.orchestrator.assistant.MasterAssistant;
import dev.jawo.orchestrator.assistant.MasterAssistant.TicketFacts;
import dev.jawo.orchestrator.mcp.OrchestratorTools;
import dev.jawo.orchestrator.service.ConfigService;
import dev.jawo.orchestrator.service.DashboardRenderer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Master control terminal: a deterministic JLine REPL running in the backend process. It parses a
 * fixed grammar and calls {@link OrchestratorTools} directly (same JVM — no LLM, no MCP round-trip, no
 * drift). Every command prints its result then the dashboard, so the terminal always ends on current
 * state. Callers here are the Master (never a sub-agent), so the {@code callerTaskId} scoping arg is
 * always {@code null}.
 *
 * <p>{@code ship}/{@code review} are not here yet: they delegate GitLab/Jira work to the sub-agent
 * (its own MCP) via {@code write_task_context}; that lands with the delegation layer.
 */
@Component
@Order(Integer.MAX_VALUE)
public class MasterShell implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterShell.class);

    private final OrchestratorTools tools;
    private final DashboardRenderer dashboard;
    private final ConfigService configService;
    private final MasterAssistant assistant;

    public MasterShell(OrchestratorTools tools, DashboardRenderer dashboard, ConfigService configService,
                       MasterAssistant assistant) {
        this.tools = tools;
        this.dashboard = dashboard;
        this.configService = configService;
        this.assistant = assistant;
    }

    @Override
    public void run(ApplicationArguments args) {
        Terminal terminal;
        try {
            terminal = TerminalBuilder.builder().system(true).name("jawo").build();
        } catch (IOException e) {
            log.warn("No terminal available — Master shell disabled ({})", e.getMessage());
            return;
        }
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();
        var w = terminal.writer();
        w.println("jawo — Master control terminal. Type 'help'. Ctrl-D to detach (agents keep running).");
        w.println();
        w.println(dashboard.render());
        w.flush();
        while (true) {
            String line;
            try {
                line = reader.readLine("jawo> ");
            } catch (UserInterruptException e) {
                continue;
            } catch (EndOfFileException e) {
                break;
            }
            if (line == null || line.isBlank()) {
                continue;
            }
            if (line.strip().equals("quit") || line.strip().equals("exit")) {
                break;
            }
            w.println(dispatch(line.strip()));
            w.flush();
        }
    }

    private String dispatch(String line) {
        List<String> tok = List.of(line.split("\\s+"));
        String cmd = tok.get(0);
        try {
            String result = switch (cmd) {
                case "status" -> "";
                case "help" -> help();
                case "do" -> doTask(tok);
                case "resume" -> resumeTask(tok);
                case "review" -> reviewTask(tok);
                case "focus" -> tools.focusTask(arg(tok, 1, "focus <ticket>"));
                case "ide" -> tools.openInIde(arg(tok, 1, "ide <ticket> [project]"),
                        tok.contains("project") ? "project" : "diff", null);
                case "deploy" -> tools.deployTask(arg(tok, 1, "deploy <ticket>"), null);
                case "respawn" -> tools.openTaskTab(arg(tok, 1, "respawn <ticket>"), null);
                case "done" -> tools.removeTask(arg(tok, 1, "done <ticket>"), null);
                default -> "unknown command '" + cmd + "' — try 'help'";
            };
            return result.isBlank() ? dashboard.render() : result + "\n\n" + dashboard.render();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "error: " + e.getMessage();
        }
    }

    private String doTask(List<String> tok) {
        String ticket = arg(tok, 1, "do <ticket> [project] [plan]");
        String mode = tok.contains("plan") ? "plan" : null;
        String explicit = tok.stream().skip(2).filter(t -> !t.equals("plan")).findFirst().orElse(null);

        // Explicit project given: skip the ticket read, agent distills the ticket itself.
        if (explicit != null) {
            return tools.initializeTask(ticket, resolveProject(explicit),
                    "Read " + ticket + " via your Jira MCP and implement it.", mode, null, null);
        }
        // No project: read the ticket once (headless) to resolve the project by labels + grab the title.
        var facts = assistant.readTicket(ticket);
        if (facts.isPresent() && !facts.get().exists()) {
            return "error: Jira issue " + ticket + " not found";
        }
        if (facts.isPresent()) {
            TicketFacts f = facts.get();
            String instructions = "Implement Jira " + ticket + " — \"" + f.title()
                    + "\". Read the ticket via your Jira MCP for full details, then work.";
            return tools.initializeTask(ticket, resolveByLabels(f), instructions, mode, null, f.title());
        }
        // Assistant unavailable — fall back to single-project or the explicit-project error.
        return tools.initializeTask(ticket, resolveProject(null),
                "Read " + ticket + " via your Jira MCP and implement it.", mode, null, null);
    }

    /**
     * Reopened MR: `resume <mr-url>` — the MR is enough. The assistant reads it for the source branch
     * (= the task) and project; jawo resumes that branch + links the MR at CI_POLLING (no new MR). An
     * explicit ticket token may be given to skip the lookup.
     */
    private String resumeTask(List<String> tok) {
        String mrUrl = tok.stream().skip(1).filter(t -> t.startsWith("http")).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("usage: resume <mr-url>"));
        String ticket = tok.stream().skip(1).filter(t -> !t.startsWith("http")).findFirst().orElse(null);
        if (ticket == null) {
            var mr = assistant.readMergeRequest(mrUrl);
            if (mr.isEmpty() || !mr.get().exists()) {
                return "error: could not read MR (or not found): " + mrUrl;
            }
            ticket = mr.get().sourceBranch();
        }
        return tools.resumeTask(ticket, mrUrl);
    }

    /**
     * MR sweep: pull the MR's pipeline + unresolved comments (headless) and relay ONE brief to the
     * agent via task_context.md, which fixes locally and drafts replies. Nothing is pushed/posted.
     */
    private String reviewTask(List<String> tok) {
        String ticket = arg(tok, 1, "review <ticket>");
        String mrUrl = tools.taskMrUrl(ticket);
        if (mrUrl == null || mrUrl.isBlank()) {
            return "error: no MR linked to " + ticket + " — `ship` or `resume <mr-url>` first";
        }
        var sweep = assistant.readReview(mrUrl);
        if (sweep.isEmpty() || !sweep.get().exists()) {
            return "error: could not read the MR review for " + mrUrl;
        }
        var r = sweep.get();
        boolean pipelineFailed = r.pipelineStatus() != null && r.pipelineStatus().toLowerCase().contains("fail");
        if (r.comments().isEmpty() && !pipelineFailed) {
            return "review " + ticket + ": pipeline " + r.pipelineStatus()
                    + ", no unresolved comments — your move: `deploy` or `done`";
        }
        StringBuilder brief = new StringBuilder("Review round for MR ").append(mrUrl).append(".\n");
        if (pipelineFailed) {
            brief.append("Pipeline: ").append(r.pipelineStatus()).append(" — fix the failing build.\n");
        }
        if (!r.comments().isEmpty()) {
            brief.append("Unresolved comments — fix the valid ones LOCALLY (no commit/push) and draft a"
                    + " reply for EACH in review_replies.md:\n");
            r.comments().forEach(c -> brief.append("- ").append(c).append('\n'));
        }
        brief.append("When done, set status REVIEW_PENDING. Do NOT push or post anything yourself.");
        tools.writeTaskContext(ticket, brief.toString());
        return "review " + ticket + ": relayed " + r.comments().size() + " comment(s), pipeline "
                + r.pipelineStatus() + " -> agent";
    }

    /** Picks the jawo project whose configured labels intersect the ticket's labels (or Jira key). */
    private String resolveByLabels(TicketFacts f) {
        Map<String, List<String>> projectLabels = new java.util.LinkedHashMap<>();
        configService.load().projects().forEach((k, v) -> projectLabels.put(k, v.labels()));
        List<String> matches = projectsMatching(f, projectLabels);
        if (matches.size() == 1) {
            return matches.get(0);
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("no project matches ticket labels " + f.labels()
                    + " — specify: do <ticket> <project>");
        }
        throw new IllegalArgumentException("ticket labels match multiple projects " + matches
                + " — specify: do <ticket> <project>");
    }

    /** Pure: the project keys whose labels intersect the ticket's labels or its Jira project key. */
    static List<String> projectsMatching(TicketFacts f, Map<String, List<String>> projectLabels) {
        Set<String> tokens = new HashSet<>(f.labels());
        tokens.add(f.jiraProject());
        return projectLabels.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().stream().anyMatch(tokens::contains))
                .map(Map.Entry::getKey)
                .toList();
    }

    private String resolveProject(String project) {
        Set<String> keys = configService.load().projects().keySet();
        if (project != null && !project.isBlank()) {
            if (!keys.contains(project)) {
                throw new IllegalArgumentException("unknown project '" + project + "'. Configured: " + keys);
            }
            return project;
        }
        if (keys.size() == 1) {
            return keys.iterator().next();
        }
        throw new IllegalArgumentException("multiple projects " + keys + " — specify one: do <ticket> <project>");
    }

    private static String arg(List<String> tok, int i, String usage) {
        if (tok.size() <= i || tok.get(i).isBlank()) {
            throw new IllegalArgumentException("usage: " + usage);
        }
        return tok.get(i);
    }

    private static String help() {
        List<String> lines = new ArrayList<>();
        lines.add("commands (task = ticket id or alias):");
        lines.add("  status                       show the dashboard");
        lines.add("  do <ticket> [project] [plan] spin up a sub-agent in a worktree");
        lines.add("  resume <mr-url>              reopened MR: resume its branch + link it -> CI_POLLING");
        lines.add("  focus <ticket>               jump to the agent's window (talk to it there)");
        lines.add("  review <ticket>              pull the MR's pipeline + comments, relay them to the agent");
        lines.add("  ide <ticket> [project]       diff of changes vs base (or full project)");
        lines.add("  deploy <ticket>              merge task branch into deployBranch + push");
        lines.add("  respawn <ticket>             restart a dead agent session");
        lines.add("  done <ticket>                full cleanup (window, worktree, state; branch kept)");
        lines.add("  help | quit                  this reference | detach (agents keep running)");
        return String.join("\n", lines);
    }
}
