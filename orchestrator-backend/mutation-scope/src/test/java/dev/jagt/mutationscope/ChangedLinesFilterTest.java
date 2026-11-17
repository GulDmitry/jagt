package dev.jagt.mutationscope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.pitest.classinfo.ClassName;
import org.pitest.mutationtest.build.InterceptorType;
import org.pitest.mutationtest.engine.Location;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.MutationIdentifier;

class ChangedLinesFilterTest {

  private final ChangedLinesFilter filter =
      new ChangedLinesFilter(new ChangedLines(Map.of("dev/jagt/Foo.java", Set.of(12, 13))));

  @ParameterizedTest
  @CsvSource({
    "dev.jagt.Foo, Foo.java, 12, true",
    "dev.jagt.Foo$Inner, Foo.java, 13, true",
    "dev.jagt.Foo, Foo.java, 14, false",
    "dev.jagt.Bar, Bar.java, 12, false",
    "other.Foo, Foo.java, 12, false"
  })
  void keepsMutantOnlyOnChangedLineOfChangedFile(
      String type, String source, int line, boolean kept) {
    MutationDetails mutant =
        new MutationDetails(
            new MutationIdentifier(
                Location.location(ClassName.fromString(type), "run", "()V"), 0, "MUTATOR"),
            source,
            "description",
            line,
            0);

    var result = filter.intercept(List.of(mutant), mock(Mutater.class));

    assertThat(result.contains(mutant)).isEqualTo(kept);
  }

  @Test
  void isFilterInterceptor() {
    assertThat(filter.type()).isEqualTo(InterceptorType.FILTER);
  }
}
