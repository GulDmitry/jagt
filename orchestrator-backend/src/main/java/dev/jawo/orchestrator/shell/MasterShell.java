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
import org.jline.utils.AttributedString;
import org.jline.utils.Status;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

    /** Serializes ALL terminal writes off the reader thread: the refresh ticker, printAbove, and status.close. */
    private final Object paintLock = new Object();
    /** True once the dashboard lives in a pinned status region: dispatch then returns only the command result. */
    private volatile boolean dashboardPinned;
    /** Set under {@link #paintLock} on shutdown so a late tick never paints into a closing status region. */
    private boolean stopped;

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

        int refreshSeconds = configService.load().dashboardRefreshSecondsOrDefault();
        boolean dumb = terminal.getType() == null || terminal.getType().startsWith(Terminal.TYPE_DUMB);
        Status status = refreshSeconds > 0 && !dumb ? Status.getStatus(terminal, true) : null;
        dashboardPinned = status != null;
        ScheduledExecutorService ticker = null;
        if (dashboardPinned) {
            paintDashboard(status, terminal);
            ticker = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "jawo-dashboard-refresh");
                t.setDaemon(true);
                return t;
            });
            ticker.scheduleWithFixedDelay(() -> {
                try {
                    paintDashboard(status, terminal);
                } catch (RuntimeException e) {
                    log.debug("dashboard refresh failed: {}", e.toString());
                }
            }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
        } else {
            w.println(dashboard.render());
            w.flush();
        }

        try {
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
                String out = dispatch(line.strip());
                if (dashboardPinned) {
                    // printAbove + repaint must be atomic against the ticker, else their escape
                    // sequences interleave and mangle the cursor. Both under paintLock.
                    synchronized (paintLock) {
                        if (!out.isBlank()) {
                            reader.printAbove(out);
                        }
                        paintLocked(status, terminal);
                    }
                } else {
                    w.println(out);
                    w.flush();
                }
            }
        } finally {
            if (ticker != null) {
                ticker.shutdownNow();
                try {
                    ticker.awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            synchronized (paintLock) {
                stopped = true;
                if (status != null) {
                    status.close();
                }
            }
        }
    }

    /** Repaints the pinned dashboard region in place (no scrollback growth); ANSI colors preserved. */
    private void paintDashboard(Status status, Terminal terminal) {
        synchronized (paintLock) {
            paintLocked(status, terminal);
        }
    }

    /** Caller MUST hold {@link #paintLock}. No-op once {@link #stopped}, so a late tick never paints a closed region. */
    private void paintLocked(Status status, Terminal terminal) {
        if (stopped) {
            return;
        }
        List<AttributedString> lines = new ArrayList<>();
        for (String line : dashboard.render().split("\\R")) {
            lines.add(AttributedString.fromAnsi(line));
        }
        status.update(lines);
        terminal.flush();
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
                case "ship" -> tools.ship(arg(tok, 1, "ship <ticket>"));
                case "focus" -> tools.focusTask(arg(tok, 1, "focus <ticket>"));
                case "ide" -> tools.openInIde(arg(tok, 1, "ide <ticket> [diff]"),
                        tok.contains("diff") ? "diff" : "project", null);
                case "deploy" -> tools.deployTask(arg(tok, 1, "deploy <ticket>"), null);
                case "respawn" -> tools.openTaskTab(arg(tok, 1, "respawn <ticket>"), null);
                case "done" -> tools.removeTask(arg(tok, 1, "done <ticket>"), null);
                default -> "unknown command '" + cmd + "' — try 'help'";
            };
            return withDashboard(result, dashboardPinned, dashboardPinned ? "" : dashboard.render());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return "error: " + e.getMessage();
        }
    }

    /**
     * What the command loop prints. When the dashboard is pinned it lives in the status region, so only the
     * command result goes to the scrollback; otherwise the dashboard is appended (blank result = dashboard alone).
     */
    static String withDashboard(String result, boolean pinned, String dashboardText) {
        if (pinned) {
            return result;
        }
        return result.isBlank() ? dashboardText : result + "\n\n" + dashboardText;
    }

    /** A bare issue key like {@code ABC-123} — used only to skip the read on the fast path, never parsed out of a URL. */
    private static final Pattern KEY_REF = Pattern.compile("[A-Za-z][A-Za-z0-9]*-[0-9]+");

    String doTask(List<String> tok) {
        String ref = arg(tok, 1, "do <ticket|url> [project] [plan]");
        String mode = tok.contains("plan") ? "plan" : null;
        String explicit = tok.stream().skip(2).filter(t -> !t.equals("plan")).findFirst().orElse(null);
        boolean bareKey = KEY_REF.matcher(ref).matches();

        // Fast path: a bare key + explicit project needs no read — the key IS the task id.
        if (bareKey && explicit != null) {
            return tools.initializeTask(ref, resolveProject(explicit),
                    "Read " + ref + " via your issue-tracker MCP and implement it.", mode, null, null);
        }
        // Otherwise read the item. `ref` may be a KEY or a URL to any tracker — the assistant follows it
        // and returns the canonical key (jawo names the branch/worktree by it; it is NOT parsed from a URL).
        var facts = assistant.readTicket(ref);
        if (facts.isPresent() && !facts.get().exists()) {
            return "error: could not read " + ref + " (bad/inaccessible URL or unknown key?)";
        }
        if (facts.isPresent()) {
            TicketFacts f = facts.get();
            // Name the task by the canonical key the assistant read back; if it returned none but the
            // caller already gave a bare key, that key is a fine task id (a URL has no such fallback).
            String taskId = f.key() != null && !f.key().isBlank() ? f.key() : (bareKey ? ref : null);
            if (taskId == null) {
                return "error: read " + ref + " but the assistant returned no issue key to name the task";
            }
            String project = explicit != null ? resolveProject(explicit) : resolveByLabels(f);
            String instructions = "Implement " + taskId + " — \"" + f.title()
                    + "\". Read it via your issue-tracker MCP for full details, then work.";
            return tools.initializeTask(taskId, project, instructions, mode, null, f.title());
        }
        // Assistant unavailable: only a bare key can proceed — a URL has no derivable task id without it.
        if (!bareKey) {
            return "error: assistant unavailable — pass an issue key (not a URL), or add the project";
        }
        return tools.initializeTask(ref, resolveProject(explicit),
                "Read " + ref + " via your issue-tracker MCP and implement it.", mode, null, null);
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
            brief.append("Unresolved comments — fix the valid ones LOCALLY (no commit/push). For EACH"
                    + " comment write a block in review_replies.md: the original comment (with its thread"
                    + " link if available) followed by the reply you intend to post:\n");
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
        lines.add("  ship <ticket>                approve: agent commits (pattern title), pushes, MR, posts replies");
        lines.add("  review <ticket>              pull the MR's pipeline + comments, relay them to the agent");
        lines.add("  ide <ticket> [diff]          open worktree project (live Git diff); `diff` = static snapshot vs base");
        lines.add("  deploy <ticket>              merge task branch into deployBranch + push");
        lines.add("  respawn <ticket>             restart a dead agent session");
        lines.add("  done <ticket>                full cleanup (window, worktree, state; branch kept)");
        lines.add("  help | quit                  this reference | detach (agents keep running)");
        return String.join("\n", lines);
    }
}
