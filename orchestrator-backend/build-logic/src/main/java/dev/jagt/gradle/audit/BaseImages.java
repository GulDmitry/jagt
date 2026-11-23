package dev.jagt.gradle.audit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** The base images the registry workflow publishes, as the local audit builds and scans them. */
public final class BaseImages {

  /** One image: its Dockerfile under {@code base/} and the image it builds on, if any. */
  public record Image(String name, String dockerfile, @Nullable String from) {}

  private static final List<Image> ALL =
      List.of(
          new Image("base", "Dockerfile", null),
          new Image("stand-base", "Dockerfile.stand", "base"),
          new Image("ci", "Dockerfile.ci", null));

  private static final Pattern INPUTS =
      Pattern.compile("^base/|^orchestrator-backend/scripts/linux-test-deps\\.sh$");

  private BaseImages() {}

  public static List<Image> all() {
    return ALL;
  }

  /** A base the diff cannot be read against scans, as no base does. */
  public static boolean scanNeeded(
      @Nullable String base, Function<String, List<String>> changedSince) {
    if (base == null || base.isBlank() || base.equals("all")) {
      return true;
    }
    try {
      return changedSince.apply(base).stream().anyMatch(path -> INPUTS.matcher(path).find());
    } catch (RuntimeException unreadable) {
      return true;
    }
  }

  public static String tag(Image image) {
    return "jagt-scan-" + image.name() + ":local";
  }

  public static List<String> build(Path repository, Image image) {
    List<String> command = new ArrayList<>(List.of("docker", "build", "--platform", "linux/amd64"));
    if (image.from() != null) {
      command.addAll(List.of("--build-arg", "BASE=" + tag(byName(image.from()))));
    }
    command.addAll(
        List.of(
            "--build-context",
            "scripts=" + repository.resolve("orchestrator-backend/scripts"),
            "--file",
            repository.resolve("base").resolve(image.dockerfile()).toString(),
            "--tag",
            tag(image),
            repository.resolve("base").toString()));
    return command;
  }

  public static List<String> save(Image image, Path archive) {
    return List.of("docker", "save", "--output", archive.toString(), tag(image));
  }

  /** The scanner is a linux amd64 binary: it runs in the amd64 base image, whatever the host is. */
  public static List<String> scan(Path repository, Path archive) {
    return List.of(
        "docker",
        "run",
        "--rm",
        "--platform",
        "linux/amd64",
        "--entrypoint",
        "sh",
        "--volume",
        repository.resolve("orchestrator-backend/scripts") + ":/scripts:ro",
        "--volume",
        archive + ":/image.tar:ro",
        tag(byName("base")),
        "/scripts/vulnerability-scan.sh",
        "scan",
        "image",
        "--archive",
        "/image.tar");
  }

  private static Image byName(String name) {
    return ALL.stream().filter(image -> image.name().equals(name)).findFirst().orElseThrow();
  }
}
