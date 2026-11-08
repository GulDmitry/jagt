package dev.jagt.orchestrator.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * The models a session may be moved up through, the one it started on first, and the rung it runs
 * on; the first may be blank, which is the agent CLI's own default.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelTier(List<String> models, int rung) {

  public ModelTier {
    models = List.copyOf(models);
    rung = Math.max(0, Math.min(rung, models.size() - 1));
  }

  /**
   * The ladder above {@code start}: {@code start}, then every tier listed after it; a start no tier
   * names stands in for the first. Null where no model is above.
   */
  public static @Nullable ModelTier above(String start, List<String> tiers) {
    int at = Math.max(0, tiers.indexOf(start));
    List<String> above = tiers.subList(Math.min(at + 1, tiers.size()), tiers.size());
    return above.isEmpty()
        ? null
        : new ModelTier(Stream.concat(Stream.of(start), above.stream()).toList(), 0);
  }

  @JsonIgnore
  public String current() {
    return models.get(rung);
  }

  @JsonIgnore
  public String configured() {
    return models.getFirst();
  }

  @JsonIgnore
  public boolean higher() {
    return rung + 1 < models.size();
  }

  @JsonIgnore
  public boolean raised() {
    return rung > 0;
  }

  public ModelTier raise() {
    return new ModelTier(models, rung + 1);
  }

  public ModelTier lower() {
    return new ModelTier(models, 0);
  }
}
