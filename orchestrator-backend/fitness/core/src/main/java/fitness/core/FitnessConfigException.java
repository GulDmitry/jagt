package fitness.core;

import org.jspecify.annotations.Nullable;

public final class FitnessConfigException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final Reason reason;
  private final String key;

  public FitnessConfigException(Reason reason, String key) {
    this(reason, key, null);
  }

  public FitnessConfigException(Reason reason, String key, @Nullable Throwable cause) {
    super(reason + ": " + key, cause);
    this.reason = reason;
    this.key = key;
  }

  public Reason reason() {
    return reason;
  }

  public String key() {
    return key;
  }
}
