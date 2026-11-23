package dev.jagt.orchestrator.service.codereview;

import java.util.List;
import java.util.Map;
import lombok.With;

/**
 * What the code reviewer keeps of one request: the heads it reviewed, the keys of the notes it
 * posted, the answers it gave per thread, the last reaction counts read per note ("likes/dislikes")
 * and whether the request is over. {@code taskId} is blank for a request no task holds.
 */
public record LedgerRow(
    String url,
    String project,
    String taskId,
    @With List<String> heads,
    @With List<String> keys,
    @With Map<String, Integer> answers,
    @With Map<String, String> counts,
    @With boolean ended) {

  public LedgerRow {
    heads = List.copyOf(heads);
    keys = List.copyOf(keys);
    answers = Map.copyOf(answers);
    counts = Map.copyOf(counts);
  }

  public static LedgerRow of(String url, String project, String taskId) {
    return new LedgerRow(url, project, taskId, List.of(), List.of(), Map.of(), Map.of(), false);
  }
}
