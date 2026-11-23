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
            "ENV JAGT_JAVA_OPTIONS=\"-Xmx512m -XX:+UseSerialGC",
            "-XX:+ExitOnOutOfMemoryError",
            "-XX:+HeapDumpOnOutOfMemoryError",
            "-XX:HeapDumpPath=/data/dumps");
  }

  @Test
  void onlyJagtsOwnJvmReadsTheOptionsNotEveryProgramSessionStarts() {
    String dockerfile = SourceRoots.read(DOCKERFILE);

    assertThat(SourceRoots.read(ENTRYPOINT)).contains("$JAGT_JAVA_OPTIONS -jar /app/jagt.jar");
    assertThat(dockerfile).doesNotContain("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS");
  }

  @Test
  void theImageRunsAsAnyUserInGroupZeroWithGroupWritableDirectories() {
    String dockerfile = SourceRoots.read(DOCKERFILE);

    assertThat(dockerfile)
        .contains(
            "USER 10001:0",
            "chgrp -R 0 /app /data && chmod -R g=u /app /data",
            "ENV HOME=/data/home");
  }
}
