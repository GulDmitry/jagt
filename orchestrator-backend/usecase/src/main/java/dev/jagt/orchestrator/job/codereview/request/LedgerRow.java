package dev.jagt.orchestrator.job.codereview.request;

import java.util.List;
import java.util.Map;
import lombok.With;

/**
 * What the code reviewer keeps of one request: the heads it reviewed, the keys of the notes it
 * posted, what it learned and spent, and whether the request is over. {@code taskId} is blank for a
 * request no task holds.
 */
public record LedgerRow(
    String url,
    String project,
    String taskId,
    @With List<String> heads,
    @With List<String> keys,
    @With Signals signals,
    @With boolean ended) {

  /**
   * The answers given per thread, the last reaction counts read per note ("likes/dislikes/
   * resolution") and the model spend of the request's third-party reads.
   */
  @With
  public record Signals(Map<String, Integer> answers, Map<String, String> counts, double costUsd) {

    public static final Signals NONE = new Signals(Map.of(), Map.of(), 0);

    public Signals {
      answers = Map.copyOf(answers);
      counts = Map.copyOf(counts);
    }
  }

  public LedgerRow {
    heads = List.copyOf(heads);
    keys = List.copyOf(keys);
  }

  public static LedgerRow of(String url, String project, String taskId) {
    return new LedgerRow(url, project, taskId, List.of(), List.of(), Signals.NONE, false);
  }

  public Map<String, Integer> answers() {
    return signals.answers();
  }

  public Map<String, String> counts() {
    return signals.counts();
  }

  public LedgerRow withAnswers(Map<String, Integer> answers) {
    return withSignals(signals.withAnswers(answers));
  }

  public LedgerRow withCounts(Map<String, String> counts) {
    return withSignals(signals.withCounts(counts));
  }

  public LedgerRow withCostUsd(double costUsd) {
    return withSignals(signals.withCostUsd(costUsd));
  }

  public boolean spentUp(double ceilingUsd) {
    return signals.costUsd() >= ceilingUsd;
  }
}
