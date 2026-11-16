package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImageJvmOptionsTest {

  private static final Path DOCKERFILE = Path.of("../Dockerfile");
  private static final Path ENTRYPOINT = Path.of("../entrypoint.sh");

  @Test
  void theImageSizesTheHeapForPodSharedWithSessionsAndDumpsToTheVolume() {
    String dockerfile = SourceRoots.read(DOCKERFILE);

    assertThat(dockerfile)
        .contains(
            "ENV JAGT_JAVA_OPTIONS=\"-XX:MaxRAMPercentage=25 -XX:+UseSerialGC",
            "-XX:+ExitOnOutOfMemoryError",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=/data/dumps");
  }

  @Test
  void onlyJagtsOwnJvmReadsTheOptionsNotEveryProgramSessionStarts() {
    String dockerfile = SourceRoots.read(DOCKERFILE);

    assertThat(SourceRoots.read(ENTRYPOINT)).contains("java $JAGT_JAVA_OPTIONS -jar /app/jagt.jar");
    assertThat(dockerfile).doesNotContain("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS");
  }

  @Test
  void theImageRunsAsFixedUserAndGroupThePodSpecNames() {
    String dockerfile = SourceRoots.read(DOCKERFILE);

    assertThat(dockerfile)
        .contains(
            "groupadd --gid 10001 jagt", "useradd --create-home --uid 10001 --gid 10001 jagt");
  }
}
