package dev.jagt.orchestrator.command;

import dev.jagt.orchestrator.flow.TaskView;
import dev.jagt.orchestrator.service.ReviewDrafts;
import dev.jagt.orchestrator.service.StateService;
import dev.jagt.orchestrator.service.TaskViews;
import dev.jagt.orchestrator.service.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The round a human approves, on the screen they are already looking at. The drafts stay a file the agent owns —
 * this only reads it — but a file is not where a review round is read: knowing the convention, finding the
 * worktree and opening an editor stand between the human and the answers jagt is about to post in their name.
 *
 * <p>Every line of the file reaches the screen. The shape the round brief prescribes is used to SEPARATE the
 * blocks and lift the verdict out of the prose, and whatever does not fit it is printed as it stands rather than
 * dropped — the file is written by an agent, so a parser that hides what it did not recognise would hide exactly
 * the round that went wrong.
 */
@Service
@RequiredArgsConstructor
public class ReviewRepliesReport {

    /** The shape the round brief prescribes to the agent; anything else is text this cannot separate. */
    private static final Pattern BLOCK = Pattern.compile("^##+\\s*(.*)$");
    private static final Pattern VERDICT = Pattern.compile("^(FIXED|NO CHANGE|QUESTION)\\s*[-–—:]?\\s*(.*)$");

    private final TaskViews views;
    private final StateService state;
    private final ReviewDrafts drafts;

    public String render(String tail) {
        String asked = tail == null ? "" : tail.strip();
        return asked.isEmpty() ? everything() : one(asked);
    }

    private String one(String asked) {
        String id = state.canonicalTaskId(asked);
        Optional<TaskView> view = views.all().stream().filter(task -> task.id().equals(id)).findFirst();
        if (view.isEmpty()) {
            return "no task `" + asked + "`.";
        }
        return section(view.get()).orElse(reference(view.get())
                + " has no drafted replies — review_replies.md is not in its worktree.");
    }

    private String everything() {
        List<String> sections = views.all().stream().map(this::section).flatMap(Optional::stream).toList();
        return sections.isEmpty()
                ? "no drafted review replies: no task is carrying a review_replies.md."
                : String.join("\n\n", sections);
    }

    /**
     * What it asks about is the FILE, never the card's badge: that one is shown only where it is actionable, so a
     * task whose status has moved on would read as holding no drafts while the answers sat in its worktree.
     */
    private Optional<String> section(TaskView task) {
        Optional<TaskState> state = this.state.task(task.id());
        return state.map(TaskState::worktreePath)
                .filter(worktree -> !worktree.isBlank())
                .flatMap(worktree -> WorktreeFiles.read(Path.of(worktree)
                        .resolve(WorktreeFiles.REVIEW_REPLIES)))
                .filter(text -> !text.isBlank())
                .map(text -> header(task, state.filter(drafts::spent).isPresent()) + "\n\n" + body(text));
    }

    /**
     * A spent file is still PRINTED — it is the only record of what was answered — but never advertised as
     * something a ship will send.
     */
    private static String header(TaskView task, boolean spent) {
        return spent
                ? "review replies for " + name(task) + " — drafted in a round already shipped, so these were"
                        + " posted; nothing here is waiting to go out"
                : "review replies drafted for " + name(task) + " — nothing is posted until `ship "
                        + reference(task) + "`";
    }

    private static String name(TaskView task) {
        String reference = reference(task);
        return task.title() == null || task.title().isBlank() ? reference
                : reference + " · " + task.title();
    }

    private static String reference(TaskView task) {
        return task.alias() == null || task.alias().isBlank() ? task.id() : task.alias();
    }

    /**
     * One paragraph per comment, numbered as it is rendered rather than counted up front: a number the file did
     * not carry would be a claim about how many comments the round had, which only the host can make.
     */
    private static String body(String file) {
        List<String> out = new ArrayList<>();
        List<String> block = new ArrayList<>();
        String heading = null;
        int number = 0;
        for (String line : file.strip().lines().toList()) {
            Matcher start = BLOCK.matcher(line);
            if (!start.matches()) {
                block.add(line);
                continue;
            }
            out.add(rendered(heading, block, heading == null ? 0 : ++number));
            heading = start.group(1).strip();
            block = new ArrayList<>();
        }
        out.add(rendered(heading, block, heading == null ? 0 : ++number));
        return String.join("\n\n", out.stream().filter(part -> !part.isBlank()).toList());
    }

    /** {@code number} 0 for text that stands outside every block — it is printed where it was written. */
    private static String rendered(String heading, List<String> lines, int number) {
        List<String> kept = new ArrayList<>(lines.stream().dropWhile(String::isBlank).toList());
        while (!kept.isEmpty() && kept.get(kept.size() - 1).isBlank()) {
            kept.remove(kept.size() - 1);
        }
        if (heading == null) {
            return String.join("\n", kept);
        }
        String verdict = null;
        List<String> rest = new ArrayList<>();
        for (String line : kept) {
            Matcher said = VERDICT.matcher(line.strip());
            if (said.matches() && verdict == null) {
                verdict = said.group(1);
                if (!said.group(2).isBlank()) {
                    rest.add(said.group(2).strip());
                }
                continue;
            }
            rest.add(line.strip());
        }
        StringBuilder paragraph = new StringBuilder("  %d · %s · %s"
                .formatted(number, verdict == null ? "no verdict" : verdict, heading));
        rest.forEach(line -> paragraph.append("\n      ").append(line));
        return paragraph.toString();
    }
}
