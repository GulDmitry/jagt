package dev.jagt.orchestrator.task.review;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * A round written to the session that no report has named yet: neither its tag nor one of its note
 * ids. It ends on such a report, on a newer round, or on a lift.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Untaken(String digest, List<String> noteIds, String brief) {

  public static final int TAG_LENGTH = 8;

  public Untaken {
    noteIds = noteIds == null ? List.of() : List.copyOf(noteIds);
  }

  public String tag() {
    return digest.substring(0, Math.min(TAG_LENGTH, digest.length()));
  }

  public boolean namedBy(@Nullable String report) {
    return report != null
        && Stream.concat(Stream.of(tag()), noteIds.stream())
            .anyMatch(name ->
                Pattern.compile("(?<!\\w)" + Pattern.quote(name) + "(?!\\w)")
                    .matcher(report)
                    .find());
  }
}
