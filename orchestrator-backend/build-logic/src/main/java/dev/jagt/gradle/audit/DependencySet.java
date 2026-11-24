package dev.jagt.gradle.audit;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/** A base the diff cannot be read against scans, as no base does. */
public final class DependencySet {

  private static final Pattern FILES =
      Pattern.compile(
          "(^|/)(buildscript-)?gradle\\.lockfile$"
              + "|(^|/)gradle/libs\\.versions\\.toml$"
              + "|(^|/)gradle/verification-metadata\\.xml$"
              + "|(^|/)package-lock\\.json$");

  private DependencySet() {}

  public static boolean scanNeeded(String base, Function<String, List<String>> changedSince) {
    if (base == null || base.isBlank() || base.equals("all")) {
      return true;
    }
    try {
      return changedSince.apply(base).stream().anyMatch(path -> FILES.matcher(path).find());
    } catch (RuntimeException unreadable) {
      return true;
    }
  }
}
