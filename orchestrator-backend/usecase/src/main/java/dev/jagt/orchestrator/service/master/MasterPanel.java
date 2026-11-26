package dev.jagt.orchestrator.service.master;

import dev.jagt.orchestrator.port.assistant.RoundReviewer;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Judgement;
import dev.jagt.orchestrator.port.assistant.RoundReviewer.Press;
import dev.jagt.orchestrator.port.host.Processes;
import dev.jagt.orchestrator.service.ConfigFile;
import dev.jagt.orchestrator.service.OneLine;
import dev.jagt.orchestrator.service.master.MasterReview.Review;
import dev.jagt.orchestrator.service.worktree.WorktreeFiles;
import dev.jagt.orchestrator.task.TaskState;
import dev.jagt.orchestrator.task.master.MasterRight;
import dev.jagt.orchestrator.task.repo.TaskRepo;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Each role the brief names reads the round on its own, without the others' reasoning; the verdict
 * is then the code's: a question or an unreadable round goes to the human, any finding back to the
 * session, and ready only where every role found nothing and proved what it rested on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterPanel {

  private final ChargedReviews reviews;
  private final MasterBriefs briefs;
  private final RoundQuotes quotes;
  private final MasterReview stored;
  private final RoleYields yields;

  static final String HUMAN_UNREAD = "what the human typed to the session could not be read";

  /** What jagt read for a round once, quoted to every role. */
  record RoundRead(String ticket, String diff, String decided, List<String> said, String notes) {}

  /**
   * {@code model} blank reads with the Master's own model; {@code applies} empty reads every round;
   * {@code unless} names a role whose reading this one yields to, blank where none.
   */
  public record Role(
      String name, String question, String model, List<String> applies, String unless) {

    String modelOr(String masters) {
      return model.isBlank() ? masters : model;
    }

    /**
     * True where the changed paths are unknown; a {@code !} glob names paths the role never reads.
     */
    boolean reads(Optional<List<String>> changed) {
      if (applies.isEmpty() || changed.isEmpty()) {
        return true;
      }
      List<String> never =
          applies.stream()
              .filter(glob -> glob.startsWith("!"))
              .map(glob -> glob.substring(1))
              .toList();
      List<String> only = applies.stream().filter(glob -> !glob.startsWith("!")).toList();
      List<String> left =
          changed.get().stream()
              .filter(path -> WorktreeFiles.firstMatching(never, List.of(path)).isEmpty())
              .toList();
      return only.isEmpty() ? !left.isEmpty() : WorktreeFiles.firstMatching(only, left).isPresent();
    }
  }

  private static MasterVerdictFile.Skipped skipped(Role role, boolean yielded) {
    return new MasterVerdictFile.Skipped(
        role.name(),
        yielded
            ? "the " + role.unless() + " role reads it"
            : "no changed path matches " + String.join(", ", role.applies()));
  }

  /**
   * Writes the round's review file; false where the brief could not be read, and nothing was asked.
   */
  public boolean review(String taskId, TaskState task, ConfigFile.ArbiterConfig config) {
    Optional<String> masters = briefs.master(taskId, config);
    if (masters.isEmpty()) {
      return false;
    }
    String inputs = quotes.inputs(taskId, task).orElse("");
    if (quotes.carried(taskId, task, inputs)) {
      return true;
    }
    log.atInfo()
        .setMessage("master review asked")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("trigger", quotes.trigger(task, inputs))
        .addKeyValue("fingerprint", quotes.fingerprint(task, inputs))
        .log();
    String brief = masters.get();
    List<Path> worktrees = worktrees(task);
    List<Role> named = roles(brief, briefs.author());
    Optional<List<String>> changed =
        named.stream().allMatch(role -> role.applies().isEmpty())
            ? Optional.empty()
            : quotes.paths(task);
    List<Role> applying =
        named.stream()
            .filter(role -> role.reads(changed) && !yields.yields(role.unless(), task))
            .toList();
    // A round no role reads would pass unread.
    List<Role> roles = applying.isEmpty() ? named : applying;
    String shared =
        MasterPrompts.shared(
            brief, briefs.author(), config.may(MasterRight.ANSWER), reviews.loadsMcpServer());
    Optional<RoundRead> read = quotes.round(taskId, task);
    List<Judgement> judgements = new ArrayList<>();
    if (read.isEmpty()) {
      roles.forEach(role -> judgements.add(Judgement.failed(HUMAN_UNREAD)));
    } else {
      // Rules and format after the quote was tried 2026-10-18: same verdicts, $2.05 against $2.02
      // on the master eval.
      String round = shared + "\n\n" + MasterPrompts.quote(taskId, task, read.get());
      List<RoundReviewer.Round> rounds =
          roles.stream()
              .map(role ->
                  new RoundReviewer.Round(
                      round,
                      MasterPrompts.prompt(role),
                      worktrees,
                      role.modelOr(config.modelOrInherited())))
              .toList();
      try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
        // Started together, every role writes the round to the cache; after the first, the rest
        // read it.
        judgements.add(read(threads.submit(() -> reviews.review(taskId, rounds.getFirst()))));
        List<Future<Judgement>> asked =
            rounds.stream()
                .skip(1)
                .map(next -> threads.submit(() -> reviews.review(taskId, next)))
                .toList();
        for (Future<Judgement> answer : asked) {
          judgements.add(read(answer));
        }
      } catch (InterruptedException stopped) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    List<MasterReview.Read> reads = MasterVerdictFile.reads(roles, judgements);
    boolean allRead = judgements.stream().allMatch(judgement -> judgement.failure().isBlank());
    Review review = Review.of(task, allRead ? quotes.ownDiff(task).orElse("") : "", inputs, reads);
    return stored.write(
        taskId,
        task,
        review,
        MasterVerdictFile.of(
            taskId,
            reads,
            named.stream()
                .filter(role -> !roles.contains(role))
                .map(role -> skipped(role, yields.yields(role.unless(), task)))
                .toList()));
  }

  /**
   * One read, not one per role: a plan answers a single question, whether it does what the ticket
   * asks.
   */
  public boolean plan(String taskId, TaskState task, ConfigFile.ArbiterConfig config) {
    Optional<String> brief = briefs.master(taskId, config);
    if (brief.isEmpty()) {
      return false;
    }
    log.atInfo()
        .setMessage("master review asked")
        .addKeyValue("task", taskId)
        .addKeyValue("alias", task.ticket().alias())
        .addKeyValue("trigger", "plan")
        .addKeyValue(
            "fingerprint", quotes.planFingerprint(task, quotes.inputs(taskId, task).orElse("")))
        .log();
    Optional<RoundRead> read = quotes.plan(taskId, task);
    Judgement judged =
        read.isEmpty()
            ? Judgement.failed(HUMAN_UNREAD)
            : reviews.review(
                taskId,
                new RoundReviewer.Round(
                    "",
                    MasterPrompts.planPrompt(
                        taskId,
                        task,
                        brief.get(),
                        quotes.planFile(task),
                        read.get(),
                        config.may(MasterRight.ANSWER),
                        reviews.loadsMcpServer()),
                    worktrees(task),
                    config.modelOrInherited()));
    List<MasterReview.Read> reads = MasterVerdictFile.reads(List.of(PLANNER), List.of(judged));
    return stored.write(
        taskId,
        task,
        Review.of(task, "", "", reads),
        MasterVerdictFile.of(taskId, reads, List.of()));
  }

  private static final Role PLANNER =
      new Role("planner", "whether the plan does what the ticket asks", "", List.of(), "");

  /** What a question came back with: a decision, or why none came. */
  public sealed interface Answer permits Decision, Undecided {}

  /**
   * {@code newTask} is work the decision would open as a task of its own, blank where none. {@code
   * press} is the move the decision is, or words.
   */
  public record Decision(String text, String newTask, Press press) implements Answer {}

  public record Undecided(String cause) implements Answer {}

  /** The session's question decided as the human would. */
  public Answer answer(
      String taskId,
      TaskState task,
      String question,
      ConfigFile.ArbiterConfig config,
      boolean stuck) {
    Optional<String> brief = briefs.master(taskId, config);
    if (brief.isEmpty()) {
      return new Undecided("the master brief could not be read");
    }
    Optional<RoundRead> read = quotes.question(task);
    if (read.isEmpty()) {
      return new Undecided("what the human typed could not be read");
    }
    List<NamedTickets.Fact> tracker = quotes.named(taskId, question);
    Optional<NamedTickets.Fact> unread = tracker.stream().filter(fact -> !fact.read()).findFirst();
    if (unread.isPresent()) {
      return new Undecided("the tracker could not say about " + unread.get().key());
    }
    Judgement judged =
        reviews.review(
            taskId,
            new RoundReviewer.Round(
                "",
                MasterPrompts.answerPrompt(
                    taskId,
                    task,
                    new Briefs(brief.get(), briefs.author()),
                    new Asked(
                        question,
                        read.get().decided(),
                        read.get().said(),
                        tracker.stream().map(NamedTickets.Fact::line).toList(),
                        stuck),
                    reviews.loadsMcpServer()),
                worktrees(task),
                config.modelOrInherited()));
    if (!judged.failure().isBlank()) {
      return new Undecided(judged.failure());
    }
    if (judged.findings().isEmpty()) {
      return new Undecided("the read returned no findings");
    }
    return new Decision(
        judged.findings().stream()
            .map(f -> OneLine.of(f.issue()))
            .collect(Collectors.joining("\n")),
        judged.newTask().strip(),
        judged.press());
  }

  private static List<Path> worktrees(TaskState task) {
    return task.repos().stream().map(TaskRepo::worktreePath).map(Path::of).toList();
  }

  private static Judgement read(Future<Judgement> answer) throws InterruptedException {
    try {
      return answer.get();
    } catch (ExecutionException thrown) {
      if (thrown.getCause() instanceof Processes.Stopped unjudged) {
        throw unjudged;
      }
      return Judgement.failed("the review threw " + thrown.getCause());
    }
  }

  /**
   * The Master's own table where its brief has one, else the author's: the Master extends the
   * session.
   */
  static List<Role> roles(String brief, String authorBrief) {
    List<Role> own = table(brief);
    List<Role> roles = own.isEmpty() ? table(authorBrief) : own;
    return roles.isEmpty()
        ? List.of(new Role("reviewer", "every question your brief asks", "", List.of(), ""))
        : roles;
  }

  /** Columns past the question are found by their header's name. */
  private static List<Role> table(String brief) {
    List<Role> roles = new ArrayList<>();
    List<String> header = List.of();
    for (String line : brief.lines().map(String::strip).toList()) {
      List<String> cells =
          line.startsWith("|")
              ? Arrays.stream(line.substring(1).split("\\|")).map(String::strip).toList()
              : List.of();
      if (cells.size() < 2) {
        header = List.of();
      } else if (cells.getFirst().equalsIgnoreCase("role")) {
        header = cells.stream().map(cell -> cell.toLowerCase(Locale.ROOT)).toList();
      } else if (!header.isEmpty() && !cells.getFirst().startsWith("-")) {
        roles.add(
            new Role(
                cells.get(0),
                cells.get(1),
                cell(cells, header, "model"),
                GLOBS
                    .splitAsStream(cell(cells, header, "applies").replace("`", ""))
                    .filter(glob -> !glob.isEmpty())
                    .toList(),
                cell(cells, header, "unless")));
      }
    }
    return roles;
  }

  /** Commas and blanks part globs, but not inside a brace's alternatives. */
  private static final Pattern GLOBS = Pattern.compile("[,\\s]+(?![^{]*})");

  private static String cell(List<String> cells, List<String> header, String column) {
    int at = header.indexOf(column);
    return at > 1 && at < cells.size() ? cells.get(at) : "";
  }

  record Briefs(String judgeBy, String worksTo) {}

  record Asked(
      String question, String decided, List<String> said, List<String> tracker, boolean stuck) {}
}
