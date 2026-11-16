package dev.jagt.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class MergeMarkersTest {

  @Test
  void noTrackedTextFileKeepsAnUnresolvedMergeMarker() throws Exception {
    Process listing =
        new ProcessBuilder("git", "-C", "..", "ls-files", "-z").redirectErrorStream(true).start();
    String tracked = new String(listing.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(listing.waitFor()).describedAs("git ls-files").isZero();
    Pattern marker = Pattern.compile("(?m)^(<{7} .*|={7}|>{7} .*)\\r?$");

    List<String> marked =
        Arrays.stream(tracked.split("\0"))
            .map(name -> Path.of("..").resolve(name))
            .filter(Files::isRegularFile)
            .filter(file -> {
              try {
                byte[] bytes = Files.readAllBytes(file);
                return !new String(bytes, StandardCharsets.ISO_8859_1).contains("\0")
                    && marker.matcher(new String(bytes, StandardCharsets.UTF_8)).find();
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
            })
            .map(Path::toString)
            .sorted()
            .toList();

    assertThat(marked).isEmpty();
  }
}
