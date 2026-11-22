package fitness.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GlobTest {

  @Test
  void aCommaOutsideBracesIsALetterNotAnAlternation() {
    Glob glob = Glob.of(Path.of("."), "x,y.md");

    assertThat(glob.matches("x,y.md")).isTrue();
    assertThat(glob.matches("x")).isFalse();
    assertThat(glob.matches("y.md")).isFalse();
  }

  @Test
  void aCommaInsideBracesAlternates() {
    Glob glob = Glob.of(Path.of("."), "{a,b}.md");

    assertThat(glob.matches("a.md")).isTrue();
    assertThat(glob.matches("b.md")).isTrue();
    assertThat(glob.matches("a,b.md")).isFalse();
  }

  @Test
  void aListSplitsOnlyAtCommasOutsideBraces() {
    assertThat(Glob.split("*.{a,b},c.md")).containsExactly("*.{a,b}", "c.md");
  }
}
