package dev.jagt.orchestrator.service.git;

import org.jspecify.annotations.Nullable;

public final class Shas {

  private Shas() {}

  public static String shortSha(@Nullable String sha) {
    return sha == null || sha.length() < 8 ? String.valueOf(sha) : sha.substring(0, 8);
  }
}
