package dev.jagt.orchestrator.service.release;

import java.util.regex.Pattern;

/** What a kept release is called under {@code versions/<v>/}. */
public final class Versions {

  public static final String DIR = "versions";
  public static final String JAR = "jagt.jar";
  public static final String SUM = "jagt.jar.sha256";

  private static final Pattern VERSION = Pattern.compile("\\d{1,9}(\\.\\d{1,9})*");

  private Versions() {}

  public static boolean valid(String version) {
    return VERSION.matcher(version).matches();
  }
}
