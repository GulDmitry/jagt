package dev.jagt.orchestrator.config.source;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

/**
 * A value naming an input jagt cannot read; the message carries the key and the input, never a
 * value.
 */
@Getter
public class ConfigValueRefused extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  private final String key;
  private final String input;

  public ConfigValueRefused(String key, String input, String problem, @Nullable Throwable cause) {
    super(key + ": " + input + " " + problem, cause);
    this.key = key;
    this.input = input;
  }
}
