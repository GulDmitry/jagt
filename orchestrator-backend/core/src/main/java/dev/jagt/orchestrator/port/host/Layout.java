package dev.jagt.orchestrator.port.host;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** Every path under the data root; the only place one is built. */
public class Layout {

  private static final String TASKS = "tasks";
  private static final String SCRATCH = "scratch";
  private static final String MD = ".md";

  private final Path data;
  private final Path stateFile;

  public Layout(Path data, @Nullable Path stateFile) {
    this.data = data;
    this.stateFile = stateFile != null ? stateFile : data.resolve("jagt").resolve("state.json");
  }

  public Path data() {
    return data;
  }

  public Path stateFile() {
    return stateFile;
  }

  /** The folders jagt creates at start. */
  public List<Path> folders() {
    return List.of(
        stateFile.getParent(), clone(""), tasks(), archive(), records(), memory(), logs());
  }

  public Path tasks() {
    return data.resolve(TASKS);
  }

  public Path worktree(String taskSlug, String projectKey) {
    return worktrees(taskSlug).resolve(projectKey);
  }

  public Path deployWorktree(String taskSlug, String projectKey) {
    return worktrees(taskSlug).resolve(projectKey + "-deploy");
  }

  public Path revertWorktree(String taskSlug, String projectKey) {
    return worktrees(taskSlug).resolve(projectKey + "-revert");
  }

  public Path worktrees(String taskSlug) {
    return task(taskSlug).resolve("worktrees");
  }

  /** Where an older version cut a project's worktrees, one folder per project. */
  public Path legacyWorktrees(String projectKey) {
    return data.resolve("worktrees").resolve(projectKey);
  }

  public Path planBaseline(String taskSlug) {
    return task(taskSlug).resolve("plan-baseline.md");
  }

  public Path ticketText(String taskSlug) {
    return task(taskSlug).resolve("ticket-text.md");
  }

  public Path scratch(String taskSlug) {
    return task(taskSlug).resolve(SCRATCH);
  }

  /**
   * Whether {@code folder} is where the task's scratch was cut: in its folder, or, for a task an
   * older version cut, {@code scratch/<slug>} wherever that older data folder lay.
   */
  public boolean isScratchOf(Path folder, String taskSlug) {
    Path normal = folder.toAbsolutePath().normalize();
    Path parent = normal.getParent();
    return normal.equals(scratch(taskSlug))
        || (parent != null
            && normal.getFileName().toString().equals(taskSlug)
            && SCRATCH.equals(String.valueOf(parent.getFileName())));
  }

  /** The folder git must not climb out of from a scratch folder, else nothing. */
  public @Nullable Path scratchCeiling(Path folder) {
    Path normal = folder.toAbsolutePath().normalize();
    Path parent = normal.getParent();
    if (parent == null) {
      return null;
    }
    if (SCRATCH.equals(String.valueOf(normal.getFileName()))
        && tasks().equals(parent.getParent())) {
      return tasks();
    }
    return SCRATCH.equals(String.valueOf(parent.getFileName())) ? parent : null;
  }

  public Path roleBranchWorktree(String projectKey, String branch) {
    return data.resolve("role-branches").resolve(projectKey + "-" + branch.replace('/', '-'));
  }

  public Path clone(String projectKey) {
    return data.resolve("projects").resolve(projectKey);
  }

  public Path archive() {
    return data.resolve("archive");
  }

  public Path records() {
    return data.resolve("records");
  }

  public Path memory() {
    return data.resolve("memory");
  }

  public Path logs() {
    return data.resolve("logs");
  }

  public Path actStopped() {
    return beside("act-stopped");
  }

  public Path boardStopped() {
    return beside("board-stopped");
  }

  public Path actStrikes() {
    return beside("act-strikes");
  }

  public Path token() {
    return beside("master-token");
  }

  public Path alive() {
    return beside("alive");
  }

  public Path intakeTurnedAway() {
    return beside("intake-turned-away");
  }

  public Path checkerVerdicts(String role) {
    return beside(role + "-verdicts");
  }

  public Path legacyQaVerdicts() {
    return beside("qa-verdicts");
  }

  public Path usageAlerts() {
    return beside("usage-alerts.properties");
  }

  public Path openCodeGate() {
    return beside("opencode-gate");
  }

  public Path usageByDay() {
    return record("usage-by-day.jsonl");
  }

  public Path finished() {
    return record("finished.jsonl");
  }

  public Path misses() {
    return record("misses.jsonl");
  }

  public Path interactions() {
    return record("interactions.jsonl");
  }

  public Path stops() {
    return record("stops.jsonl");
  }

  public Path stopsReviewed() {
    return record("stops-reviewed.jsonl");
  }

  public Path codeReviewLedger() {
    return data.resolve("jagt").resolve("code-review.json");
  }

  public Path codeReviewCheckouts() {
    return data.resolve("jagt").resolve("code-review").resolve("checkouts");
  }

  public Path codeReviewStats() {
    return record("code-review.jsonl");
  }

  /**
   * What an older version kept flat beside its state, by that name, with where it belongs now.
   * {@code master-token} is absent: it is drawn anew at each start.
   */
  public Map<String, Path> placed() {
    Map<String, Path> placed = new LinkedHashMap<>();
    placed.put("state.json", stateFile);
    placed.put("state.json.bak", beside(stateFile.getFileName() + ".bak"));
    placed.put("state.json.corrupt", beside(stateFile.getFileName() + ".corrupt"));
    placed.put("act-stopped", actStopped());
    placed.put("board-stopped", boardStopped());
    placed.put("act-strikes", actStrikes());
    placed.put("alive", alive());
    placed.put("intake-turned-away", intakeTurnedAway());
    placed.put("usage-alerts.properties", usageAlerts());
    placed.put("opencode-gate", openCodeGate());
    for (Path record :
        List.of(usageByDay(), finished(), misses(), interactions(), stops(), stopsReviewed())) {
      placed.put(record.getFileName().toString(), record);
      placed.put(record.getFileName() + ".1", record(record.getFileName() + ".1"));
    }
    return placed;
  }

  /** Each older folder, with where an entry of it named so belongs now. */
  public Map<String, Function<String, Path>> placedEntries() {
    Map<String, Function<String, Path>> entries = new LinkedHashMap<>();
    entries.put("plan-baselines", name -> planBaseline(stem(name)));
    entries.put("ticket-texts", name -> ticketText(stem(name)));
    entries.put("archive", archive()::resolve);
    entries.put("repos", this::clone);
    entries.put("logs", logs()::resolve);
    entries.put("memory", memory()::resolve);
    return entries;
  }

  /** The file a checker's verdicts were kept in beside the old state, else nothing. */
  public @Nullable Path placedVerdicts(String name) {
    return name.endsWith("-verdicts") ? beside(name) : null;
  }

  private static String stem(String fileName) {
    return fileName.endsWith(MD)
        ? fileName.substring(0, fileName.length() - MD.length())
        : fileName;
  }

  public Path task(String taskSlug) {
    return tasks().resolve(taskSlug);
  }

  private Path record(String name) {
    return records().resolve(name);
  }

  private Path beside(String name) {
    return stateFile.resolveSibling(name);
  }
}
