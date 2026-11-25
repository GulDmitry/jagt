package dev.jagt.orchestrator.service.role;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The marks one role or the board adds to the head; none where there is nothing to say. */
public interface HeaderChips {

  List<HeaderChip> chips();

  /**
   * One mark in the board's head. {@code key} is its catalog word, {@code key + ".tip"} its hover;
   * {@code verb}, where set, is what pressing it runs; {@code at} is epoch millis; {@code detail},
   * where set, is appended to the hover.
   */
  record HeaderChip(
      String key,
      @Nullable String verb,
      boolean broken,
      @Nullable Long at,
      @Nullable String detail) {}
}
