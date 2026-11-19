package dev.jagt.orchestrator.job.release;

import java.util.regex.Pattern;

/** What a kept release is called under {@code versions/<v>/}. */
final class Versions {

  static final String DIR = "versions";
  static final String JAR = "jagt.jar";
  static final String SUM = "jagt.jar.sha256";

  private static final Pattern VERSION = Pattern.compile("\\d{1,9}(\\.\\d{1,9})*");

  private Versions() {}

  static boolean valid(String version) {
    return VERSION.matcher(version).matches();
  }
}
